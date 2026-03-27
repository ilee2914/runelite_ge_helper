# Development Journal

## 2026-03-27 — Project Initialization

- Created `CLAUDE.md` with project goals, API reference, and development rules
- Created `JOURNAL.md` for tracking changes
- Researched RuneLite plugin architecture (Gradle build, Plugin/Config/Panel classes, event system)
- Researched OSRS Wiki Real-time Prices API endpoints and response formats
- Created implementation plan for the GE Helper plugin

## 2026-03-27 — Full Implementation

### Project Scaffolding
- Created `build.gradle` with RuneLite client dependency, Lombok, and test config
- Created `settings.gradle` (project name: `ge-helper`)
- Created `runelite-plugin.properties` with plugin metadata
- Set up Gradle 8.5 wrapper (required for Java 21 compatibility)

### Data Models
- `PriceData.java` — latest buy/sell price from wiki API (high/highTime/low/lowTime)
- `ItemMapping.java` — item metadata from /mapping endpoint
- `TimeseriesEntry.java` — historical price data point from /timeseries endpoint

### API Client
- `WikiPriceClient.java` — OkHttp-based client with 60s cache for `/latest`, 10min cache for `/mapping`, and on-demand `/timeseries` fetching. Sets proper User-Agent per wiki policy.

### Plugin Core
- `GEHelperPlugin.java` — main orchestrator with `@Subscribe` for `GrandExchangeOfferChanged` and `WidgetLoaded` events, sidebar navigation registration, overlay registration, and periodic 60s price refresh
- `GEHelperConfig.java` — config interface with `showPricesOnOffer` and `graphTimestep` settings
- `GEOfferHelper.java` — helper for wiki URL generation and price logging
- `GEOfferOverlay.java` — renders buy/sell prices above GE widget slots using `OverlayLayer.ABOVE_WIDGETS`

### Sidebar UI
- `GEHelperPanel.java` — main sidebar panel with offer list, refresh button, and price graph
- `OfferPanel.java` — individual offer row with item name, buy/sell badge, progress bar, offer price, wiki prices, and wiki link
- `PriceGraphPanel.java` — custom Graphics2D line chart with buy (green) and sell (red) lines, grid, time labels, and legend

### Build Fix
- Fixed `client.getGrandExchangeOffer(slot)` → `client.getGrandExchangeOffers()[slot]` in 3 files (RuneLite API uses plural method returning array)
- Upgraded Gradle wrapper from 7.6 to 8.5 for Java 21 compatibility

### Build Result
- **BUILD SUCCESSFUL** — all 14 source files compile without errors
- Only deprecation warning (expected with RuneLite's evolving API)
