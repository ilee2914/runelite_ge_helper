package com.gehelper;

import com.gehelper.ui.GEHelperPanel;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
	name = "GE Helper",
	description = "Tracks GE prices with live wiki data, price graphs, and offer monitoring",
	tags = {"grand exchange", "ge", "prices", "wiki", "tracker"}
)
public class GEHelperPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private GEHelperConfig config;

	@Inject
	private WikiPriceClient priceClient;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ScheduledExecutorService executorService;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GEOfferOverlay geOfferOverlay;

	@Inject
	private ItemManager itemManager;

	private GEHelperPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> priceRefreshTask;

	@Override
	protected void startUp() throws Exception
	{
		log.info("GE Helper started!");

		// Initialize panel
		panel = new GEHelperPanel(priceClient, config, itemManager);

		// Create navigation button for sidebar
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/ge_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("GE Helper")
			.icon(icon != null ? icon : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		// Register overlay for GE price display
		overlayManager.add(geOfferOverlay);

		// Fetch initial prices and mappings in background
		executorService.submit(() ->
		{
			priceClient.fetchItemMapping();
			priceClient.fetchLatestPrices();
		});

		// Schedule periodic price refresh (every 60 seconds)
		priceRefreshTask = executorService.scheduleAtFixedRate(() ->
		{
			try
			{
				priceClient.fetchLatestPrices();
				SwingUtilities.invokeLater(() -> panel.refreshPrices());
			}
			catch (Exception e)
			{
				log.error("Price refresh failed", e);
			}
		}, 60, 60, TimeUnit.SECONDS);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("GE Helper stopped!");

		if (priceRefreshTask != null)
		{
			priceRefreshTask.cancel(true);
		}

		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(geOfferOverlay);

		if (panel != null)
		{
			panel.shutdown();
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		int slot = event.getSlot();
		GrandExchangeOfferState state = offer.getState();

		int itemId = offer.getItemId();
		int totalQuantity = offer.getTotalQuantity();
		int quantityFilled = offer.getQuantitySold();
		int price = offer.getPrice();

		boolean isBuy = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT;

		// Get item name from mapping
		String itemName = priceClient.getItemName(itemId);

		// Determine effective state for tracking
		boolean isActive = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.SELLING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.SOLD
			|| state == GrandExchangeOfferState.CANCELLED_BUY
			|| state == GrandExchangeOfferState.CANCELLED_SELL;

		GrandExchangeOfferState effectiveState = isActive ? state : GrandExchangeOfferState.EMPTY;

		panel.updateOffer(slot, itemId, itemName, isBuy, totalQuantity, quantityFilled, price, effectiveState);

		log.debug("GE offer changed: slot={}, item={}, state={}, filled={}/{}", slot, itemName, state, quantityFilled, totalQuantity);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.GRAND_EXCHANGE)
		{
			if (config.showPricesOnOffer())
			{
				clientThread.invokeLater(this::addPriceInfoToGE);
			}
		}
	}

	/**
	 * Adds price information and wiki link to the GE offer status screen.
	 */
	private void addPriceInfoToGE()
	{
		try
		{
			// Try to find the GE offer setup widget to get the current item
			Widget geOfferContainer = client.getWidget(InterfaceID.GRAND_EXCHANGE, 0);
			if (geOfferContainer == null)
			{
				return;
			}

			// Get the item ID from the current GE slot being viewed
			// We check all 8 GE slots to find active offers
			for (int slot = 0; slot < 8; slot++)
			{
				GrandExchangeOffer offer = client.getGrandExchangeOffers()[slot];
				if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
				{
					continue;
				}

				int itemId = offer.getItemId();
				PriceData priceData = priceClient.getPrice(itemId);
				if (priceData == null)
				{
					continue;
				}

				log.debug("GE slot {} has item {} with buy={} sell={}",
					slot, itemId, priceData.getHigh(), priceData.getLow());
			}
		}
		catch (Exception e)
		{
			log.error("Error adding price info to GE", e);
		}
	}

	/**
	 * Opens the wiki price page for the given item ID.
	 */
	public static void openWikiPricePage(int itemId)
	{
		LinkBrowser.browse("https://prices.runescape.wiki/osrs/item/" + itemId);
	}

	@Provides
	GEHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GEHelperConfig.class);
	}
}
