package com.gehelper;

import lombok.Data;

/**
 * Represents the latest price data for an item from the OSRS Wiki real-time prices API.
 * "high" = instant-buy price (someone buying at this price)
 * "low" = instant-sell price (someone selling at this price)
 */
@Data
public class PriceData
{
	private Long high;
	private Long highTime;
	private Long low;
	private Long lowTime;
}
