package com.dynatrace.jmeter.plugins;

import com.dynatrace.mint.MintDimension;
import com.dynatrace.mint.MintGauge;
import com.dynatrace.mint.MintMetricsLine;
import com.dynatrace.mint.SchemalessMetricSanitizer;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MintMetricSenderTest {
	private MintMetricSender mintMetricSender;

	@Before
	public void setup() {
		mintMetricSender = new MintMetricSender();
	}

	@Test
	public void testLineCount() {
		assertEquals(0, mintMetricSender.getLineCount(null));
		assertEquals(0, mintMetricSender.getLineCount(""));
		assertEquals(1, mintMetricSender.getLineCount("line1"));
		assertEquals(2, mintMetricSender.getLineCount("line1" + System.getProperty("line.separator") + "line2"));
		assertEquals(2, mintMetricSender.getLineCount("line1\nline2"));
		assertEquals(2, mintMetricSender.getLineCount("line1\rline2"));
		assertEquals(2, mintMetricSender.getLineCount("line1\r\nline2"));
	}

	@Test
	public void testSplitMessagesWithEmptyLines() {
		final List<MintMetricsLine> lines = new ArrayList<>();
		final List<String> splitMessages = mintMetricSender.splitMessages(lines);
		assertEquals(0, splitMessages.size());
	}

	@Test
	public void testSplitMessagesBelowMaxLinesAndSize() {
		final List<MintMetricsLine> lines = new ArrayList<>();
		MintMetricsLine line1 = createLine("metric-key1", 1, 1, "dimKey", "dimValue");
		MintMetricsLine line2 = createLine("metric-key2", 2, 1, "dimKey", "dimValue");
		lines.add(line1);
		lines.add(line2);
		final List<String> splitMessages = mintMetricSender.splitMessages(lines);
		assertEquals(1, splitMessages.size());
	}

	@Test
	public void testSplitMessagesAboveMaxLines() {
		final List<MintMetricsLine> lines = new ArrayList<>();
		for (int i = 0; i < MintMetricSender.MAX_LINES_PER_MESSAGE + 1; i++) {
			MintMetricsLine line = createLine("metric-key-" + i, 1, 1, "dimKey", "dimValue");
			lines.add(line);
		}
		final List<String> splitMessages = mintMetricSender.splitMessages(lines);
		assertEquals(2, splitMessages.size());
	}

	@Test
	public void testSplitMessagesAboveMaxSize() {
		final List<MintMetricsLine> lines = new ArrayList<>();
		int messageSize = 0;
		int idx = 0;

		while (messageSize < MintMetricSender.MAX_MESSAGE_SIZE_BYTES) {
			MintMetricsLine line = createLine("metric-key-" + idx, idx, 100, "dimKey", "dimValue");
			messageSize += line.printMessage(false).length();
			idx++;
			lines.add(line);
		}
		final List<String> splitMessages = mintMetricSender.splitMessages(lines);
		assertEquals(2, splitMessages.size());

	}

    @Test
    public void testCreateLineWithMetaData() {
        MintMetricsLine line = new MintMetricsLine("jmeter.usermetrics.minactivethreads",
                "JMeter - min active threads", "count", "the minimum number of active threads");
        String metadataString = line.printMessage(true);
        assertEquals("#jmeter.usermetrics.minactivethreads gauge dt.meta.unit=\"count\","
                + "dt.meta.description=\"the minimum number of active threads\",dt.meta.displayname=\"JMeter - min active threads\"", metadataString);
    }

	private MintMetricsLine createLine(String metricKey, int metricValue, int nrDimensions, String dimensionKeyPrefix,
			String dimensionValuePrefix) {
		final MintMetricsLine metricsLine = new MintMetricsLine(metricKey);
		for (int i = 0; i < nrDimensions; i++) {
			metricsLine.addDimension(
					new MintDimension(SchemalessMetricSanitizer.sanitizeDimensionIdentifier(dimensionKeyPrefix + "_" + i),
							SchemalessMetricSanitizer.sanitizeDimensionValue(dimensionValuePrefix + "_" + i)));
		}
		metricsLine.addGauge(new MintGauge(metricValue));
		return metricsLine;
	}
}
