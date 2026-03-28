package com.github.ilee2.gehelper;

import com.github.ilee2.gehelper.ui.GEHelperPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Handles enhancements to the GE Offer Status screen:
 * - Adds a "Wiki Prices" button to open the wiki price page
 * - Shows current buy/sell prices from the wiki API overlay
 */
@Slf4j
@Singleton
public class GEOfferHelper
{
	@Inject
	private Client client;

	@Inject
	private WikiPriceClient priceClient;

	@Inject
	private ClientThread clientThread;

	/**
	 * Called when the GE interface is loaded. Checks offers and logs price info.
	 * The actual price display is handled via overlay or widget manipulation.
	 */
	public void onGEWidgetLoaded()
	{
		clientThread.invokeLater(() ->
		{
			for (int slot = 0; slot < 8; slot++)
			{
				GrandExchangeOffer offer = client.getGrandExchangeOffers()[slot];
				if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
				{
					continue;
				}

				int itemId = offer.getItemId();
				String itemName = priceClient.getItemName(itemId);
				PriceData priceData = priceClient.getPrice(itemId);

				if (priceData != null)
				{
					String buyPrice = priceData.getHigh() != null
						? QuantityFormatter.formatNumber(priceData.getHigh()) + " gp"
						: "N/A";
					String sellPrice = priceData.getLow() != null
						? QuantityFormatter.formatNumber(priceData.getLow()) + " gp"
						: "N/A";

					log.debug("GE Slot {}: {} — Buy: {}, Sell: {}", slot, itemName, buyPrice, sellPrice);
				}
			}
		});
	}

	/**
	 * Builds the wiki URL for a given item ID.
	 */
	public static String getWikiUrl(int itemId)
	{
		return "https://prices.runescape.wiki/osrs/item/" + itemId;
	}

	/**
	 * Opens the wiki price page for the given item.
	 */
	public static void openWikiPage(int itemId)
	{
		LinkBrowser.browse(getWikiUrl(itemId));
	}
}
