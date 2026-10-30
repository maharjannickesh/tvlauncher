# TV Launcher v1 — Design

Date: 2026-10-29. Owner: Nickesh. Design source: `tv-launcher-design/DESIGN_SPEC.md` + `tv-launcher-design/mockups/*.dc.html` (Claude Design handoff). This document adds the decisions made in conversation and the v1 cut; where it is silent, the handoff spec and mockups are the authority for layout, sizes and colours.

## Intent

A personal home screen for the Living Room Chromecast with Google TV. Turning on the TV should feel like it greets Nickesh, then shows a calm, glassy dashboard: the time at home, the weather, three world clocks for the people he cares about, and his apps one click away. It replaces the busy, ad-driven stock Google TV home.

## Target device and stack

- Device: Chromecast with Google TV (Android 14, API 34, **armeabi-v7a 32-bit**, render surface 1920×1080 at 320 dpi, physical 4K). Verified over adb on 2026-10-29 (wireless adb notes in memory `living-room-tv-adb`).
- Stack (from the handoff, confirmed): Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), Material3 Compose, DataStore Preferences, Coroutines, OkHttp (for Open-Meteo JSON), kotlinx-serialization. Min SDK 26, target/compile SDK 36, Gradle 8.14.3, AGP 8.13, Kotlin 2.2, JDK 21.
- Package: `com.nickesh.tvlauncher`. Project lives at `~/ClaudeProjects/tvlauncher` (Android project at the root, `tv-launcher-design/` kept as the design reference). Own git repo. No cross-imports from other ClaudeProjects (project-isolation policy); copy anything useful.
- Fonts bundled as assets: Outfit 200–600, Roboto Mono 500/600 (downloaded from Google Fonts at build time of the repo, committed).
- Dev loop: `./gradlew installDebug` to the Chromecast over wireless adb (`-s 10.0.0.21:<port>`); an Android TV AVD (API 34, x86_64) is the fallback when the TV is off.

## Goals

