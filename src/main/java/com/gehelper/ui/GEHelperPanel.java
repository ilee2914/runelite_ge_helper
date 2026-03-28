package com.gehelper.ui;

import com.gehelper.GEHelperConfig;
import com.gehelper.PriceData;
import com.gehelper.TimeseriesEntry;
import com.gehelper.WikiPriceClient;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.game.ItemManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main sidebar panel for GE Helper.
 * Shows active GE offers, each with an inline price graph.
 */
@Slf4j
public class GEHelperPanel extends PluginPanel
{
	private final WikiPriceClient priceClient;
	private final GEHelperConfig config;
	private final ItemManager itemManager;

	private final JPanel offersPanel;
	private final JLabel statusLabel;

	private final Map<Integer, OfferInfo> activeOffers = new LinkedHashMap<>();
	private final List<OfferPanel> offerPanels = new ArrayList<>();

	private final SearchPanel searchPanel;
	private final ConfigManager configManager;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public GEHelperPanel(WikiPriceClient priceClient, GEHelperConfig config, ItemManager itemManager, ConfigManager configManager)
	{
		super(false);
		this.priceClient = priceClient;
		this.config = config;
		this.itemManager = itemManager;
		this.configManager = configManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Header
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(8, 8, 5, 8));

		JLabel title = new JLabel("GE Helper");
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		header.add(title, BorderLayout.WEST);

