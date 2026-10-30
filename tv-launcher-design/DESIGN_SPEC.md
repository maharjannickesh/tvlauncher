# Google TV Launcher: Design Handoff

Build a custom Android TV / Google TV home-screen launcher that matches the mockups in `mockups/`.
The `.dc.html` files are HTML design sources: open them as reference for exact layout, sizes and colors.
They use a small templating syntax (`{{hole}}`, `<sc-for>`, `<sc-if>`) and a `renderVals()` class; the JS in
each file shows the intended logic (time-zone math, flip-clock behaviour, state).

## Target stack (suggested)
- Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), min SDK 26
- Canvas: 1920×1080 dp-equivalent design; keep 96 px side / 60 px top safe margins (overscan)
- Font: Outfit (Google Fonts) weights 200–600; Roboto Mono 600 for flip-clock digits
- Full D-pad navigation; focused item = scale 1.1, 3 px white border, soft shadow + accent glow

## Screens

### 1. Home (`mockups/Main.dc.html`)
- **Background**: full-bleed image (user-chosen, see screen 3) with scrim:
  top 0–32% black 38%→0, bottom 52–100% 0→black 62%. Fallback built-in scenes: Dusk, Night, Forest.
- **Top bar** (56 px): left greeting by home-time hour (<5 night, <12 morning, <17 afternoon, else evening)
  + pin icon + home city. Right: "Ask Gemini" pill (mic icon in accent circle), then round 56 px glass
  buttons: Search, Input source, Settings, Profile.
- **Hero**: home time 220 px weight 200 (12h with AM/PM 52 px, or 24h), date line 36 px
  ("Thursday, October 29").
- **Weather card** (580 px wide glass card, radius 28): icon, current temp 96 px, condition,
  H/L, city; divider; 4-day forecast (day, icon, hi/lo). °F/°C setting.
- **World clocks**: 3 glass cards (radius 28, 152 px tall). Each: analog dial 104 px
  (hour/minute white hands, accent second hand), city, digital time 46 px, meta
  "Tomorrow · +9h 45m" relative to home zone (rounded to 15 min), sun/moon icon for 06–18 local.
  City + IANA time zone configurable for each.
- **Apps row**: "Your apps" label, tiles 172×104 radius 20 (Android TV banner ratio ~16:9.5),
  label under each, last tile "All apps" (grid icon).
- Glass style: `rgba(255,255,255,0.10)` fill, 1 px `rgba(255,255,255,0.16)` border, blur 28.
- Accent default `#8FB8FF` (options `#FFB27A`, `#7FE0C2`, `#F2A7D8`).

### 2. Screensaver (`mockups/Screensaver.dc.html`)
- Same background, dimmed (default 35% black, configurable).
- Flip clock: HH : MM (optional :SS). Cards 250×360 (200×290 with seconds), radius 26,
  top half `#23262E`, bottom `#1C1F26`, digits Roboto Mono 600 280 px `#EEF1F6`,
  4 px hinge line + side notches. Flip: old top half folds down (rotateX 0→-90°, 300 ms ease-in),
  then new bottom half drops (90°→0°, 300 ms ease-out).
- AM/PM 44 px beside clock (hidden in 24h). Date 40 px below.
- Pill bar: weather (icon, temp, city) | three world clocks (city + time).

### 3. Background settings (`mockups/Background.dc.html`)
- Source: Google Photos | Built-in scenes | Solid color.
- Google Photos: QR code + 3 steps ("Scan with phone → pick photos/album → Done"), status
  "Waiting for your selection…", grid of selected photos (tap to set now).
- Change photo: 5 min / 15 min / 1 hour / Daily / Never. Shuffle toggle.

## Implementation notes
- **Launcher registration**: activity with intent filter `MAIN` + `HOME` + `DEFAULT` (+ `LEANBACK_LAUNCHER`).
- **App list + real icons**: query `Intent(ACTION_MAIN).addCategory(CATEGORY_LEANBACK_LAUNCHER)`;
  use `activityInfo.loadBanner(pm)` falling back to `loadIcon(pm)`; launch with
  `pm.getLeanbackLaunchIntentForPackage(pkg)`. Listen for package add/remove broadcasts.
- **Clocks**: `java.time.ZonedDateTime` per zone; tick each second (minute for hero).
- **Weather**: pick a weather API (e.g. Open-Meteo, no key) by configured city/coords; cache 30 min.
- **Google Photos background**: Google Photos **Picker API** (Library API no longer allows reading a
  user's whole library). Create a picking session → show `pickerUri` as QR → poll session until
  `mediaItemsSet` → list + download picked items → store locally → rotate per interval.
  OAuth on TV via device-code flow (scope `photospicker.mediaitems.readonly`).
- **Gemini button**: fire the system assistant (`Intent.ACTION_ASSIST` or
  `RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE` / `SearchManager` voice); use a generic mic icon,
  not the Gemini logo.
- **Screensaver**: implement as a `DreamService` so it works with the system screensaver setting.
- **Settings persistence**: DataStore (home zone, 3 clocks, units, 24h, accent, background source,
  interval, shuffle).
