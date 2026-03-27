package com.gehelper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import java.awt.*;

/**
 * Overlay that shows current wiki buy/sell prices on the Grand Exchange interface.
 */
@Slf4j
public class GEOfferOverlay extends Overlay
{
	private static final Color BUY_COLOR = new Color(0, 200, 0);
	private static final Color SELL_COLOR = new Color(255, 80, 80);
	private static final Color BG_COLOR = new Color(0, 0, 0, 180);

	private final Client client;
	private final WikiPriceClient priceClient;
	private final GEHelperConfig config;

	@Inject
	public GEOfferOverlay(Client client, WikiPriceClient priceClient, GEHelperConfig config)
	{
		this.client = client;
		this.priceClient = priceClient;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGH);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showPricesOnOffer())
		{
			return null;
		}

		Widget geWidget = client.getWidget(InterfaceID.GRAND_EXCHANGE, 0);
		if (geWidget == null || geWidget.isHidden())
		{
			return null;
		}

		g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
		FontMetrics fm = g.getFontMetrics();

		// Iterate GE slots and show prices for active offers
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

			// Find the GE slot widget to position our overlay
			// GE slots are children 7-14 of the GE interface (widget group 465)
			Widget slotWidget = client.getWidget(InterfaceID.GRAND_EXCHANGE, 7 + slot);
			if (slotWidget == null || slotWidget.isHidden())
			{
				continue;
			}

			Rectangle bounds = slotWidget.getBounds();
			if (bounds == null)
			{
				continue;
			}

			// Draw price info below each GE slot
			int textY = bounds.y + bounds.height + 2;
			int textX = bounds.x + 2;

			if (priceData.getHigh() != null)
			{
				String buyText = "Buy: " + QuantityFormatter.formatNumber(priceData.getHigh());
				drawOutlinedText(g, fm, buyText, textX, textY, BUY_COLOR);
				textY += fm.getHeight() + 1;
			}

			if (priceData.getLow() != null)
			{
				String sellText = "Sell: " + QuantityFormatter.formatNumber(priceData.getLow());
				drawOutlinedText(g, fm, sellText, textX, textY, SELL_COLOR);
			}
		}

		return null;
	}

	private void drawOutlinedText(Graphics2D g, FontMetrics fm, String text, int x, int y, Color color)
	{
		// Background
		int w = fm.stringWidth(text) + 6;
		int h = fm.getHeight() + 2;
		g.setColor(BG_COLOR);
		g.fillRoundRect(x - 2, y - fm.getAscent(), w, h, 4, 4);

		// Text
		g.setColor(color);
		g.drawString(text, x + 1, y);
	}
}
