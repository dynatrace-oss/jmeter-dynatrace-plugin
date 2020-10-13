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