		JLabel refreshBtn = new JLabel("⟳");
		refreshBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		refreshBtn.setFont(refreshBtn.getFont().deriveFont(18f));
		refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		refreshBtn.setToolTipText("Refresh prices");
		refreshBtn.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				refreshAll();
			}
		});
		header.add(refreshBtn, BorderLayout.EAST);

		// Top container holding header and tabs
		JPanel topContainer = new JPanel(new BorderLayout());
		topContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		topContainer.add(header, BorderLayout.NORTH);

		// Tab Group
		JPanel displayPanel = new JPanel(new BorderLayout());
		displayPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		MaterialTabGroup tabGroup = new MaterialTabGroup(displayPanel);

		// Offers view UI
		JPanel offersView = new JPanel(new BorderLayout());
		offersView.setBackground(ColorScheme.DARK_GRAY_COLOR);

		offersPanel = new JPanel();
		offersPanel.setLayout(new BoxLayout(offersPanel, BoxLayout.Y_AXIS));
		offersPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		statusLabel = new JLabel("No active offers");
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setFont(FontManager.getRunescapeFont());
		statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		statusLabel.setBorder(new EmptyBorder(20, 0, 20, 0));

		JPanel offersWrapper = new JPanel(new BorderLayout());
		offersWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		offersWrapper.add(offersPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(offersWrapper);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		offersView.add(scrollPane, BorderLayout.CENTER);

		MaterialTab offersTab = new MaterialTab("Offers", tabGroup, offersView);

		// Search view UI
		searchPanel = new SearchPanel(priceClient, config, itemManager, configManager);
		MaterialTab searchTab = new MaterialTab("Search", tabGroup, searchPanel);

		tabGroup.addTab(offersTab);
		tabGroup.addTab(searchTab);
		tabGroup.select(offersTab);

		topContainer.add(tabGroup, BorderLayout.SOUTH);

		add(topContainer, BorderLayout.NORTH);
		add(displayPanel, BorderLayout.CENTER);

		rebuildOffersList();
	}

	/**
	 * Update an offer slot with new data.
	 */
	public void updateOffer(int slot, int itemId, String itemName, boolean isBuy,
							int totalQuantity, int quantityFilled, int price,
							GrandExchangeOfferState state)
	{
		if (state == GrandExchangeOfferState.EMPTY)
		{
			activeOffers.remove(slot);
		}
		else
		{
			OfferInfo info = new OfferInfo();
			info.slot = slot;
			info.itemId = itemId;
			info.itemName = itemName;
			info.isBuy = isBuy;
			info.totalQuantity = totalQuantity;
			info.quantityFilled = quantityFilled;
			info.price = price;
			info.state = state;
			activeOffers.put(slot, info);
		}

		SwingUtilities.invokeLater(this::rebuildOffersList);
	}

	/**
	 * Refresh wiki prices and timeseries for all tracked offers.
	 */
	public void refreshAll()
	{
		refreshPrices();
		refreshAllTimeseries();
	}

	/**
	 * Refresh wiki prices for all tracked offers.
	 */
	public void refreshPrices()
	{
		if (searchPanel != null)
		{
			searchPanel.refreshPrices();
		}

		executor.submit(() ->
		{
			try
			{
				Map<Integer, PriceData> prices = priceClient.fetchLatestPrices();
				SwingUtilities.invokeLater(() ->
				{
					for (OfferPanel panel : offerPanels)
					{
						PriceData pd = prices.get(panel.getItemId());
						panel.updateWikiPrice(pd);
					}
				});
			}
			catch (Exception e)
			{
				log.error("Failed to refresh prices", e);
			}
		});
	}

	/**
	 * Fetch and update timeseries graphs for all current offer panels.
	 */
	private void refreshAllTimeseries()
	{
		for (OfferPanel panel : offerPanels)
		{
			refreshTimeseries(panel.getItemId(), panel);
		}
	}

	/**
	 * Fetch and update timeseries graph for a specific offer panel based on its selected timeframe.
	 */
	private void refreshTimeseries(int itemId, OfferPanel panel)
	{
		executor.submit(() ->
		{
			try
			{
				String timeframe = panel.getTimestep();
				String apiTimestep = "5m"; // Default for 12H and 1D
				if ("7D".equals(timeframe)) apiTimestep = "1h";
				else if ("30D".equals(timeframe)) apiTimestep = "6h";

				List<TimeseriesEntry> timeseries = priceClient.fetchTimeseries(itemId, apiTimestep);

				// For 12H, we only want the last 12 hours of data (12 * 60 / 5 = 144 entries)
				if ("12H".equals(timeframe) && timeseries.size() > 144)
				{
					timeseries = timeseries.subList(timeseries.size() - 144, timeseries.size());
				}

				final List<TimeseriesEntry> finalTimeseries = timeseries;
				SwingUtilities.invokeLater(() -> panel.updateTimeseries(finalTimeseries));
			}
			catch (Exception e)
			{
				log.error("Failed to fetch timeseries for item {}", itemId, e);
			}
		});
	}

	private void rebuildOffersList()
	{
		offersPanel.removeAll();
		offerPanels.clear();

		if (activeOffers.isEmpty())
		{
			offersPanel.add(statusLabel);
		}
		else
		{
			for (OfferInfo info : activeOffers.values())
			{
				OfferPanel[] panelHolder = new OfferPanel[1];
				panelHolder[0] = new OfferPanel(
					info.itemId, info.itemName, info.isBuy,
					info.totalQuantity, info.quantityFilled, info.price, config, itemManager,
					() -> refreshTimeseries(info.itemId, panelHolder[0])
				);
				OfferPanel panel = panelHolder[0];

				// Apply any cached wiki price immediately
				PriceData cached = priceClient.getPrice(info.itemId);
				if (cached != null)
				{
					panel.updateWikiPrice(cached);
				}

				panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
				offerPanels.add(panel);
				offersPanel.add(panel);
				offersPanel.add(Box.createVerticalStrut(4));
			}
		}

		offersPanel.revalidate();
		offersPanel.repaint();

		// Fetch prices and graphs in background for all items
		refreshPrices();
		refreshAllTimeseries();
	}

	public void shutdown()
	{
		executor.shutdownNow();
		if (searchPanel != null)
		{
			searchPanel.shutdown();
		}
	}

	/**
	 * Internal data holder for offer information.
	 */
	private static class OfferInfo
	{
		int slot;
		int itemId;
		String itemName;
		boolean isBuy;
		int totalQuantity;
		int quantityFilled;
		int price;
		GrandExchangeOfferState state;
	}
}
