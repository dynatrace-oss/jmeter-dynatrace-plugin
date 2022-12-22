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

import java.util.ArrayList;
import java.util.List;

public class MintMetricsLine {
	String metricKey;
	List<MintDimension> dimensions = new ArrayList<MintDimension>();
	List<MintGauge> gauges = new ArrayList<MintGauge>();

	public MintMetricsLine(String metricKey) {
		this.metricKey = metricKey;
	}

    public MintMetricsLine(String metricKey, String displayName, String unit, String description) {
        this(metricKey);
        addDimension(new MintDimension("dt.meta.unit", unit));
        addDimension(new MintDimension("dt.meta.description", description));
        addDimension(new MintDimension("dt.meta.displayname", displayName));
    }

    public void addDimension(MintDimension dimension) {
		dimensions.add(dimension);
	}

	public void addGauge(MintGauge gauge) {
		gauges.add(gauge);
	}

	public String printMessage(boolean metadata) {
		StringBuilder dimensionString = new StringBuilder();
		StringBuilder gaugeString = new StringBuilder();

		for (MintDimension d : dimensions) {
			dimensionString.append(d.dimensionKey);
			dimensionString.append("=");
            dimensionString.append(addQuotes(d.dimensionValue, metadata));
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
            if (metadata) {
                return "#" + metricKey + " gauge " + dimensionString;
            } else {
                return metricKey + "," + dimensionString + " " + "gauge," + gaugeString + " " + timestampMillis;
            }
        } else {
            return metricKey + " " + "gauge," + gaugeString + " " + timestampMillis;
        }
	}

    private String addQuotes(String string, boolean addQuotes) {
        return addQuotes ? ("\"" + string + "\"") : string;
    }

    @Override
	public String toString() {
		return printMessage(false);
	}
}
