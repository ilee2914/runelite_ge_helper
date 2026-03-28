package com.github.ilee2.gehelper.ui;

import com.github.ilee2.gehelper.GEHelperConfig;
import com.github.ilee2.gehelper.PriceData;
import com.github.ilee2.gehelper.TimeseriesEntry;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class SearchItemPanel extends JPanel
{
	private static final Color HOVER_COLOR = new Color(60, 60, 60);

	private final int itemId;
	private final JLabel wikiPriceLabel;
	private final PriceGraphPanel graphPanel;

	private String currentTimestep = "12H";
	private JLabel label12H, label1D, label7D, label30D;

	public SearchItemPanel(int itemId, String itemName, GEHelperConfig config, ItemManager itemManager, Runnable onTimeframeChange, Runnable onClickPanel)
	{
		this.itemId = itemId;

		setLayout(new BorderLayout(5, 2));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(6, 8, 6, 8));

		// Top row: [icon] item name
		JPanel topRow = new JPanel(new BorderLayout(4, 0));
		topRow.setOpaque(false);

		// Item icon
		JLabel iconLabel = new JLabel();
		if (itemManager != null)
		{
			itemManager.getImage(itemId, 1, false).addTo(iconLabel);
		}

		JLabel nameLabel = new JLabel(itemName);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(FontManager.getRunescapeBoldFont());

		JPanel namePanel = new JPanel(new BorderLayout(4, 0));
		namePanel.setOpaque(false);
		namePanel.add(iconLabel, BorderLayout.WEST);
		namePanel.add(nameLabel, BorderLayout.CENTER);
		topRow.add(namePanel, BorderLayout.WEST);

		// Price row
		JPanel priceRow = new JPanel(new BorderLayout());
		priceRow.setOpaque(false);

		wikiPriceLabel = new JLabel("");
		wikiPriceLabel.setFont(FontManager.getRunescapeSmallFont());
		priceRow.add(wikiPriceLabel, BorderLayout.WEST);

		JPanel infoPanel = new JPanel();
		infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
		infoPanel.setOpaque(false);
		infoPanel.add(topRow);
		infoPanel.add(Box.createVerticalStrut(3));
		infoPanel.add(priceRow);

		// Graph header
		JPanel graphHeader = new JPanel(new BorderLayout());
		graphHeader.setOpaque(false);

		JLabel priceHistoryLabel = new JLabel("Price History");
		priceHistoryLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		priceHistoryLabel.setFont(FontManager.getRunescapeSmallFont());
		graphHeader.add(priceHistoryLabel, BorderLayout.WEST);

		JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		rightHeader.setOpaque(false);

		label12H = createTimeframeLabel("12H", "12H", onTimeframeChange);
		label1D = createTimeframeLabel("1D", "1D", onTimeframeChange);
		label7D = createTimeframeLabel("7D", "7D", onTimeframeChange);
		label30D = createTimeframeLabel("30D", "30D", onTimeframeChange);
		updateTimeframeLabels();

		rightHeader.add(label12H);
		rightHeader.add(label1D);
		rightHeader.add(label7D);
		rightHeader.add(label30D);

		JLabel wikiLink = new JLabel("<html><u>Wiki</u></html>");
		wikiLink.setForeground(new Color(100, 149, 237));
		wikiLink.setFont(FontManager.getRunescapeSmallFont());
		wikiLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		wikiLink.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse("https://prices.runescape.wiki/osrs/item/" + itemId);
			}
		});
		rightHeader.add(wikiLink);
		graphHeader.add(rightHeader, BorderLayout.EAST);

		// Graph
		graphPanel = new PriceGraphPanel();
		graphPanel.setShowTitle(false);
		graphPanel.setShowLegend(config.showGraphLegend());
		graphPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setOpaque(false);
		infoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		graphHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		contentPanel.add(infoPanel);
		contentPanel.add(Box.createVerticalStrut(6));
		contentPanel.add(graphHeader);
		contentPanel.add(Box.createVerticalStrut(4));
		contentPanel.add(graphPanel);

		add(contentPanel, BorderLayout.CENTER);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (onClickPanel != null)
				{
					onClickPanel.run();
				}
			}
		});
	}

	public void setNeedsLoadText()
	{
		wikiPriceLabel.setText("<html><font color='#AAAAAA'>Click to load data...</font></html>");
	}

	public int getItemId()
	{
		return itemId;
	}

	public void updateWikiPrice(PriceData priceData)
	{
		if (priceData != null)
		{
			StringBuilder sb = new StringBuilder("<html>");
			if (priceData.getHigh() != null)
			{
				sb.append("<font color='#00BE00'>Buy: ").append(QuantityFormatter.formatNumber(priceData.getHigh())).append("</font>");
			}
			if (priceData.getLow() != null)
			{
				if (priceData.getHigh() != null) sb.append("     ");
				sb.append("<font color='#DC3C3C'>Sell: ").append(QuantityFormatter.formatNumber(priceData.getLow())).append("</font>");
			}
			sb.append("</html>");
			wikiPriceLabel.setText(sb.toString());
		}
		else
		{
			wikiPriceLabel.setText("");
		}
	}

	public void updateTimeseries(List<TimeseriesEntry> timeseries)
	{
		graphPanel.setData(timeseries);
		graphPanel.repaint();
	}

	public String getTimestep()
	{
		return currentTimestep;
	}

	private JLabel createTimeframeLabel(String text, String timestep, Runnable onChange)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!currentTimestep.equals(timestep))
				{
					currentTimestep = timestep;
					updateTimeframeLabels();
					if (onChange != null) onChange.run();
				}
			}
			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setForeground(Color.WHITE);
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				updateTimeframeLabels();
			}
		});
		return label;
	}

	private void updateTimeframeLabels()
	{
		if (label12H != null) label12H.setForeground("12H".equals(currentTimestep) ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		if (label1D != null) label1D.setForeground("1D".equals(currentTimestep) ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		if (label7D != null) label7D.setForeground("7D".equals(currentTimestep) ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		if (label30D != null) label30D.setForeground("30D".equals(currentTimestep) ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
	}
}
