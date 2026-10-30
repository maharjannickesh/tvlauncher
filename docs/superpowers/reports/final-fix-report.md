# Final fix wave report

Status: DONE_WITH_CONCERNS. Pushed: origin/v1 = 22a5f51.

## Findings
1. Chips: ui/settings/BackgroundScreen.kt:170 `fillMaxSize()` -> `fillMaxHeight()` (+ import). Device: shots/task10-background.png shows 5 min / 15 min / 1 hour / Daily on one row, Never wrapping.
2. Weather:
   - data/weather/WeatherRepository.kt: `toUi` finds `homeDate` in `daily.time`, slices today + next 4 from there, null if missing; new `WeatherFreshness.isFresh(snapshot, city, unit, nowMs)` (30 min TTL + city/unit match).
   - ui/home/HomeViewModel.kt: `ok` = ui != null && isFresh; the loop sleeps with `withTimeoutOrNull(30 min | 90 s) { refresh.first() }`; new `refreshNow()`.
   - MainActivity.kt: `LifecycleEventObserver` calls `refreshNow()` on ON_START.
   - ScreensaverService.kt: re-reads the cache every 30 min and uses `LocalDate.now(ZoneId.of(homeZone))`.
   - OpenMeteo.kt: `forecast_days` 5 -> 7. Found on device: after midnight a 5-day forecast has only 3 days after the new today, so toUi returned null and the card showed "--". 7 days leaves slack.
   - Tests added: stale cache is not fresh (plus city/unit mismatch, null), toUi homeDate=day1 returns day-1 values and 4 following days, homeDate missing returns null.
3. Focus: ui/home/AppsRow.kt: per-package FocusRequesters, `focusedIndex` tracked via onFocusChanged (kept when the tile left the list, cleared when focus leaves a still-present tile), `LaunchedEffect(apps)` refocuses `min(idx, lastIndex)` only if the focused package vanished, initial focus on tile 0 only on first composition, `Modifier.focusRestorer()` on the Row. app/build.gradle.kts: `-Pprobe` adds `.probe` id suffix and a `src/probe/res` label "AAA Probe" (sorts first, so the probe lands in the 8-tile row). README dev loop documents the flag.
   - Device proof: shots/final-pre-uninstall.png (probe tile focused), shots/final-uninstall-focus.png (after `adb uninstall com.nickesh.tvlauncher.probe`: Hulu, the neighbour at the same index, is focused). A first attempt (before the onFocusChanged tweak) sent focus to the top bar; fixed and re-proved.
4. Start window: res/values/themes.xml + values-v31/themes.xml (Theme.TvLauncher, parent NoTitleBar.Fullscreen, windowBackground = drawable/window_background.xml Dusk gradient #16213F -> #4F4372 -> #E39470; v31 also sets splash background #16213F and the icon), drawable/ic_launcher.xml, manifest icon + theme on application and activity. Lint MissingApplicationIcon gone.
   - NOT proven on device: shots/final-startwindow.png is just the Welcome screen. Rapid screencap loops (raw, 6-8 frames) show the previous screen (Google TV home or Settings) until our first frame is drawn; no starting-window frame appeared at all, so neither black nor the gradient was observable. The theme is built and installed; the visual result is unverified.
5. FlipDigitsTest.justAfterMidnight_12h: 00:05, 12h -> ["12","05"], "AM".
6. `.DS_Store` untracked (git rm --cached; landed in the chips commit 3385f82).

## Verification
- `JAVA_HOME=... ./gradlew :app:testDebugUnitTest lintDebug` -> BUILD SUCCESSFUL, 51 tests (47 + 4 new), 0 failures; lint 0 errors, 20 warnings, 1 hint.
- Device: final-home.png (shots/) shows Home intact with fresh weather (Sat-Tue).

## Commits
3385f82 chips; 23e07e9 weather; d89d823 focus + probe; d568a35 start window + icon; 22a5f51 flip test + .DS_Store.

## Concerns
- Start window visual unverified (see 4).
- The weather card's day columns only roll over at the next refresh (up to 30 min after midnight), because the cache is not re-sliced on date change; the high/low can show the previous day until then. Not in the finding list.
- `pm clear com.nickesh.tvlauncher` was run on the TV to force a weather refetch (our own app's settings reset to defaults: 12 h clock). No other app was touched; the probe was uninstalled.
