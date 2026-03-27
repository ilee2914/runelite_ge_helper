package com.gehelper.ui;

import com.gehelper.TimeseriesEntry;
import lombok.Setter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Custom Swing component that draws a line chart of buy/sell prices over time.
 */
public class PriceGraphPanel extends JPanel
{
	private static final int RIGHT_PADDING = 4;
	private static final int LEFT_MARGIN = 3;  // gap between label and graph edge
	private static final int TOP_PADDING = 18;
	private static final int BOTTOM_PADDING = 16; // room for time labels
	private static final Color BUY_COLOR = new Color(0, 190, 0);
	private static final Color SELL_COLOR = new Color(220, 60, 60);
	private static final Color GRID_COLOR = new Color(60, 60, 60);
	private static final Color AXIS_COLOR = new Color(180, 180, 180);
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter TOOLTIP_TIME_FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm");

	@Setter
	private List<TimeseriesEntry> data;

	@Setter
	private String itemName = "";

	@Setter
	private boolean showLegend = false;

	@Setter
	private boolean showTitle = true;

	private Point hoveredPoint = null;

	public PriceGraphPanel()
	{
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(0, 140));
		setMinimumSize(new Dimension(100, 100));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

		addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				hoveredPoint = e.getPoint();
				repaint();
			}
		});

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent e)
			{
				hoveredPoint = null;
				repaint();
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (data == null || data.isEmpty())
		{
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g2.setFont(FontManager.getRunescapeSmallFont());
			String msg = data == null ? "Loading price data..." : "No price data available";
			FontMetrics fm = g2.getFontMetrics();
			int x = (getWidth() - fm.stringWidth(msg)) / 2;
			int y = getHeight() / 2;
			g2.drawString(msg, x, y);
			return;
		}

		// Title
		g2.setFont(FontManager.getRunescapeSmallFont());
		if (showTitle && !itemName.isEmpty())
		{
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g2.drawString("Price History", LEFT_MARGIN, 13);
		}

		// Measure widest price label to set left padding dynamically
		FontMetrics fmLabel = g2.getFontMetrics();
		int labelWidth = Math.max(fmLabel.stringWidth("999K"), fmLabel.stringWidth("9.9M"));
		int graphX = labelWidth + LEFT_MARGIN + 4;
		int graphY = showTitle ? TOP_PADDING : 6;
		int graphW = getWidth() - graphX - RIGHT_PADDING;
		int graphH = getHeight() - graphY - BOTTOM_PADDING;

		if (graphW <= 0 || graphH <= 0)
		{
			return;
		}

		// Find price range
		long minPrice = Long.MAX_VALUE;
		long maxPrice = Long.MIN_VALUE;
		for (TimeseriesEntry entry : data)
		{
			if (entry.getAvgHighPrice() != null)
			{
				minPrice = Math.min(minPrice, entry.getAvgHighPrice());
				maxPrice = Math.max(maxPrice, entry.getAvgHighPrice());
			}
			if (entry.getAvgLowPrice() != null)
			{
				minPrice = Math.min(minPrice, entry.getAvgLowPrice());
				maxPrice = Math.max(maxPrice, entry.getAvgLowPrice());
			}
		}

		if (minPrice == Long.MAX_VALUE || maxPrice == Long.MIN_VALUE)
		{
			return;
		}

		// Add 5% padding to price range
		long priceRange = maxPrice - minPrice;
		if (priceRange == 0) priceRange = 1;
		long pricePad = Math.max(1, priceRange / 20);
		minPrice -= pricePad;
		maxPrice += pricePad;
		priceRange = maxPrice - minPrice;

		// Draw grid lines and price labels
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();
		int numGridLines = 4;
		for (int i = 0; i <= numGridLines; i++)
		{
			int y = graphY + (int) ((double) i / numGridLines * graphH);
			long price = maxPrice - (long) ((double) i / numGridLines * priceRange);

			g2.setColor(GRID_COLOR);
			g2.drawLine(graphX, y, graphX + graphW, y);

			g2.setColor(AXIS_COLOR);
			String label = formatPrice(price);
			g2.drawString(label, graphX - fm.stringWidth(label) - LEFT_MARGIN, y + fm.getAscent() / 2 - 1);
		}

		// Draw time labels (max 3 to avoid crowding)
		if (data.size() > 1)
		{
			int numTimeLabels = Math.min(3, data.size());
			for (int i = 0; i < numTimeLabels; i++)
			{
				int idx = (numTimeLabels == 1) ? 0
					: (int) ((double) i / (numTimeLabels - 1) * (data.size() - 1));
				int x = graphX + (int) ((double) idx / (data.size() - 1) * graphW);
				long ts = data.get(idx).getTimestamp();
				LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneId.systemDefault());
				String timeLabel = dt.format(TIME_FMT);

				// Clamp so labels don't overflow left/right edges
				int labelX = x - fm.stringWidth(timeLabel) / 2;
				labelX = Math.max(graphX, Math.min(labelX, graphX + graphW - fm.stringWidth(timeLabel)));

				g2.setColor(AXIS_COLOR);
				g2.drawString(timeLabel, labelX, graphY + graphH + fm.getAscent() + 1);
			}
		}

		// Draw buy price line (high = instant buy)
		drawLine(g2, data, true, graphX, graphY, graphW, graphH, minPrice, priceRange, BUY_COLOR);

		// Draw sell price line (low = instant sell)
		drawLine(g2, data, false, graphX, graphY, graphW, graphH, minPrice, priceRange, SELL_COLOR);

		// Optional legend (config-controlled)
		if (showLegend)
		{
			int legendY = graphY + graphH + fm.getAscent() + 1;
			g2.setColor(BUY_COLOR);
			g2.fillRect(graphX + graphW - 85, legendY - 6, 7, 7);
			g2.drawString("Buy", graphX + graphW - 75, legendY);

			g2.setColor(SELL_COLOR);
			g2.fillRect(graphX + graphW - 48, legendY - 6, 7, 7);
			g2.drawString("Sell", graphX + graphW - 38, legendY);
		}

		// Hover tooltip
		if (hoveredPoint != null && data.size() > 1 && graphW > 0)
		{
			// Check if mouse is near the graph horizontally
			if (hoveredPoint.x >= graphX - 10 && hoveredPoint.x <= graphX + graphW + 10)
			{
				int closestIdx = (int) Math.round((double)(hoveredPoint.x - graphX) / graphW * (data.size() - 1));
				closestIdx = Math.max(0, Math.min(closestIdx, data.size() - 1));

				TimeseriesEntry entry = data.get(closestIdx);
				int pointX = graphX + (int) ((double) closestIdx / (data.size() - 1) * graphW);

				// Draw vertical bar marker
				g2.setColor(new Color(255, 255, 255, 60));
				g2.drawLine(pointX, graphY, pointX, graphY + graphH);

				// Resolve data
				LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochSecond(entry.getTimestamp()), ZoneId.systemDefault());
				String timeStr = dt.format(TOOLTIP_TIME_FMT);
				String buyStr = entry.getAvgHighPrice() != null ? formatPrice(entry.getAvgHighPrice()) : "-";
				String sellStr = entry.getAvgLowPrice() != null ? formatPrice(entry.getAvgLowPrice()) : "-";

				String[] lines = { timeStr, "B: " + buyStr, "S: " + sellStr };

				// Dimensions
				g2.setFont(FontManager.getRunescapeSmallFont());
				FontMetrics ttFm = g2.getFontMetrics();
				int maxW = 40;
				for (String line : lines) maxW = Math.max(maxW, ttFm.stringWidth(line));
				int boxW = maxW + 10;
				int boxH = 4 + lines.length * ttFm.getHeight();

				int boxX = pointX + 8;
				if (boxX + boxW > getWidth() - RIGHT_PADDING)
				{
					boxX = pointX - boxW - 8;
				}
				int boxY = hoveredPoint.y - boxH - 5;
				if (boxY < TOP_PADDING)
				{
					boxY = hoveredPoint.y + 15;
				}

				// Background
				g2.setColor(new Color(40, 40, 40, 230));
				g2.fillRect(boxX, boxY, boxW, boxH);
				g2.setColor(new Color(100, 100, 100));
				g2.drawRect(boxX, boxY, boxW, boxH);

				// Text
				int textY = boxY + 2 + ttFm.getAscent();
				for (int i = 0; i < lines.length; i++)
				{
					if (i == 1) g2.setColor(BUY_COLOR);
					else if (i == 2) g2.setColor(SELL_COLOR);
					else g2.setColor(Color.WHITE);

					g2.drawString(lines[i], boxX + 5, textY);
					textY += ttFm.getHeight();
				}
			}
		}
	}

	private void drawLine(Graphics2D g2, List<TimeseriesEntry> entries, boolean isHigh,
						  int graphX, int graphY, int graphW, int graphH,
						  long minPrice, long priceRange, Color color)
	{
		Path2D.Double path = new Path2D.Double();
		boolean started = false;

		for (int i = 0; i < entries.size(); i++)
		{
			Long price = isHigh ? entries.get(i).getAvgHighPrice() : entries.get(i).getAvgLowPrice();
			if (price == null)
			{
				continue;
			}

			double x = graphX + (double) i / (entries.size() - 1) * graphW;
			double y = graphY + graphH - ((double) (price - minPrice) / priceRange * graphH);

			if (!started)
			{
				path.moveTo(x, y);
				started = true;
			}
			else
			{
				path.lineTo(x, y);
			}
		}

		if (started)
		{
			g2.setColor(color);
			g2.setStroke(new BasicStroke(1.5f));
			g2.draw(path);
		}
	}

	private String formatPrice(long price)
	{
		if (price >= 10_000_000)
		{
			return String.format("%.1fM", price / 1_000_000.0);
		}
		else if (price >= 100_000)
		{
			return String.format("%.0fK", price / 1_000.0);
		}
		else if (price >= 10_000)
		{
			return String.format("%.1fK", price / 1_000.0);
		}
		return String.valueOf(price);
	}
}
