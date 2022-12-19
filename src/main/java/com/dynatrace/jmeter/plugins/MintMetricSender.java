/**
 * Copyright 2018-2020 Dynatrace LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dynatrace.jmeter.plugins;

import com.dynatrace.mint.MintMetricsLine;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.util.EntityUtils;
import org.apache.jmeter.report.utils.MetricUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class MintMetricSender {
	private static final Logger log = LoggerFactory.getLogger(MintMetricSender.class);
	private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
	private static final String AUTHORIZATION_HEADER_VALUE = "Api-token ";
	private static final int CONNECT_TIMEOUT = 10_000;
	private static final int SOCKET_TIMEOUT = 30_000;
	private static final int MAX_CONNECTIONS = 10;
	private static final int MAX_THREADS = 5;
	// limits for sending a single message
	static final int MAX_LINES_PER_MESSAGE = 1000;
	static final int MAX_MESSAGE_SIZE_BYTES = 1048576;
	private CloseableHttpAsyncClient httpClient;
	private HttpPost httpRequest;
	private URL url;
	private String token;
	private String name;
	private Future<HttpResponse> lastRequest;
	private List<MintMetricsLine> metrics = new CopyOnWriteArrayList<>();

	public MintMetricSender() {
	}

	public synchronized void setup(String name, String mintIngestUrl, String mintIngestToken) throws Exception {
		this.url = new URL(mintIngestUrl);
		this.token = mintIngestToken;
		this.name = name;

		IOReactorConfig ioReactorConfig = IOReactorConfig.custom().setIoThreadCount(MAX_THREADS).setConnectTimeout(CONNECT_TIMEOUT)
				.setSoTimeout(SOCKET_TIMEOUT)
				.build();
		ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);
		PoolingNHttpClientConnectionManager connManager = new PoolingNHttpClientConnectionManager(ioReactor);
		httpClient = HttpAsyncClientBuilder.create().setConnectionManager(connManager).setMaxConnPerRoute(MAX_CONNECTIONS)
				.setMaxConnTotal(MAX_CONNECTIONS)
				.setUserAgent("ApacheJMeter 5").disableCookieManagement().disableConnectionState().build();
		httpRequest = createRequest(this.url, this.token);
		httpClient.start();
	}

	private HttpPost createRequest(URL url, String token) throws URISyntaxException {
		RequestConfig defaultRequestConfig = RequestConfig.custom().setConnectTimeout(CONNECT_TIMEOUT)
				.setSocketTimeout(SOCKET_TIMEOUT)
				.setConnectionRequestTimeout(CONNECT_TIMEOUT).build();
		HttpPost currentHttpRequest = new HttpPost(url.toURI());
		currentHttpRequest.setConfig(defaultRequestConfig);
		if (StringUtils.isNotBlank(token)) {
			currentHttpRequest.setHeader(AUTHORIZATION_HEADER_NAME, AUTHORIZATION_HEADER_VALUE + token);
		}

		log.debug("{}: Created MintMetricSender with url: {}", name, url);
		return currentHttpRequest;
	}

	public synchronized void addMetric(MintMetricsLine line) {
		log.debug("{}: addMetric({})", name, line);
		metrics.add(line);
	}

	public synchronized void writeAndSendMetrics() {
		if (metrics.isEmpty()) {
			return;
		}

		final List<MintMetricsLine> copyMetrics = new ArrayList<>(metrics);
		metrics = new CopyOnWriteArrayList<>();

		final List<String> splitMessages = splitMessages(copyMetrics);
		if (splitMessages.size() > 1) {
			log.info("{}: Splitted the message into {} requests", name, splitMessages.size());
		}
		for (String splitMessage : splitMessages) {
			writeAndSendMetrics(splitMessage);
		}
	}

	public synchronized void checkConnection() throws MintConnectionException {
		try {
			if (httpRequest == null) {
				httpRequest = this.createRequest(this.url, this.token);
			}
			log.debug("{}: Sending empty metrics", name);
			httpRequest.setEntity(new StringEntity("", StandardCharsets.UTF_8));
			lastRequest = httpClient.execute(httpRequest, new FutureCallback<HttpResponse>() {
				public void completed(HttpResponse response) {
				}

				public void failed(Exception ex) {
				}

				public void cancelled() {
				}
			});

		} catch (URISyntaxException ex) {
			//
		}
		try {
			final HttpResponse lastResponse = lastRequest.get(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
			int code = lastResponse.getStatusLine().getStatusCode();
			if (MetricUtils.isSuccessCode(code)) {
				log.debug("{}: Successfully checked connection", name);
			} else {
				log.warn("{}: Error writing metrics to MINT Url: {}, responseCode: {}, responseBody: {}",
						name, new Object[] { url, code, getBody(lastResponse) });

				switch (code) {
				case 400:
					// ok, message because of empty request: responseCode: 400, responseBody: {"linesOk":0,"linesInvalid":0,"error":{"code":400,"message":"empty request","invalidLines":[]}}
					break;
				case 401:
					throw new MintConnectionException("Error executing connection check for MINT server: Invalid token");
				case 404:
				case 405:
					throw new MintConnectionException("Error executing connection check for MINT server: Invalid url");
				default:
					throw new MintConnectionException("Error executing connection check for MINT server: Other error");
				}
			}

		} catch (ExecutionException | TimeoutException | InterruptedException ex) {
			log.warn("{}: Error executing connection check for MINT server: {}", name, ex.getMessage());
			throw new MintConnectionException("General Error executing connection check for MINT server");
		}
	}

	private void writeAndSendMetrics(final String message) {
		try {
			if (httpRequest == null) {
				httpRequest = this.createRequest(url, token);
			}

			log.debug("{}: Sending metrics: {}", name, message);
			final int nrLines = getLineCount(message);

			httpRequest.setEntity(new StringEntity(message, StandardCharsets.UTF_8));
			lastRequest = httpClient.execute(httpRequest, new FutureCallback<HttpResponse>() {
				public void completed(HttpResponse response) {
					int code = response.getStatusLine().getStatusCode();
					if (MetricUtils.isSuccessCode(code)) {
						log.info("{}: Success, number of metrics written: {}", name, nrLines);
						log.debug("{}: Last message: {}", name, message);
					} else {
						log.error("{}: Error writing metrics to MINT Url: {}, responseCode: {}, responseBody: {}",
								name, new Object[] { url, code, getBody(response) });
						log.info("{}: Last message: {}", name, message);
					}

				}

				public void failed(Exception ex) {
					log.error("{}: failed to send data to MINT server: {}", name, ex.getMessage());
				}

				public void cancelled() {
					log.warn("{}: Request to MINT server was cancelled", nrLines);
				}
			});
		} catch (URISyntaxException ex) {
			log.error(ex.getMessage(), ex);
		}

	}

	List<String> splitMessages(final List<MintMetricsLine> copyMetrics) {
		final List<String> splitMessages = new ArrayList<>();
		Iterator<MintMetricsLine> metricsIterator = copyMetrics.iterator();
		int metricLines = 0;
		int metricSize = 0;
		StringBuilder metricMessage = new StringBuilder();
		while (metricsIterator.hasNext()) {
			final MintMetricsLine metricsLine = metricsIterator.next();
			String message = metricsLine.printMessage(false) + System.getProperty("line.separator");
			if (metricLines + 1 < MAX_LINES_PER_MESSAGE && metricSize + message.length() < MAX_MESSAGE_SIZE_BYTES) {
				metricMessage.append(message);
				metricLines++;
				metricSize = metricMessage.length();
			} else {
				// add the previous message content to the list
				splitMessages.add(metricMessage.toString());
				// create e new buffer and add the first message to the buffer
				metricMessage = new StringBuilder(message);
				metricLines = 1;
				metricSize = metricMessage.length();
			}
			metricsIterator.remove();
		}
		// add the last metric message if there is one
		if (metricMessage.length() > 0) {
			splitMessages.add(metricMessage.toString());
		}
		return splitMessages;
	}

	int getLineCount(final String message) {
		int lines = 0;
		if (message != null) {
			final Scanner scanner = new Scanner(message);
			while (scanner.hasNextLine()) {
				scanner.nextLine();
				lines++;
			}
		}
		return lines;
	}

	private static String getBody(HttpResponse response) {
		String body = "";

		try {
			if (response != null && response.getEntity() != null) {
				body = EntityUtils.toString(response.getEntity());
			}
		} catch (Exception ex) {
		}

		return body;
	}

	public void destroy() {
		log.info("{}: Destroying", name);

		if (lastRequest != null) {
			try {
				lastRequest.get(5L, TimeUnit.SECONDS);
			} catch (ExecutionException | TimeoutException | InterruptedException ex) {
				log.error("{}: Error waiting for last request to be send to MINT server: {}", name, ex.getMessage());
			}
		}

		if (httpRequest != null) {
			httpRequest.abort();
		}

		IOUtils.closeQuietly(httpClient);
	}

    public void setupMetrics() {
        List<MintMetricsLine> metrics = new ArrayList<>(Arrays.asList(
                new MintMetricsLine("jmeter.usermetrics.minactivethreads", "JMeter - min active threads", "count", "the minimum number of active threads"),
                new MintMetricsLine("jmeter.usermetrics.maxactivethreads", "JMeter - max active threads", "count", "the maximum number of active threads"),
                new MintMetricsLine("jmeter.usermetrics.meanactivethreads", "JMeter - mean active threads", "count", "the arithmetic mean of active threads"),
                new MintMetricsLine("jmeter.usermetrics.startedthreads", "JMeter - started threads", "count", "the number of started threads"),
                new MintMetricsLine("jmeter.usermetrics.finishedthreads", "JMeter - finished threads", "count", "the number of finished threads"),
                new MintMetricsLine("jmeter.usermetrics.transaction.count", "JMeter - number of requests", "count", "the total number of requests"),
                new MintMetricsLine("jmeter.usermetrics.transaction.success", "JMeter - successful requests", "count", "the number of successful requests"),
                new MintMetricsLine("jmeter.usermetrics.transaction.error", "JMeter - failed requests", "count", "the number of failed requests"),
                new MintMetricsLine("jmeter.usermetrics.transaction.hits", "JMeter - number of hits", "count", "the number of hits to the server"),
                new MintMetricsLine("jmeter.usermetrics.transaction.mintime", "JMeter - min response time", "MilliSecond", "the minimal elapsed time for requests within sliding window"),
                new MintMetricsLine("jmeter.usermetrics.transaction.maxtime", "JMeter - max response time", "MilliSecond", "the maximal elapsed time for requests within sliding window"),
                new MintMetricsLine("jmeter.usermetrics.transaction.meantime", "JMeter - mean response time", "MilliSecond", "the arithmetic mean of the elapsed time"),
                new MintMetricsLine("jmeter.usermetrics.transaction.sentbytes", "JMeter - sent bytes", "Byte", "the number of sent bytes"),
                new MintMetricsLine("jmeter.usermetrics.transaction.receivedbytes", "JMeter - received bytes", "Byte", "the number of received bytes")
        ));
        String metricsString = metrics.stream()
                .map(line -> line.printMessage(true) + System.getProperty("line.separator"))
                .collect(Collectors.joining());
        writeAndSendMetrics(metricsString);
        log.info("{}: Successfully send metrics metadata", name);
    }
}
