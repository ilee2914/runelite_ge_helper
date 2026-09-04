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

## 2026-08-17 — Loading the plugin in the normally installed client

Goal: have the plugin load in the RuneLite client launched from the Steam shortcut
(a non-Steam game pointing at `%LOCALAPPDATA%\RuneLite\RuneLite.exe`), not just in
the Gradle dev client.

### How RuneLite sideloading actually works

- `PluginManager.loadSideLoadPlugins()` scans `~/.runelite/sideloaded-plugins/*.jar`,
  but returns immediately unless `developerMode` is true.
- `RuneLite.main` computes it as
  `options.has("developer-mode") && RuneLiteProperties.getLauncherVersion() == null`,
  and `getLauncherVersion()` is just `System.getProperty("runelite.launcher.version")`.
- The launcher calls `setJvmParams(properties)` — which includes
  `runelite.launcher.version` — before it picks a launch mode, so **JVM, FORK and
  REFLECT modes all disable developer mode**. No value in `settings.json` changes this.
  Verified empirically: adding `--developer-mode` to the launcher's client arguments
  reaches the client (it shows up in the startup log) and is still ignored.

### The one path that works

`Launcher.main` has an early return: given `--classpath`, it skips the bootstrap and
update flow and calls `ReflectionLauncher.launch()` in the current JVM, *before*
`setJvmParams` runs. So `runelite.launcher.version` is never set and developer mode
takes effect. `--classpath` takes bare artifact file names, resolved against
`~/.runelite/repository2`.

Developer mode additionally requires assertions (`-ea`) or `RuneLite.main` throws.
This was the cause of an early silent failure: the client died right after the
Preloader line with nothing else logged.

### Added

- `scripts/Launch-RuneLiteDev.ps1` — reads the same `bootstrap.json` the launcher
  reads, downloads any artifacts missing from `repository2` (SHA-256 verified),
  builds the classpath, and starts the client through the `--classpath` path with
  `-ea`. Developer mode is passed via the `RUNELITE_ARGS` environment variable, which
  the launcher appends to the client arguments, so RuneLite's own `settings.json` is
  left untouched. Falls back to launching `RuneLite.exe` normally on any error.
  Copies the newest `build/libs/ge-helper-*.jar` into the sideload directory if it is
  newer than the installed one.
- `installSideload` / `uninstallSideload` Gradle tasks for the sideload directory.

### Known cosmetic issue

Developer mode also loads RuneLite's built-in Developer Tools plugin, which fails with
`NoClassDefFoundError: net/runelite/api/gameval/VarbitID` — the launcher ships a
stripped `runelite-api-*-runtime.jar` that omits the `gameval` classes (confirmed: the
class is in none of the 35 jars in `repository2`). It logs one error plus a follow-up
NPE from `ClientUI.removeNavigation` during cleanup, then startup continues normally.

### Verified

Client starts as `(launcher version unknown)`, logs
`Side-loading plugin C:\Users\ilee2\.runelite\sideloaded-plugins\ge-helper.jar` and
`GE Helper started!`, and all 54 Plugin Hub plugins plus the GPU plugin still load.

## 2026-08-17 — Launch through RuneLite.exe instead of javaw

The first version of the launch script started the client with the bundled
`jre\bin\javaw.exe`. That worked, but it changed the executable that owns the game
window from `RuneLite.exe` to `javaw.exe`, which broke the Logitech G HUB mouse button
bindings — G HUB matches its per-application profile on the executable path, and its
stored profile points at `C:\Users\ilee2\AppData\Local\RuneLite\RuneLite.exe`
(confirmed by finding that path inside `%LOCALAPPDATA%\LGHUB\settings.db`).
Steam Input was unaffected, since Steam keys off the shortcut's app ID.

### Fix

`RuneLite.exe` is a native launcher that runs `RuneLite.jar`'s
`net.runelite.launcher.Launcher` in-process, so it can take the `--classpath` path too.
Its CLI (recovered from the usage strings inside the exe) is:

    RuneLite.exe -c [options] [-- [java arguments]]

- `-c` / `--cli` enables argument parsing. It is *not* the console flag; that is
  `--console`. `ForkLauncher` passes `-c` for the same reason.
- `-J <arg>` adds a JVM argument, passed as **two** separate arguments.
- Everything after `--` goes to `Launcher.main`.

So the client now starts as `RuneLite.exe -c -J <arg> ... -- --classpath <names>`, and
the game window belongs to `RuneLite.exe` at the exact path G HUB has profiled.

### Gotcha

In `-c` mode the exe does **not** apply the `vmArgs` from its own `config.json` — only
the `-J` arguments reach the JVM. The first attempt logged just
`-ea -Dsun.java2d.d3d=true -Dsun.java2d.opengl=false`, silently dropping `-Xss2m`,
`-Xmx768m` and `-Drunelite.launcher.blacklistedDlls` (the list that stops RTSS/Nahimic
overlay DLLs from crashing the client). The script now reads `config.json` and forwards
each `vmArg` explicitly, so the full argument list is restored.

### Steam Input only applies when Steam starts the client

Running the script directly gets you the plugin and the G HUB bindings, but *not* the
Steam controller layout: Steam Input only activates for a process Steam launched. This
briefly looked like the `RuneLite.exe` change had broken the controller, when the real
cause was testing against a client started from a terminal.

Confirmed by launching the shortcut through `steam://rungameid/<id>` (for a non-Steam
shortcut the id is `appid << 32 | 0x02000000`) and then inspecting the process:
`HKCU\Software\Valve\Steam\RunningAppID` becomes the shortcut's app id, and
`gameoverlayrenderer64.dll` is loaded into `RuneLite.exe`. Both bindings work in that
state. So the two input systems key off different things and both are satisfied at once:
G HUB matches the executable path, Steam matches the app id of the shortcut it launched.

