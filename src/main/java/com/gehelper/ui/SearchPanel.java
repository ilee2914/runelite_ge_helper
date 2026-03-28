package com.gehelper.ui;

import com.gehelper.GEHelperConfig;
import com.gehelper.ItemMapping;
import com.gehelper.PriceData;
import com.gehelper.TimeseriesEntry;
import com.gehelper.WikiPriceClient;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
public class SearchPanel extends PluginPanel
{
	private final WikiPriceClient priceClient;
	private final GEHelperConfig config;
	private final ItemManager itemManager;
	private final ConfigManager configManager;

	private final JPanel searchResultsPanel;
	private final IconTextField searchBar;
	private final JPopupMenu autocompleteMenu = new JPopupMenu();
	private int selectedIndex = -1;

	private final LinkedHashSet<Integer> searchHistory = new LinkedHashSet<>();
	private final List<SearchItemPanel> resultPanels = new ArrayList<>();

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public SearchPanel(WikiPriceClient priceClient, GEHelperConfig config, ItemManager itemManager, ConfigManager configManager)
	{
		super(false);
		this.priceClient = priceClient;
		this.config = config;
		this.itemManager = itemManager;
		this.configManager = configManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Search header
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(8, 8, 8, 8));

		searchBar = new IconTextField();
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(100, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setMinimumSize(new Dimension(0, 30));
		searchBar.addActionListener(this::onSearch);
		searchBar.addClearListener(() -> {
			searchBar.setText("");
			searchBar.setIcon(IconTextField.Icon.SEARCH);
		});
		
		autocompleteMenu.setFocusable(false);
		
		searchBar.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (!autocompleteMenu.isVisible())
				{
					return;
				}

				int menuItems = autocompleteMenu.getComponentCount();
				if (menuItems == 0)
				{
					return;
				}

				if (e.getKeyCode() == KeyEvent.VK_DOWN)
				{
					selectedIndex++;
					if (selectedIndex >= menuItems)
					{
						selectedIndex = 0;
					}
					updateMenuSelection();
				}
				else if (e.getKeyCode() == KeyEvent.VK_UP)
				{
					selectedIndex--;
					if (selectedIndex < 0)
					{
						selectedIndex = menuItems - 1;
					}
					updateMenuSelection();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					if (selectedIndex >= 0 && selectedIndex < menuItems)
					{
						JMenuItem item = (JMenuItem) autocompleteMenu.getComponent(selectedIndex);
						item.doClick();
						e.consume();
					}
				}
			}
		});

		searchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e) { updateAutocomplete(); }
			@Override
			public void removeUpdate(DocumentEvent e) { updateAutocomplete(); }
			@Override
			public void changedUpdate(DocumentEvent e) { updateAutocomplete(); }
		});

		header.add(searchBar, BorderLayout.CENTER);

		searchResultsPanel = new JPanel();
		searchResultsPanel.setLayout(new BoxLayout(searchResultsPanel, BoxLayout.Y_AXIS));
		searchResultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(searchResultsPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(wrapper);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		add(header, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
		
		// Load history
		String historyData = configManager.getConfiguration("gehelper", "searchHistory");
		if (historyData != null && !historyData.isEmpty())
		{
			for (String part : historyData.split(","))
			{
				try
				{
					int id = Integer.parseInt(part.trim());
					searchHistory.add(id);
				}
				catch (NumberFormatException ignored) {}
			}
		}

		// Defer the initial UI build to a background thread to ensure item mappings are available
		executor.submit(() ->
		{
			priceClient.fetchItemMapping();
			SwingUtilities.invokeLater(this::rebuildSearchResults);
		});
	}

	private void onSearch(ActionEvent e)
	{
		String query = searchBar.getText();
		if (query == null || query.trim().isEmpty())
		{
			return;
		}

		ItemMapping mapping = priceClient.searchItem(query);
		if (mapping != null)
		{
			searchBar.setIcon(IconTextField.Icon.SEARCH);
			searchBar.setText(""); // clear upon successful search
			autocompleteMenu.setVisible(false);
			
			int itemId = mapping.getId();
			pushToTop(itemId);
		}
		else
		{
			searchBar.setIcon(IconTextField.Icon.ERROR);
		}
	}

	private void updateMenuSelection()
	{
		if (selectedIndex >= 0 && selectedIndex < autocompleteMenu.getComponentCount())
		{
			MenuElement[] path = new MenuElement[2];
			path[0] = autocompleteMenu;
			path[1] = (MenuElement) autocompleteMenu.getComponent(selectedIndex);
			MenuSelectionManager.defaultManager().setSelectedPath(path);
		}
	}

	private void updateAutocomplete()
	{
		String query = searchBar.getText();
		if (query == null || query.length() < 4)
		{
			if (autocompleteMenu.isVisible())
			{
				autocompleteMenu.setVisible(false);
			}
			return;
		}

		executor.submit(() ->
		{
			List<ItemMapping> results = priceClient.searchItems(query, 10);
			SwingUtilities.invokeLater(() ->
			{
				// Check if text has changed since we started the lookup
				if (!query.equals(searchBar.getText()))
				{
					return;
				}

				autocompleteMenu.removeAll();
				selectedIndex = -1;

				if (results.isEmpty())
				{
					if (autocompleteMenu.isVisible())
					{
						autocompleteMenu.setVisible(false);
					}
					return;
				}

				for (ItemMapping mapping : results)
				{
					net.runelite.client.util.AsyncBufferedImage img = itemManager.getImage(mapping.getId(), 1, false);
					JMenuItem item = new JMenuItem(mapping.getName(), new ImageIcon(img));
					img.onLoaded(() -> item.repaint());

					item.addActionListener(ev ->
					{
						searchBar.setIcon(IconTextField.Icon.SEARCH);
						searchBar.setText(""); // clear upon successful search
						autocompleteMenu.setVisible(false);
						pushToTop(mapping.getId());
					});
					autocompleteMenu.add(item);
				}

				if (searchBar.isShowing())
				{
					if (!autocompleteMenu.isVisible())
					{
						autocompleteMenu.show(searchBar, 0, searchBar.getHeight());
					}
					else
					{
						autocompleteMenu.pack();
					}
				}
			});
		});
	}

	private void pushToTop(int itemId)
	{
		searchHistory.remove(itemId);
		searchHistory.add(itemId);

		saveHistory();
		rebuildSearchResults();
	}

	private void saveHistory()
	{
		String toSave = searchHistory.stream().map(String::valueOf).collect(Collectors.joining(","));
		configManager.setConfiguration("gehelper", "searchHistory", toSave);
	}

	private void rebuildSearchResults()
	{
		searchResultsPanel.removeAll();
		resultPanels.clear();

		if (searchHistory.isEmpty())
		{
			JLabel emptyLabel = new JLabel("Search for an item above");
			emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			emptyLabel.setFont(FontManager.getRunescapeFont());
			emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
			emptyLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
			searchResultsPanel.add(emptyLabel);
		}
		else
		{
			// Convert to list to iterate back-to-front (newest first)
			List<Integer> historyList = new ArrayList<>(searchHistory);
			int count = 0;
			for (int i = historyList.size() - 1; i >= 0; i--)
			{
				int itemId = historyList.get(i);
				String itemName = priceClient.getItemName(itemId);

				final boolean loadData = count < 10;
				SearchItemPanel[] panelHolder = new SearchItemPanel[1];
				panelHolder[0] = new SearchItemPanel(
					itemId, itemName, config, itemManager,
					() -> {
						if (loadData) refreshTimeseries(itemId, panelHolder[0]);
					},
					() -> pushToTop(itemId)
				);
				SearchItemPanel panel = panelHolder[0];

				if (loadData)
				{
					// Apply any cached wiki price immediately
					PriceData cached = priceClient.getPrice(itemId);
					if (cached != null)
					{
						panel.updateWikiPrice(cached);
					}
				}
				else
				{
					panel.setNeedsLoadText();
				}

				panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
				resultPanels.add(panel);
				searchResultsPanel.add(panel);
				searchResultsPanel.add(Box.createVerticalStrut(4));
				count++;
			}
		}

		searchResultsPanel.revalidate();
		searchResultsPanel.repaint();

		refreshPrices();
		refreshAllTimeseries();
	}

	public void refreshPrices()
	{
		executor.submit(() ->
		{
			try
			{
				Map<Integer, PriceData> prices = priceClient.fetchLatestPrices();
				SwingUtilities.invokeLater(() ->
				{
					int count = 0;
					for (SearchItemPanel panel : resultPanels)
					{
						if (count < 10)
						{
							PriceData pd = prices.get(panel.getItemId());
							panel.updateWikiPrice(pd);
						}
						count++;
					}
				});
			}
			catch (Exception e)
			{
				log.error("Failed to refresh search prices", e);
			}
		});
	}

	private void refreshAllTimeseries()
	{
		int count = 0;
		for (SearchItemPanel panel : resultPanels)
		{
			if (count < 10)
			{
				refreshTimeseries(panel.getItemId(), panel);
			}
			count++;
		}
	}

	private void refreshTimeseries(int itemId, SearchItemPanel panel)
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

				// For 12H, we only want the last 12 hours of data
				if ("12H".equals(timeframe) && timeseries.size() > 144)
				{
					timeseries = timeseries.subList(timeseries.size() - 144, timeseries.size());
				}

				final List<TimeseriesEntry> finalTimeseries = timeseries;
				SwingUtilities.invokeLater(() -> panel.updateTimeseries(finalTimeseries));
			}
			catch (Exception e)
			{
				log.error("Failed to fetch search timeseries for item {}", itemId, e);
			}
		});
	}

	public void shutdown()
	{
		executor.shutdownNow();
	}
}
