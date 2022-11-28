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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.apache.jmeter.visualizers.backend.SamplerMetric;
import org.apache.jmeter.visualizers.backend.UserMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dynatrace.mint.MintDimension;
import com.dynatrace.mint.MintGauge;
import com.dynatrace.mint.MintMetricsLine;
import com.dynatrace.mint.SchemalessMetricSanitizer;

public class MintBackendListener extends AbstractBackendListenerClient implements Runnable {
	private static final Logger log = LoggerFactory.getLogger(MintBackendListener.class);
	private static final Map<String, String> DEFAULT_ARGS = new HashMap();
	private static final long SEND_INTERVAL = 60;
	private ScheduledExecutorService scheduler;
	private ScheduledFuture<?> timerHandle;
	private MintMetricSender mintMetricSender;
	private Map<String, String> testDimensions = new HashMap<>();
	private Map<String, String> transactionDimensions = new HashMap<>();
	private boolean enabled;
	private String listenerName;
	private String sendSamplersByRegex;
	private Pattern samplersToFilter;

	static {
		DEFAULT_ARGS.put("dynatraceMetricIngestUrl", "https://DT_SERVER/api/v2/metrics/ingest");
		DEFAULT_ARGS.put("dynatraceApiToken", "****");
		DEFAULT_ARGS.put("testDimensions", "testName=${__TestPlanName}");
		DEFAULT_ARGS.put("transactionDimensions", "dt.entity.service=SERVICE-XXXXXXXXXXXXX");
		DEFAULT_ARGS.put("enabled", "${__P(enabled, true)}");
		DEFAULT_ARGS.put("name", "DT MINT Backendlistener");
		DEFAULT_ARGS.put("samplersRegex", ".*");
	}

	@Override
	public void setupTest(BackendListenerContext context) throws Exception {
		super.setupTest(context);
		listenerName = context.getParameter("name");
		log.info("{}: Test started", listenerName);
		scheduler = Executors.newScheduledThreadPool(1);
		timerHandle = this.scheduler.scheduleAtFixedRate(this, 0L, SEND_INTERVAL, TimeUnit.SECONDS);
		mintMetricSender = new MintMetricSender();
		String dynatraceMetricIngestUrl = context.getParameter("dynatraceMetricIngestUrl");
		String dynatraceApiToken = context.getParameter("dynatraceApiToken");

		sendSamplersByRegex = context.getParameter("samplersRegex", "");
		samplersToFilter = Pattern.compile(sendSamplersByRegex);

		final String testDimensionString = context.getParameter("testDimensions", "");
		final String transactionDimensionString = context.getParameter("transactionDimensions", "");
		testDimensions.putAll(Arrays.stream(testDimensionString.split("[, ]"))
				.map(s -> s.split("[= ]"))
				.filter(strings -> strings.length == 2)
				.collect(Collectors.toMap(
						a -> a[0],  //key
						a -> a[1]   //value
				)));
		transactionDimensions.putAll(Arrays.stream(transactionDimensionString.split("[, ]"))
				// filter default dimension SERVICE-XXXXXXXXXXXXX
				.filter(strings -> !strings.equals(DEFAULT_ARGS.get("transactionDimensions")))
				.map(s -> s.split("[= ]"))
				.filter(strings -> strings.length == 2)
				.collect(Collectors.toMap(
						a -> a[0],  //key
						a -> a[1]   //value
				)));

		String enableParam = context.getParameter("enabled", "true");
		enabled = Boolean.parseBoolean(enableParam);
		log.info("{}: Configured enabled state {}", listenerName, enabled);
		log.info("{}: Configured test dimensions {}", listenerName, testDimensions);
		log.info("{}: Configured transaction dimensions {}", listenerName, transactionDimensions);

		if (enabled) {
			// only check the connection if the plugin was enabled
			try {
				mintMetricSender.setup(listenerName, dynatraceMetricIngestUrl, dynatraceApiToken);
				mintMetricSender.checkConnection();
				log.info("{}: Start MINT metric sender for url {}", listenerName, dynatraceMetricIngestUrl);
			} catch (Exception ex) {
				log.info("{}: Start MINT metric sender for url {} failed with {}, setting enabled state to false",
						listenerName, dynatraceMetricIngestUrl, ex.getMessage());
				// disable the plugin when the connection check fails
				enabled = false;
			}
		}
		log.info("{}: Enabled state {}", listenerName, enabled);
	}

	@Override
	public void teardownTest(BackendListenerContext context) throws Exception {
		log.info("{}: Test finished", listenerName);
		boolean cancelState = this.timerHandle.cancel(false);
		log.debug("{}: Canceled state: {}", listenerName, cancelState);
		scheduler.shutdown();

		try {
			scheduler.awaitTermination(30L, TimeUnit.SECONDS);
		} catch (InterruptedException var4) {
			log.error("{}: Error waiting for end of scheduler", listenerName);
			Thread.currentThread().interrupt();
		}

		if (enabled) {
			log.info("{}: Sending last metrics", listenerName);
			this.sendMetrics();
		}

		mintMetricSender.destroy();
		super.teardownTest(context);
	}

	@Override
	public Arguments getDefaultParameters() {
		Arguments arguments = new Arguments();
		DEFAULT_ARGS.forEach(arguments::addArgument);
		return arguments;

	}

