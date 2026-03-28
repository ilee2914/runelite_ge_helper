package com.github.ilee2.gehelper;

import lombok.Data;

/**
 * Represents a single data point from the OSRS Wiki /timeseries endpoint.
 */
@Data
public class TimeseriesEntry
{
	private long timestamp;
	private Long avgHighPrice;
	private Long avgLowPrice;
	private Long highPriceVolume;
	private Long lowPriceVolume;
}