## GE slot prices moved to the top right, icons redrawn

### Overlay placement

`GEOfferOverlay` drew each offer's wiki buy/sell prices starting at
`bounds.y + bounds.height + 2`, i.e. just *below* the slot widget and left-aligned. That
put them in the bottom-left dead space. They now start at `bounds.y + fm.getAscent() + 3`
and are right-aligned to `bounds.x + bounds.width - 3`, so the pair sits in the slot's
top-right corner.

Right-alignment needed a wrapper because `drawOutlinedText` measures its background box
from the *left* edge (`x - 2`, width `stringWidth + 6`). `drawOutlinedTextRight` takes the
desired right edge and back-solves the left `x` as `rightEdge - stringWidth - 4`, so the
box's right edge — not the text origin — is what lands on the target. Without that the two
lines would ragged-edge against each other, since "Buy:" and "Sell:" differ in width.

Only the per-slot prices moved; the offer-setup panel readout is unchanged.

### Icons

`src/main/resources/ge_icon.png` was a **640x640 JPEG** with a `.png` extension. JPEG has
no alpha channel, so the transparency checkerboard from whatever editor produced it had
been flattened into the image as literal gray squares — that checkerboard was rendering in
the sidebar as part of the icon. The size was wrong too: RuneLite does not scale
`NavigationButton` icons, and stock ones are small (the built-in Grand Exchange plugin's is
17x15).

Both icons are now regenerated from one script — a gold coin with a rising green arrow,
drawn in a 20-unit logical space, supersampled 16x and downsampled with LANCZOS for
antialiasing:

- `src/main/resources/ge_icon.png` — 20x20 RGBA, sized to sit alongside stock nav icons.
- `icon.png` — 48x72 RGBA, the Plugin Hub maximum, with the design rendered at 48x48 and
  centered. The previous one squeezed a wide coin-and-arrow layout into a portrait canvas.

The arrow carries a dark outline that is invisible against the sidebar's dark background
but separates it from the coin where the two shapes nearly touch.

## 2026-08-19 — Launch script now serves every plugin project

A second plugin project (`runelite_prayer_alert`) was added alongside this one.
`PluginManager#loadSideLoadPlugins` already loads every `.jar` in
`~/.runelite/sideloaded-plugins` under its own `PluginClassLoader`, so nothing about the
client side needed changing — but `scripts/Launch-RuneLiteDev.ps1` only refreshed the
hardcoded `ge-helper-*.jar`, leaving other projects to be installed by hand.

Generalised the sync block: it now walks the sibling folders under `custom_plugins/`, reads
`rootProject.name` from each `settings.gradle`, and copies that project's newest
`build/libs/<name>-*.jar` to `<name>.jar` in the sideload directory. Reading the Gradle root
name (rather than guessing from the folder name) keeps the launcher and each project's
`installSideload` task producing the same file name.

Everything else in the script — bootstrap update, classpath cache, JVM args, fallback to the
stock launcher — is unchanged, and the Steam shortcut still points at the same path.

## 2026-09-04 — High/low markers and per-graph refresh

Two sidebar graph requests: mark the extremes of the visible window, and let a single graph
be reloaded without re-fetching every graph on the panel.

### High/low lines

`PriceGraphPanel` already scanned the series to find the price range; it now also remembers
*where* each extreme occurred. The period high is the maximum `avgHighPrice` (top of the
green instant-buy line) and the period low is the minimum `avgLowPrice` (bottom of the red
instant-sell line) — so each line marks the extreme of the series it belongs to, not a
mixed high/low across both.

Each is drawn as a dotted horizontal line (`BasicStroke` with a 3/3 dash) across the plot,
a 5px dot on the point where it happened, and a value label (`H 1504K` / `L 1340K`) on a
translucent chip. Labels sit *inside* the plot area — the high one below its line, the low
one above its — because the plot only pads the range by 5%, so a label outside either line
would fall off the top or bottom of the component. When both labels would land in the same
band (a near-flat series) the low one is pinned to the left edge instead.

Config toggle `showHighLowLines` (default on), alongside the existing legend toggle. Like
the legend it applies when the panel rebuilds, since the plugin has no `ConfigChanged`
handler.

### Per-graph refresh

Each graph header gained a `⟳` next to the timeframe buttons, in both the Offers and Search
tabs. It refetches the timeseries for that one item and re-reads its wiki price from the
shared `/latest` snapshot (cache-aware, so no extra network call unless the 60s snapshot is
stale). The header's global refresh is unchanged.

For a failure to be visible per graph, `WikiPriceClient#fetchTimeseries` now throws
`IOException` instead of swallowing the error and returning an empty list — an empty list
was indistinguishable from an item with no trades, so a failed graph sat on "Loading price
data..." forever. `PriceGraphPanel` gained explicit `setLoading()` / `setError()` /
`setNeedsLoad()` states; the error state reads "Failed to load prices / Click refresh to
retry", and the `⟳` dims while a fetch for that graph is in flight.

Search results past the tenth are not auto-loaded, and their graphs now say "Click refresh
to load" rather than showing a loading spinner message that never resolves. Their `⟳`
loads on demand (the panel-click path that pushes an item to the top still works too).

The graph header's `FlowLayout` gap went from 6px to 4px so the extra icon still fits the
225px sidebar next to `Price History`, the four timeframes and the wiki link.
