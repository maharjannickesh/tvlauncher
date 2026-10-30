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

## Deferred minors and rulings (from the SDD ledger, 2026-10-30)

- Scan clean. Rubric check: ClockEditTest.typedQueryIsNotApplied asserts `before == before.copy()` — a near-tautology; Ruling: keep it as a documentation pin but the reviewer may flag it; if flagged, drop it (Review Focus 1 is also pinned on the device step). Costs nothing.
- Task 1: minor (deferred): merge/isDirty tests do not cover coordinates/clocks; SettingsScreen clock copy keeps stale lat/lon (Task 6 replaces those rows); interim weather query "Columbus" until Task 3.
- Task 2: Ruling: plan-mandated tautological test `typedQueryIsNotApplied` — finding stands (pre-flight already anticipated it); replace with a reflection pin that ClockEdit's public API is exactly applyMatch/applyHomeMatch/withZone. Costs nothing.
- Task 2: minor (deferred): IdleOptions edge tests (0/negative/60); GeoMatch.timezone non-null could fail a whole search decode; query not trimmed (Open-Meteo needs ≥ 2 chars — Task 6 guards); FakeApi low fidelity; `Other` region ~100 entries (UX note for Task 6). Zone pins for America/Argentina/*, US/Eastern, Etc/UTC folded into the fix round.
- Task 2: fix round 1/5 (1 addressed, 0 open; commits 460d050..4671cbe) — re-review clean. 112 tests. minor (deferred): test name `typedQueryIsNotApplied` no longer describes the API-surface assertion.
- Task 3: minor (deferred): samePlace mixed-mode name fallback (same-named city w/o coordinates); clock loops populate unread weatherByKey[clockN]; `failed` log appends e.message; screensaver blank until first home write after upgrade.
- Task 4: minor (deferred): kind→icon mapping duplicated with WeatherCard's icon(); long city/meta text has no ellipsis and could run under the right column; ClockSnapshot dial angles (hourDeg/minuteDeg/secondDeg) now unused — remove in a cleanup; per-second ticker could slow to per-minute now that no second hand exists (hero clock still shows seconds? check before changing).
- Task 5: implementer DONE_WITH_CONCERNS at 6ee1a68 — this Google TV has no "Modify system settings" page (ACTION_MANAGE_WRITE_SETTINGS resolves to nothing). Ruling: keep the attempt, change the fallback toast copy to the honest path: "Grant 'Modify system settings' over adb: tools/tv.sh idle-perm" (spec §7 copy amended by this ruling); README says the same. Costs: one string. Draft is replaced on each Settings open instead of cleared on leave (equivalent; avoids a blank fade) — accepted.
- Task 5: minor (deferred): toast blames permission for any write failure; permission state refreshes only on resume; focus tracking duplicated for text rows; ListPanel consumes Left/Right (Task 6 must use a two-step list, not side-by-side panels).
- Task 5: fix round 1/5 (4 addressed, 0 open; commits 6ee1a68..88ea7c2) — re-review clean. minor (deferred): Save double-press can run two idempotent saves; ListPanel focus retry can pull focus back during the first ~0.7 s if the user presses DPAD before the start item is focused; `update` exception is uncaught (pre-existing).
- Task 6: minor (deferred): re-select-all can swallow characters typed within ~150 ms of opening the field (guard: only re-select if text unchanged); focus race between Time-zone row and first-result after returning from the zone list; Home/Clock row values and the query row have no maxLines/ellipsis; result zone line no ellipsis; status line placed between City and results (accepted deviation from spec §2 order); redundant take(8).
- Task 7: review Approved-with-Important #1 (grid lacks focusRestorer; focus lost if the focused tile is filtered out / apps list changes) → fix round 1 (one-line). minor (deferred): initial-focus effect keyed on apps.isNotEmpty() can pull focus to tile 0 if apps go empty→non-empty while typing; import order; redundant cast in MainActivity; two Backs needed to clear the query while the IME is up (system behaviour, documented).
- Task 7: fix round 1/5 (1 addressed, 0 open; commits 8e14e82..8cdb473) — re-review clean. minor (deferred): stale focusedPkg after a failed/skipped hand-off could later pull focus from the search field (set focusedPkg = null after the attempt; AppsRow shares the weakness); off-screen neighbour target silently no-ops.
- Task 8: minor (deferred): Save-time toast still says "tap the row to grant" though this TV has no grant page — reword to the adb hint; README could name which action shows the hint toast.
- Final review (opus, 791e027..f885fe6): With fixes. Important: (1) City field re-edit appends — Ruling: spec §2 "prefilled with the current city" amended → the field starts EMPTY with the current city as placeholder (author's call 2 already says typed text is only a query); delete the timing hack. (2) geocoder zone ids unvalidated → persisted → ZoneId.of crash loop; fix = nullable timezone + filter to available zones + runCatching fallbacks in readers. (3) Save-time toast copy. Minors folded in: samePlace mixed-mode → false; screensaver applies samePlace+unit; focusedPkg = null after hand-off; "Never" timeout label; empty Done → status null; rename typedQueryIsNotApplied. One fix wave + one scoped re-review.
- Final fix wave: 8/8 addressed (commits f885fe6..56ae00c), re-review clean. minor (deferred): ZoneIds.safe logs on every call (once-per-id would be quieter); a previously persisted bad zone is not scrubbed, only degraded to UTC with a warning.

## Owed

- Device walk of the empty-City-field fix (edit Clock 2 twice in a row: Paris, then Abu Dhabi) — not run because the TV was in use (Netflix) when the fix landed. The fixed build is installed.
