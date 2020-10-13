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
