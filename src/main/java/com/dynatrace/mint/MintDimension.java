package com.dynatrace.mint;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class MintDimension {
	String dimensionKey;
	String dimensionValue;

	public MintDimension(String dimensionKey, String dimensionValue) {
		this.dimensionKey = dimensionKey;
		this.dimensionValue = dimensionValue;
	}

	public String getDimensionKey() {
		return dimensionKey;
	}

	public String getDimensionValue() {
		return dimensionValue;
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this)
				.append("dimensionKey", dimensionKey)
				.append("dimensionValue", dimensionValue)
				.toString();
	}
}
