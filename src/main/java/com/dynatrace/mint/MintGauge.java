package com.dynatrace.mint;

public class MintGauge {
	String field;
	double number;

	public MintGauge(double number) {
		this.number = number;
	}

	public MintGauge(String field, double number) {
		this.field = field;
		this.number = number;
	}

	public String getField() {
		return field;
	}

	public double getNumber() {
		return number;
	}

	@Override
	public String toString() {
		String result;
		if (field == null) {
			result = String.valueOf(this.number);
		} else {
			result = field + "=" + String.valueOf(this.number);
		}
		return result;
	}
}
