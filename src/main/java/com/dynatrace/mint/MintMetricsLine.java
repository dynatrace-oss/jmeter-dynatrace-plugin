package com.dynatrace.mint;

import java.util.ArrayList;
import java.util.List;

public class MintMetricsLine {
	String metricKey;
	List<MintDimension> dimensions = new ArrayList<MintDimension>();
	List<MintGauge> gauges = new ArrayList<MintGauge>();

	public MintMetricsLine(String metricKey) {
		this.metricKey = metricKey;
	}

	public void addDimension(MintDimension dimension) {
		dimensions.add(dimension);
	}

	public void addGauge(MintGauge gauge) {
		gauges.add(gauge);
	}

	public String printMessage() {
		StringBuilder dimensionString = new StringBuilder();
		StringBuilder gaugeString = new StringBuilder();

		for (MintDimension d : dimensions) {
			dimensionString.append(d.dimensionKey);
			dimensionString.append("=");
			dimensionString.append(d.dimensionValue);
			dimensionString.append(",");
		}

		for (MintGauge g : gauges) {
			gaugeString.append(g);
			gaugeString.append(",");
		}

		if (dimensionString.length() > 0) {
			dimensionString.setLength(dimensionString.length() - 1);
		}
		if (gaugeString.length() > 0) {
			gaugeString.setLength(gaugeString.length() - 1);
		}

		long timestampMillis = System.currentTimeMillis();
		if (dimensionString.length() > 0) {
			return metricKey + "," + dimensionString + " " + "gauge," + gaugeString + " " + timestampMillis;
		} else {
			return metricKey + " " + "gauge," + gaugeString + " " + timestampMillis;
		}
	}

	@Override
	public String toString() {
		return printMessage();
	}
}
