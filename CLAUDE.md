# RuneLite GE Helper Plugin

## Project Goals

1. **GE Offer Status Enhancement**: Add a button to the Grand Exchange Offer Status screen that opens `prices.runescape.wiki` for the selected item. Show the current instant-buy (high) and instant-sell (low) prices alongside each offer.

2. **Sidebar Price Tracker Panel**: Add a sidebar panel that:
   - Tracks progress of all the player's current GE offers (buy/sell, item name, quantity, progress)
   - Displays a price graph showing the day's price history for selected items
   - Shows current buy and sell prices pulled from the OSRS Wiki Real-time Prices API

## Developer Setup (Jagex Accounts)

This project uses Jagex Account login. Credentials must be written once before `.\gradlew.bat run` will work:

1. Open **Start Menu** → search for **"RuneLite (configure)"** → open it
2. In the **Client arguments** box add: `--insecure-write-credentials`
3. Click **Save**
4. Launch RuneLite **once** via the Jagex Launcher — it writes your credentials to `.runelite/credentials.properties`
5. From now on, `.\gradlew.bat run` will use those credentials automatically

> ⚠️ **Never share `credentials.properties`** — it bypasses your password. Delete it when done developing.
> Reference: https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts


This project targets **Old School RuneScape (OSRS)** only.

- **Base URL**: `https://prices.runescape.wiki/api/v1/osrs`
- **Endpoints**:
  - `/latest` - Current high/low prices for all items
  - `/mapping` - Item ID to name mapping with metadata
  - `/timeseries?timestep=5m&id={itemId}` - Historical price data (supports 5m, 1h, 6h, 24h)
- **User-Agent**: Must be set to a descriptive value per wiki's acceptable use policy

### Additional API References
- **OSRS Wiki Real-time Prices**: https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices
- **GE Market Watch / Usage and APIs** (use OSRS-specific endpoints only): https://runescape.wiki/w/RuneScape:Grand_Exchange_Market_Watch/Usage_and_APIs

## Rules

1. **Journal**: Keep `JOURNAL.md` updated with all code changes, features added, and decisions made.
2. **Code Style**: Follow RuneLite plugin conventions — use `@Inject`, `@Subscribe`, Lombok annotations (`@Slf4j`, `@Getter`), and Guice DI.
3. **API Etiquette**: Cache API responses and respect the 60-second update interval. Never loop item-by-item; use bulk endpoints.
4. **Package**: `com.gehelper` — all classes live under this package.
5. **Java Version**: Target Java 11 (RuneLite requirement).
6. **Dependencies**: Only use libraries available through RuneLite's client dependency (OkHttp, Gson, Guava, Swing).