1. Welcome splash → Home with real data within ~2 s on the Chromecast.
2. Pixel-faithful to the mockups at 1920×1080 (safe margins 96 px sides / 60 px top).
3. Full D-pad navigation with the focus treatment in the spec (scale 1.1, 3 px white ring, accent glow).
4. Works as a **normal app** launched from the stock launcher. Home-button takeover is explicitly a later step (v2).
5. Screensaver is a real system screensaver (`DreamService`). Chromecast with Google TV has no picker for third-party screensavers (Ambient mode only), so it is selected over adb as a documented one-line setup step, the same way third-party screensavers like Aerial Views are installed on this device (builder call, after reviewer question):
   `adb shell settings put secure screensaver_components com.nickesh.tvlauncher/.ScreensaverService && adb shell settings put secure screensaver_enabled 1`. Verification: `adb shell cmd dreams start` (or the TV's idle timeout) shows our screensaver.

## Non-goals (v1)

- Google Photos backgrounds (needs a Google Cloud OAuth client only Nickesh can create, and a phone sign-in flow). The Background screen shows the Google Photos source as "Coming soon" and disabled.
- Replacing the stock launcher / HOME intent handling. The manifest declares only `MAIN` + `LEANBACK_LAUNCHER` in v1. (Adding `HOME` + disabling `com.google.android.apps.tv.launcherx` over adb is the v2 switch-over.)
- "Hold OK to rearrange" the apps row. v1 orders apps by a fixed favourites list then alphabetical.
- Multi-user profiles, parental controls, content rows/recommendations.

## Anti-goals

- A black screen or crash on launch is a failure even if everything else works; every network fetch degrades to cached/last-known or placeholder data.
- No analytics, no accounts, no background services beyond the DreamService. No Google logo/branding (generic mic icon on the assistant pill).

## Screens

### 0. Welcome (new, from conversation)

- Full-bleed background = the current background source (scene or colour), dimmed 35 %.
- Centre text "Welcome Nickesh!!!" in Outfit 300, ~120 px, with a soft glow: a radial accent-coloured halo behind the text that breathes (alpha 0.35↔0.75, scale 0.95↔1.05, 1.6 s ease-in-out loop), plus a slow shimmer sweep across the text. Three exclamation marks exactly as Nickesh wrote them. The name comes from settings (default "Nickesh").
- Stays up until the Home data is ready (installed apps enumerated + background decoded; weather is NOT awaited) and at least 1.2 s have elapsed, then cross-fades (400 ms) into Home. Hard cap 4 s, then Home shows with placeholders.
- Shown on every cold start of the activity; not shown when returning from another app within the same process (warm resume).

### 1. Home — per `Main.dc.html`

Implements the mockup exactly: top bar (greeting by home-time hour, pin + home city, "Ask" pill, four round glass buttons), hero clock (220 px weight 200, 12 h with AM/PM or 24 h), date line, weather card (580 px), three world-clock cards with analog dials (second hand ticks each second), apps row.

Behaviour decisions (builder calls, listed so Nickesh can veto):

- **Apps row**: query `CATEGORY_LEANBACK_LAUNCHER` activities, show the first 8 in this order: a favourites list in settings (default: YouTube, Netflix, Prime Video, Disney+, Spotify, Plex, Stream Everything `com.nirajgtm.xtreamtv`, Google Play) that are actually installed, then the rest alphabetically; last tile is **All apps**, which opens a full-screen 6-column grid of every leanback app. Tiles use `loadBanner()` scaled into 172×104, falling back to the app icon centred on the tile tone from the mockup, then to the initial letter. Launch with `getLeanbackLaunchIntentForPackage`. Listen for package added/removed/changed broadcasts and refresh.
- **"Ask" pill**: fires `Intent.ACTION_ASSIST`; if nothing handles it, `RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE`; if neither, a toast. Label text "Ask Gemini" as in the mockup, generic mic icon.
- **Search** button: `Intent.ACTION_SEARCH` via `SearchManager` global search; fallback to the assistant.
- **Input source** button: Chromecast has no HDMI inputs, so this opens the system **Inputs** settings (`android.settings.INPUT_METHOD`? no — `com.android.tv.settings` "Inputs" page) if resolvable, else system Settings. Hidden entirely if neither resolves.
- **Settings** button: opens our Settings screen (see 4).
- **Profile** button: opens system **Accounts & sign-in** settings; fallback system Settings.
- **Weather**: Open-Meteo forecast API for the configured city (geocoded once via Open-Meteo geocoding, lat/lon cached), current temp + condition + today's hi/lo + 4-day forecast, `temperature_unit` by setting. Refresh every 30 min while the activity is resumed; last response cached in DataStore and shown on start. Weather-code → the four mockup icons (sun / partly / cloud / rain; snow and storm map to rain in v1). Condition text from the WMO code table.
- **Clocks**: `java.time` per IANA zone; hero refreshes each minute (on the minute boundary), dials each second. Meta line "Tomorrow · +9h 45m" computed as in the mockup JS (round to 15 min; "Today/Tomorrow/Yesterday"; "Same time"). Sun icon 06:00–17:59 local, moon otherwise.
- **Focus order**: apps row has initial focus. D-pad up goes to the clocks row, then weather/top bar.
- **Glass**: `RenderEffect` blur on API 31+ (device is 34), plain translucent fill below.

### 2. Screensaver — per `Screensaver.dc.html`

`DreamService` with a Compose view. Flip clock with the two-phase flip (top folds 0→−90° 300 ms ease-in, then new bottom drops 90→0° 300 ms ease-out) implemented with `graphicsLayer` rotationX + camera distance; optional seconds (card sizes per spec). Dim overlay from settings (default 0.35). Pill bar with weather (cached) and three world clocks. The dream subtly shifts the whole content by ±24 px every 60 s to avoid burn-in (builder call).

### 3. Background settings — per `Background.dc.html`

Sources: **Google Photos** (row visible, marked "Coming soon", not focusable in v1), **Built-in scenes** (Dusk / Night / Forest, drawn as vector scenes exactly from the mockup SVG), **Solid colour** (the four swatches). "Change photo" interval chips and Shuffle toggle are shown and persisted but only affect scenes in v1 (rotate among scenes on the interval when Shuffle is on; builder call so the controls do something).

### 4. Settings (new, minimal; needed because the mockup's prop panel is where these lived)

A simple TV list screen: Name (text, for the welcome), Home city + time zone, three world clocks (city + zone from the spec's zone list), Weather city, Units °F/°C, 24-hour clock, Accent colour (4 options), Screensaver dim + show seconds, Background (opens screen 3). Uses tv-material `ListItem`s and a side-sheet editor; text entry via the system TV keyboard.

Defaults: name Nickesh, home Columbus / America/New_York, clocks Kathmandu / London / Tokyo, weather "Columbus, OH", °F, 12 h, accent `#8FB8FF`, background scene Dusk.

**Background is one shared setting** (builder call, after reviewer question): Home, Welcome and Screensaver all show the same source/scene/colour, as the Background screen's subtitle says; the Screensaver only adds its dim overlay. The Night scene in the screensaver mockup is a preview choice, not a separate default. Shuffle rotation runs once, app-wide.

**Favourites list** (builder call): a stored ordered list of package names in the `Settings` data class with the code default above. No editor UI in v1; editing arrives with the rearrange feature in v2.

## Data and persistence

DataStore Preferences, single `Settings` data class with defaults above. Weather cache (JSON string + fetched-at) and geocode cache in the same store. No database.

## Testing policy (builder call)

Unit tests for pure logic only: clock math (offset/rounding/day label, 12/24 h formatting), weather-code mapping, app ordering. No UI/instrumentation tests in v1; the visual check is a screenshot pass on the real Chromecast (`adb exec-out screencap`) against the mockups for each screen.

## Delivery

1. Scaffold + fonts + theme + Welcome + Home skeleton with real clocks → install on TV, screenshot.
2. Apps row with real banners + launch.
3. Weather.
4. Screensaver DreamService.
5. Background + Settings screens.
6. Polish pass against mockups; unit tests green; commit + push to GitHub (`maharjannickesh/tvlauncher`, private).

## Reviewer round (2026-10-29)

A fresh builder-check raised three questions; all three were settled as builder calls and folded in above: shared background setting, adb-selected screensaver, favourites as a stored field without UI. Also from that round: mockup pixels map to device pixels at 320 dpi via a density-independent 1920-wide canvas scale; the 9-tile apps row overflows the 1728 px content width by 28 px at the 26 px gap, so the gap drops to ~22 px; the "Hold OK to rearrange" hint is dropped in v1 since rearrange is cut.

## Left to the builder

Exact Compose structure, focus-restorer handling, blur implementation details, vector conversion of the scene SVGs, Open-Meteo field selection, how the flip animation state is keyed per digit.

## v2 backlog (not in this design)

Home-button takeover (adb disable of launcherx), Google Photos Picker API flow with QR + device-code OAuth, rearrange apps row, snow/storm icons, per-clock weather.
