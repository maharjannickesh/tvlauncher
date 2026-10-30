# TV Launcher v1.1 — Settings picker + Save, digital clocks with weather, All apps search

Date: 2026-10-30. Builds on v1 (`2026-10-29-tv-launcher-v1-design.md`) and the Home key work shipped today (`main` = `v1` = 791e027). Visual language stays v1's (glass cards, Outfit, design-pixel scale); no new mockups — the builder derives the two new layouts from the existing components.

## Intent

Nickesh's own list, in his words, with the decisions he made when asked:

1. "on setting; i would like to have something like clock 1 => sub menu appears on right side to enter city name and select time zone, which should be a list instead of clicking next next on remote" → type a city, pick a match from a list; the match fills the time zone (and the coordinates for weather); the zone stays overridable from a full list. The Home clock uses the same picker.
2. "save button at the end, so that i can do multiple changes and save at once" → Settings edits a draft; Save applies; Back with unsaved changes asks "Discard changes?".
3. "in home screen for the clock we dont need both digital and analog clock, we can just have digital clock; and thinking to have weather of that city next to it … the top right icon on clock regarding day and night should represent the weather" → each world-clock card: city, digital time, offset line, and that city's weather icon + temperature where the sun/moon was. The big weather card stays and follows the **home** city; the separate "Weather city" setting goes away.
4. "the current timezone picks is only limited to few; it should show all" → the full IANA zone list, grouped by region.
5. "the search and ask gemini button both does same thing, so search may be we can take user to all apps search" → the Search button opens All apps with a type-to-filter field.
6. "on top where is says Good afternoon, make it to Good Afternoon, Nickesh" → the top-bar greeting is title-cased and carries the name from Settings: "Good Morning, Nickesh" / "Good Afternoon, Nickesh" / "Good Evening, Nickesh" (and whatever other greeting `ClockMath.greeting` already produces, same casing rule), exactly "Good Afternoon, Nickesh" for the afternoon.
7. "lets add timer for screensaver as well in setting" → a Settings row that sets how long the TV sits idle before the screensaver starts (the TV's own `screen_off_timeout`, the value `tools/tv.sh idle-set` writes), chosen from a list, applied on Save.

## Goals

- G1 Settings → "Home", "Clock 1/2/3" rows open a right-side panel on the same screen: a City field (TV keyboard), a results list from the weather provider's geocoding (name, region, country, zone), a Time zone row that opens the full grouped zone list. Picking a result sets city label, zone, latitude/longitude on the draft.
- G2 Settings has a Save row at the end. Nothing persists until Save. Back with a dirty draft shows a two-button prompt: "Discard changes?" → *Discard* / *Keep editing*. Save then Back returns to Home with everything applied at once.
- G3 World-clock cards show digital time only (no dial), plus the weather icon and current temperature for that clock's city. The icon replaces the sun/moon; at night with clear sky it shows the moon.
- G4 The big weather card shows the home city's weather, located by the home city's coordinates from the picker (or geocoded from its name if no coordinates are stored yet, which keeps existing installs working).
- G5 The zone list contains every `ZoneId.getAvailableZoneIds()` entry under its region (Africa, America, Antarctica, Asia, Atlantic, Australia, Europe, Indian, Pacific, Etc/other), sorted, navigable with the remote; current value pre-focused.
- G6 Search (top bar) opens All apps with a search field focused; typing filters the grid by label, case-insensitive, as you type; empty query shows all; Back from the field clears the query first, then leaves.
- G8 Greeting reads "Good Afternoon, Nickesh" (title case, comma, name from Settings; an empty name drops the comma and name).
- G9 Settings row **Screensaver after** with a list of 1, 2, 5, 10, 15, 30, 60 minutes (current TV value pre-selected, shown as "10 min"; a value outside the list is shown as-is, e.g. "7 min"). Applied on Save by writing `Settings.System.SCREEN_OFF_TIMEOUT`. This needs the "Modify system settings" permission (`WRITE_SETTINGS`): if it is not granted, Save still saves everything else, the row shows "Needs permission ›", and pressing it opens the system page for our app (`Settings.ACTION_MANAGE_WRITE_SETTINGS`); `tools/tv.sh` gets `idle-perm` that grants it over adb (`appops set com.nickesh.tvlauncher WRITE_SETTINGS allow`) for the dev loop.
- G7 Weather for four cities (home + 3 clocks) is fetched and cached independently with the existing 30-minute TTL, degrades to cached/placeholder exactly as today, and never blocks the clocks from ticking.

## Non-goals

- Background screen changes: it keeps its immediate-apply behaviour (live preview) and is not part of the Settings draft. (Builder call, listed for veto.)
- Hourly forecasts, a forecast on the clock cards, or weather for the screensaver beyond what it shows today.
- Reordering clocks, adding a fourth clock, deleting clocks.
- Changing the hero clock or the top bar layout beyond the Search button's action.

## Anti-goals

- A Settings change that silently applies before Save, or a Save that applies only part of the draft.
- A clock card that shows a stale temperature as if current: when the fetch fails and the cache is older than the TTL, the card shows the icon dimmed and "--°", like the big card's placeholder.
- A zone list that needs 400 remote presses: region first, then zones within the region, with the current zone pre-focused.
- A geocoding result list that loses the user's typed text, or a picker that applies a result to the wrong clock.

## Constraints

- Open-Meteo geocoding (`GeoResult(name, latitude, longitude, timezone)` already exists; extend with `admin1` and `country` and request `count=8`); forecast by latitude/longitude with the city's zone. Cached per city key.
- Settings schema changes (DataStore Preferences): `WorldClock` gains `lat: Double?`, `lon: Double?`; `Settings` gains `homeLat: Double?`, `homeLon: Double?`; `weatherCity` is removed from the model (its stored key is ignored). Old installs: coordinates null → geocode by name on first load, then store nothing (the picker is the only writer of coordinates). The weather cache moves from one snapshot to one per city key.
- TV text entry uses the existing `TextSettingRow`/`BasicTextField` pattern (TV keyboard, Done commits).
- Lint 0 errors, tests green, same build/commit rules (`v1` branch, trailer). Device checks via `tools/tv.sh`; the accessibility service stays on.

## Design

### 1. Settings draft and Save

- `SettingsScreen` receives `saved: Settings` and owns `draft` (`rememberSaveable`-friendly: keep the draft in a `ViewModel`-free `remember` of a `Settings` copy; a config change on TV is rare and loses the draft — acceptable, builder call). Rows edit the draft. `dirty = draft != saved`.
- New last row **Save** (`SettingRow("Save", …)`, accent-tinted trailing check icon when dirty, dimmed when not). On click → `onSave(draft)` → persisted via `SettingsRepository.update { draft }` → Settings closes to Home.
- Back (and the Back round button): if `!dirty` → leave; else show `DiscardDialog` (glass card centred, title "Discard changes?", buttons *Keep editing* (focused by default) / *Discard*). Discard → leave without saving.
- The "Home button…" row and "Background…" row are navigation rows, not draft fields: Background opens its screen (immediate apply, as today); on return the draft keeps its pending edits.
- Pure, tested: `SettingsDraft.isDirty(saved, draft)` is just `!=`; the meaningful tests are on the clock editing helpers below.

### 2. City picker panel (right side)

- Rows "Home" (value: `homeCity · homeZone`) and "Clock 1..3" (value: `city · zoneId`) replace today's six text/zone rows. Clicking opens `CityPickerPanel` in the right half of the Settings screen (x from ~1000 design px, same vertical band as the list), with focus moved into it; Back closes the panel and returns focus to the row.
- Panel content, top to bottom: title ("Home" / "Clock 1"), **City** text field (amended after final review: opens EMPTY with the current city as placeholder text, so typing never appends to it; Done triggers a geocode search, Done on an empty field does nothing), **Results** list (up to 8 `GeoMatch` rows: `"<name>, <admin1>, <country>"` and a second line with the zone; focusing a row previews nothing; OK selects → draft gets `city = name`, `zoneId = timezone`, `lat/lon`), **Time zone** row showing the current zone (OK → `ZoneListPanel`), and a status line ("Searching…", "No matches", "Offline — try again").
- `ZoneListPanel`: replaces the picker content while open; a region column (10 entries) and, after choosing a region, the zones of that region (`ZoneId.getAvailableZoneIds()` filtered by prefix, sorted, Etc/* and single-word ids under "Other"); OK sets `zoneId` on the draft (coordinates untouched) and returns to the picker. The current zone's region and entry are pre-focused.
- Pure, tested: `ZoneCatalog.regions(): List<String>`, `ZoneCatalog.zones(region): List<String>`, `ZoneCatalog.regionOf(zoneId)`; `ClockEdit.applyMatch(clock, match)` and the home-city equivalent.
- Geocoding: `WeatherApi.search(query): List<GeoMatch>` (Open-Meteo `search?name=&count=8&language=en`), with `GeoMatch(name, admin1, country, latitude, longitude, timezone)`. Existing `geocode(city)` becomes `search(city).firstOrNull()` mapping.

### 3. Weather per city

- `WeatherRepository.load(place: Place, unit, zone)` where `Place(key: String, name: String, lat: Double?, lon: Double?)`; `key` = `"home"` / `"clock0..2"`. Coordinates null → `search(name).firstOrNull()` (cached geo per name as today). Cache: `DataStoreWeatherCache` keeps a map keyed by `place.key` (`weather_json_<key>`), freshness per entry (same `WeatherFreshness` rules + the midnight wake).
- `HomeViewModel`: one loop per place (home + each clock), same TTL/retry/`refreshNow` semantics, each writing into `weatherByKey: StateFlow<Map<String, WeatherUi?>>`. `ClockSnapshot` gains `weather: ClockWeather?` (`kind: WeatherKind`, `temp: String`, `isDay` from the clock, not from the API). The screensaver keeps reading the home entry.
- Big card unchanged visually; its data is the `"home"` entry; its title is `homeCity`.

### 4. Clock card (digital only)

- Remove `Dial`. Layout inside the same 152-px-high glass card: left column city (26), time (grows to 52, tnum) with AM/PM, meta line (20); right column top-aligned: weather icon 40 px (Sun/Partly/Cloud/Rain from the existing icon set; Moon when `!isDay && kind == SUN`), temperature text 26 under it ("64°"; "--°" dimmed when `weather == null`). The focus/scale/glow treatment is unchanged.

### 5. All apps search

- `AllAppsScreen(query, onQueryChange, …)`: a search field row under the title (glass pill 520 px wide: magnifier icon + `BasicTextField`, placeholder "Search apps"), the grid shows `apps.filter { label contains query ignoreCase }`. Opened from the Search button with `focusSearch = true` (field focused, keyboard up); opened from "All apps" tile with the first app focused as today. Back: query non-empty → clear and focus the field; else leave.
- `SystemAction.SEARCH` and its intents are removed; the top-bar Search button calls `onOpenAllAppsSearch`.
- Pure, tested: `AppFilter.filter(apps, query)` (case-insensitive, trims, empty → all, matches on label only).

### 6. Greeting

- `ClockMath.greeting(hour, name)` returns `"Good Morning, Nickesh"` etc.; title case on both words; `name` blank → `"Good Morning"`. The hour bands are unchanged. Pure, tested (each band, with and without a name).

### 7. Screensaver timer row

- Row "Screensaver after" between "Show seconds" and "Background…"; OK opens a right-side list panel (same panel slot as the city picker) with the seven values, current one focused; OK picks and closes. The value is part of the draft (`draft.screensaverAfterMinutes: Int?`, read from `Settings.System.getInt(SCREEN_OFF_TIMEOUT)` when Settings opens, null if unreadable) and part of the dirty check.
- Save: when it changed and `Settings.System.canWrite(context)` → `Settings.System.putInt(resolver, SCREEN_OFF_TIMEOUT, minutes * 60_000)` and log; when not grantable → everything else saves, a toast "Screensaver timer needs 'Modify system settings' — tap the row to grant" and the row's value becomes "Needs permission ›" whose OK opens `Settings.ACTION_MANAGE_WRITE_SETTINGS` with our package URI (fallback toast with the manual path if it does not resolve). The 30-minute standby delay (`sleep_timeout`) is left alone.
- Pure, tested: `IdleOptions.label(minutes)`, `IdleOptions.values`, `IdleOptions.indexFor(minutes)`.

### 8. Settings migration

- On first load after update: `weatherCity` key ignored; `homeLat/homeLon` null; clocks' `lat/lon` null; everything else as before. No data loss; weather geocodes by name until the user picks a result.

## Author's calls after the builder check (listed for veto)

1. **Draft lives above the screen switch.** `MainActivity` holds the draft (a `remember`ed holder at the root, or a tiny `SettingsViewModel`), so opening Background and coming back keeps pending edits. **Save writes only draft-owned fields** onto the *latest* saved settings (name, home city/zone/lat/lon, clocks, unit, 24-hour, accent, screensaver dim, show seconds); **dirty is computed over those fields only**, so Background's immediate writes and the scene shuffle timer never make the draft look dirty or get overwritten. Pure, tested: `SettingsDraft.merge(saved, draft)`, `SettingsDraft.isDirty(saved, draft)`.
2. **Typed city text is only a search query.** The draft's city label, zone and coordinates change only when a result is picked. A label the geocoder cannot find is not supported.
3. **Migration uses the legacy weather city.** When `homeLat/homeLon` are null and the old `weatherCity` key exists, it is the geocode query for the home entry (so existing installs keep the exact weather they had); the big card's title is still `homeCity`. Once the picker writes coordinates the legacy key is ignored.
4. **Cache validity on re-pick.** `WeatherSnapshot` stores name and lat/lon; an entry is fresh only if unit, TTL and location match (coordinates when present, else name). Clock cards use a separate current-only mapper (temperature + code), so a short forecast never blanks a clock card.
5. **Legacy single weather blob is dropped**; the screensaver reads the `"home"` entry and shows "--" until Home's first refresh after the update.
6. **All apps search focus**: opening from the Search button focuses the field and requests the keyboard; if this TV needs one OK press to raise it, that is acceptable. D-pad Down from the field lands on the first filtered app.
7. **Moon only for clear night**; partly/cloud/rain keep their icons at night; `isDay` stays the existing hour approximation.

## Left to the builder

- Exact panel geometry and the results-row styling, derived from `SettingRow`/`ValueText`.
- Whether the region column and the zone column are two lists side by side or two steps; both satisfy G5.
- Debounce vs. Done-only for the city search (Done-only is fine; the TV keyboard's suggestions make debounce noisy).
- How the Save row signals "nothing to save" (dim) vs. dirty (accent tick).

## Verification

- Unit: `ClockMath.greeting` bands with/without name; `IdleOptions`; `ZoneCatalog` (every available zone appears exactly once across regions; `regionOf`), `ClockEdit.applyMatch`, `AppFilter`, `WeatherRepository` per-key cache + freshness (adapt existing tests), geocode JSON mapping with `admin1`/`country`, `ClockMath.snapshot` carrying weather.
- Lint 0 errors; suite green.
- Device (screenshots `shots/v11-*.png`): Settings with the panel open for Clock 1; typing "Lisbon" → results → pick → row shows "Lisbon · Europe/Lisbon"; zone list for region Europe with the current zone focused; Save → Home shows Lisbon's time and weather on the card; Back with a dirty draft → prompt → Keep editing / Discard; "Screensaver after" → list → pick 5 min → Save → `settings get system screen_off_timeout` reads 300000 (then set back to 10); with the permission revoked (`appops set … WRITE_SETTINGS deny`) the row shows "Needs permission" and OK opens the system page; Search button → All apps with the field focused → typing "you" filters to YouTube apps; old-install path: fresh install with v1 settings still shows home weather.
