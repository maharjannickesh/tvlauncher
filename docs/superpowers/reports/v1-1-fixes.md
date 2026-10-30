# v1.1 fixes report

Branch `v1`, device 10.0.0.21 (Chromecast with Google TV). Spec: `docs/superpowers/specs/2026-10-30-v1-1-fixes-design.md`. 124 unit tests, lint 0 errors.

## What shipped

- Settings is a draft with a Save row at the end; Back with unsaved changes asks "Discard changes?" (Keep editing / Discard). Background still applies immediately.
- Home and Clock 1/2/3 open a right-side city picker: type, Done, up to 8 geocoded matches (name, region, country, zone); picking sets city, zone and coordinates. Typed text alone changes nothing.
- Time zone row in the picker: full zone list in two steps (region, then zone).
- Screensaver after (1/2/5/10/15/30/60 min) writes `screen_off_timeout` on Save; `tools/tv.sh idle-perm` / `idle-perm-deny` grant or revoke the needed "Modify system settings" permission.
- Home: three digital world-clock cards with per-city weather icon and temperature (moon at clear night, dimmed "--°" while loading/failed/re-picked); big weather card follows the home city; "Weather city" setting removed.
- Greeting in title case with the name ("Good Afternoon, Nickesh").
- Search button opens All apps with a search field (filters as you type); the All apps tile still opens with the first app focused.
- Weather: four independent fetches, 30-minute cache per city, location-aware freshness.

## Final device pass (shots in `shots/`)

1. Home after install: `v11-final-home` (greeting with name, Kathmandu 60 / Abu Dhabi 86 / Tokyo 55, big card Columbus). Pass. Process was restarted by the install; Welcome was already past by the first usable shot (`v11-final-welcome` caught nothing useful), so Welcome itself was not observed.
2. Settings: `v11-final-settings`; Clock 2 picker `v11-final-picker`; typed Paris, `v11-final-matches` (Paris, Ile-de-France, France first); picked, `v11-final-picked`.
3. Screensaver after list `v11-final-timer15`, set 15 min, Save focus `v11-final-save-focus`, saved `v11-final-saved`: Home shows Paris on clock 2 (19:22, 55). `settings get system screen_off_timeout` = 900000. Pass.
4. Restore: typed Abu, `v11-final-restore-matches`, picked Abu Dhabi (Asia/Dubai), `v11-final-restore-picked`, timer 10 min `v11-final-restore-timer`, Save, `v11-final-restored`: Abu Dhabi back, timeout 600000. Pass.
5. Search: `v11-final-search-net` (only Netflix, keyboard up). Back dismissed the keyboard, Down, OK launched Netflix; the screenshot of Netflix came back empty (not kept), so the launch is inferred, not seen. `tools/tv.sh remote short` returned Home: `v11-final-back-home`. Pass.
6. `tools/tv.sh homekey-status`: enabled, bound yes. Timer 600000.

Quirk seen: the city field keeps its current text when edited again ("Abu DhabiParis" on a first try), so clear it with Delete before typing.

## Keyboard on open

Search raises the TV keyboard automatically when All apps opens; no extra OK needed.

## Deviations from the spec

- Picker status line ("No matches" etc.) sits under the City row rather than at the bottom of the panel.
- Timer permission: this Google TV has no system page for "Modify system settings", so it is granted over adb (`idle-perm`); the row shows "Needs permission ›" and a hint toast otherwise.
- Query clearing takes two Backs with the keyboard up: the first is consumed by the IME, the second clears the query, a third leaves.
- Vanishing-tile focus hand-off in the All apps grid was not device-verified (no safe way to change the app list).
