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

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

import com.dynatrace.mint.MintMetricsLine;

public class MintMetricSender {
	private static final Logger log = LoggerFactory.getLogger(MintMetricSender.class);
	private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
	private static final String AUTHORIZATION_HEADER_VALUE = "Api-token ";
	private CloseableHttpAsyncClient httpClient;
	private HttpPost httpRequest;
	private URL url;
	private String token;
	private Future<HttpResponse> lastRequest;
	private List<MintMetricsLine> metrics = new CopyOnWriteArrayList<>();

	public MintMetricSender() {
	}

	public synchronized void setup(String mintIngestUrl, String mintIngestToken) throws Exception {
		this.url = new URL(mintIngestUrl);
		this.token = mintIngestToken;

		IOReactorConfig ioReactorConfig = IOReactorConfig.custom().setIoThreadCount(1).setConnectTimeout(1000).setSoTimeout(3000)
				.build();
		ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);
		PoolingNHttpClientConnectionManager connManager = new PoolingNHttpClientConnectionManager(ioReactor);
		httpClient = HttpAsyncClientBuilder.create().setConnectionManager(connManager).setMaxConnPerRoute(2).setMaxConnTotal(2)
				.setUserAgent("ApacheJMeter 5").disableCookieManagement().disableConnectionState().build();
		httpRequest = createRequest(this.url, this.token);
		httpClient.start();
	}

	private HttpPost createRequest(URL url, String token) throws URISyntaxException {
		RequestConfig defaultRequestConfig = RequestConfig.custom().setConnectTimeout(1000).setSocketTimeout(3000)
				.setConnectionRequestTimeout(100).build();
		HttpPost currentHttpRequest = new HttpPost(url.toURI());
		currentHttpRequest.setConfig(defaultRequestConfig);
		if (StringUtils.isNotBlank(token)) {
			currentHttpRequest.setHeader(AUTHORIZATION_HEADER_NAME, AUTHORIZATION_HEADER_VALUE + token);
		}

		log.debug("Created MintMetricSender with url: {}", url);
		return currentHttpRequest;
	}

	public synchronized void addMetric(MintMetricsLine line) {
		log.debug("addMetric({})", line);
		metrics.add(line);
	}

	public synchronized void writeAndSendMetrics() {
		List<MintMetricsLine> copyMetrics;
		if (metrics.isEmpty()) {
			return;
		}

		copyMetrics = metrics;
		metrics = new CopyOnWriteArrayList<>();

		this.writeAndSendMetrics(copyMetrics);
	}

	public synchronized void checkConnection() throws MintConnectionException {
		try {
			if (httpRequest == null) {
				httpRequest = this.createRequest(this.url, this.token);
			}
			log.debug("Sending empty metrics: {}");
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
			final HttpResponse lastResponse = lastRequest.get(1L, TimeUnit.SECONDS);
			int code = lastResponse.getStatusLine().getStatusCode();
			if (MetricUtils.isSuccessCode(code)) {
				log.debug("Successfully checked connection");
			} else {
				log.debug("Error writing metrics to MINT Url: {}, responseCode: {}, responseBody: {}",
						new Object[] { url, code, getBody(lastResponse) });

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
			log.debug("Error executing connection check for MINT server", ex);
			throw new MintConnectionException("General Error executing connection check for MINT server");
		}
	}

	private void writeAndSendMetrics(final List<MintMetricsLine> copyMetrics) {
		try {
			if (httpRequest == null) {
				httpRequest = this.createRequest(url, token);
			}

			StringBuilder message = new StringBuilder();

			for (MintMetricsLine l : copyMetrics) {
				message.append(l.printMessage());
				message.append(System.getProperty("line.separator"));
			}

			log.debug("Sending metrics: {}", message.toString());
			httpRequest.setEntity(new StringEntity(message.toString(), StandardCharsets.UTF_8));
			lastRequest = httpClient.execute(httpRequest, new FutureCallback<HttpResponse>() {
				public void completed(HttpResponse response) {
					int code = response.getStatusLine().getStatusCode();
					if (MetricUtils.isSuccessCode(code)) {
						log.info("Success, number of metrics written: {}", copyMetrics.size());
						log.debug("Last message: {}", message.toString());
					} else {
						log.error("Error writing metrics to MINT Url: {}, responseCode: {}, responseBody: {}",
								new Object[] { url, code, getBody(response) });
						log.info("Last message: {}", message.toString());
					}

				}

				public void failed(Exception ex) {
					log.error("failed to send data to MINT server.", ex);
				}

				public void cancelled() {
					log.warn("Request to MINT server was cancelled");
				}
			});
		} catch (URISyntaxException var5) {
			log.error(var5.getMessage(), var5);
		}

	}

	private static String getBody(HttpResponse response) {
		String body = "";

		try {
			if (response != null && response.getEntity() != null) {
				body = EntityUtils.toString(response.getEntity());
			}
		} catch (Exception var3) {
		}

		return body;
	}

	public void destroy() {
		log.info("Destroying ");

		if (lastRequest != null) {
			try {
				lastRequest.get(5L, TimeUnit.SECONDS);
			} catch (ExecutionException | TimeoutException | InterruptedException var2) {
				log.error("Error waiting for last request to be send to MINT server", var2);
			}
		}

		if (httpRequest != null) {
			httpRequest.abort();
		}

		IOUtils.closeQuietly(httpClient);
	}
}
