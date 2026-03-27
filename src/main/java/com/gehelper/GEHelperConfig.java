package com.gehelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("gehelper")
public interface GEHelperConfig extends Config
{
	@ConfigItem(
		keyName = "showPricesOnOffer",
		name = "Show Prices on GE",
		description = "Display current buy/sell prices on the Grand Exchange offer screen"
	)
	default boolean showPricesOnOffer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "graphTimestep",
		name = "Graph Interval",
		description = "Time interval for the price graph (5m, 1h, 6h, 24h)"
	)
	default String graphTimestep()
	{
		return "1h";
	}

	@ConfigItem(
		keyName = "showGraphLegend",
		name = "Show Graph Legend",
		description = "Show Buy/Sell legend on each price graph"
	)
	default boolean showGraphLegend()
	{
		return false;
	}
}
