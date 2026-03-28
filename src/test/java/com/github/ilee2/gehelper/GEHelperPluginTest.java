package com.github.ilee2.gehelper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GEHelperPluginTest
{
	public static void main(String[] args) throws Exception
	{
		// Add all your private plugin classes here to load them together
		ExternalPluginManager.loadBuiltin(GEHelperPlugin.class);
		RuneLite.main(args);
	}
}