	@Override
	public void handleSampleResults(List<SampleResult> sampleResults,
			BackendListenerContext backendListenerContext) {
		log.debug("{}: handleSampleResults for {} samples", listenerName, sampleResults.size());

		UserMetric userMetrics = getUserMetrics();

		for (SampleResult sampleResult : sampleResults) {
			userMetrics.add(sampleResult);

			SamplerMetric samplerMetric = this.getSamplerMetric(sampleResult.getSampleLabel());
			samplerMetric.add(sampleResult);

			final SamplerMetric cumulatedMetrics = this.getSamplerMetric(sampleResult.getSampleLabel());
			cumulatedMetrics.add(sampleResult);
		}

		log.debug("{}: handleSampleResults: UserMetrics(startedThreads={}, finishedThreads={})",
				listenerName,
				getUserMetrics().getStartedThreads(),
				getUserMetrics().getFinishedThreads());
		final SamplerMetric allCumulatedMetrics = this.getSamplerMetric("all");
		log.debug("{}: handleSampleResults: cumulatedMetrics(hits={}, errors={}, success={}, total={})",
				listenerName,
				allCumulatedMetrics.getHits(), allCumulatedMetrics.getErrors(), allCumulatedMetrics.getSuccesses(),
				allCumulatedMetrics.getTotal());
	}

	@Override
	public void run() {
		log.debug("{}: run started", listenerName);
		if (enabled) {
			try {
				this.sendMetrics();
			} catch (Exception ex) {
				log.error("{}: Failed to send metrics: {}", listenerName, ex.getMessage());
			}
		} else {
			log.debug("{}: skip sending metrics because the plugin has been disabled", listenerName);
		}
		log.debug("{}: run finished", listenerName);
	}

	private void sendMetrics() {
		final Iterator<Entry<String, SamplerMetric>> iterator = getMetricsPerSampler().entrySet().iterator();

		while (true) {
			if (!iterator.hasNext()) {
				break;
			}

			Entry<String, SamplerMetric> entry = iterator.next();
			SamplerMetric metric = entry.getValue();
			if ((entry.getKey()).equals("all")) {
				// addCumulatedMetrics(metric);
			} else {
				String transaction = entry.getKey();
				log.debug("Checking if SampleLabel '{}' matches Regex '{}'", transaction, sendSamplersByRegex);
				Matcher matcher = samplersToFilter.matcher(transaction);
				if (matcher.find()) {
					log.debug("Adding SampleLabel '{}' to samplerMetric-List", transaction);
					addMetricsForTransaction(transaction, metric);
				} else {
					log.debug("SampleLabel '{}' does not match Regex '{}'", transaction, sendSamplersByRegex);
				}
			}
			metric.resetForTimeInterval();
		}

		UserMetric userMetrics = this.getUserMetrics();
		addMetricLineForTest("jmeter.usermetrics.minactivethreads", userMetrics.getMinActiveThreads());
		addMetricLineForTest("jmeter.usermetrics.maxactivethreads", userMetrics.getMaxActiveThreads());
		addMetricLineForTest("jmeter.usermetrics.meanactivethreads", userMetrics.getMeanActiveThreads());
		addMetricLineForTest("jmeter.usermetrics.startedthreads", userMetrics.getStartedThreads());
		addMetricLineForTest("jmeter.usermetrics.finishedthreads", userMetrics.getFinishedThreads());

		mintMetricSender.writeAndSendMetrics();
	}

	private void addMetricLineForTest(String metricKey, int metricValue) {
		MintMetricsLine line = new MintMetricsLine(metricKey);
		addTestDimensions(line);
		line.addGauge(new MintGauge(metricValue));
		mintMetricSender.addMetric(line);
	}

	private void addMetricsForTransaction(String transaction, SamplerMetric metric) {
		addMetricLineForTransaction(transaction, "jmeter.usermetrics.transaction.count", metric.getTotal());
		addMetricLineForTransaction(transaction, "jmeter.usermetrics.transaction.success", metric.getSuccesses());
		addMetricLineForTransaction(transaction, "jmeter.usermetrics.transaction.error", metric.getFailures());
		addMetricLineForTransaction(transaction, "jmeter.usermetrics.transaction.hits", metric.getHits());
		addMetricLineForTransaction(transaction, "jmeter.usermetrics.transaction.mintime", metric.getAllMinTime());
		addMetricLineForTransaction(transaction, "jmeter.usermetrics.transaction.maxtime", metric.getAllMaxTime());
		addMetricLineForTransaction(transaction, "jmeter.usermetrics.transaction.meantime", metric.getAllMean());
		addMetricLineForTransaction(transaction, "jmeter.usermetrics.transaction.sentbytes", metric.getSentBytes());
		addMetricLineForTransaction(transaction, "jmeter.usermetrics.transaction.receivedbytes", metric.getReceivedBytes());
	}

	private void addMetricLineForTransaction(String transaction, String metricKey, double metricValue) {
		MintMetricsLine line = new MintMetricsLine(metricKey);
		addTransactionDimensions(transaction, line);
		line.addGauge(new MintGauge(metricValue));
		mintMetricSender.addMetric(line);
	}

	private void addTransactionDimensions(String transaction, MintMetricsLine metricsLine) {
		metricsLine.addDimension(new MintDimension("transaction", SchemalessMetricSanitizer.sanitizeDimensionValue(transaction)));
		transactionDimensions.forEach((key, value) -> {
			if (!key.trim().isEmpty() && !value.trim().isEmpty())
				metricsLine.addDimension(
						new MintDimension(SchemalessMetricSanitizer.sanitizeDimensionIdentifier(key),
								SchemalessMetricSanitizer.sanitizeDimensionValue(value)));
		});
	}

	private void addTestDimensions(MintMetricsLine metricsLine) {
		testDimensions.forEach((key, value) -> {
			if (!key.trim().isEmpty() && !value.trim().isEmpty())
				metricsLine.addDimension(
						new MintDimension(SchemalessMetricSanitizer.sanitizeDimensionIdentifier(key),
								SchemalessMetricSanitizer.sanitizeDimensionValue(value)));
		});
	}
}
