# Task 9 report: Screensaver (DreamService + flip clock)

Commit b367dbe. Status: DONE_WITH_CONCERNS (minor).

## Implemented
- ui/screensaver/FlipClock.kt (FlipDigits, FlipDigitsCalc, FlipClock), FlipDigit.kt (split out, own file), ScreensaverContent.kt (pill bar, burn-in via pure `burnInShift(now)`, 60 s corner cycle), ScreensaverService.kt (DreamService with own Lifecycle/SavedState/ViewModelStore owners, set on the ComposeView and the decor view; settings via collectAsState, weather from weatherCache.read() once, per-second ticker aligned to the second), res/xml/screensaver.xml, manifest service, AppContainer.weatherCache exposed, tools/tv.sh dream-set/dream/dream-stop.
## TDD
- RED: with FlipClock.kt moved away, FlipDigitsTest failed to compile (Unresolved reference FlipDigits/FlipDigitsCalc). Note: I had first written the impl before the run, then removed it to record a genuine RED. Tests use org.junit (kotlin.test unavailable).
- GREEN: four tests pass; full `assembleDebug testDebugUnitTest` green.
## Device (TV 10.0.0.21:41935)
- `cmd dreams` does NOT exist on this API 34 build ("Unknown command: start"), and Somnambulator is absent. tv.sh `dream`/`dream-stop` fall back to `service call dreams 1` (dream()) / `2` (awaken()). `dumpsys dreams` showed our component as mCurrentDream.
- `settings get secure screensaver_components` -> com.nickesh.tvlauncher/.ScreensaverService.
- shots/task9-dream.png: flip clock over Dusk scene, dimmed, pill bar (weather 60° Columbus, OH + 3 clocks), PM label, date; burn-in offset visible between shots (shift moved the layout).
- Mid-flip: shots/task9-flipfast5.png caught the new bottom half unfolding (6 -> 7). tv.sh shot is ~1 s per call, so used raw adb exec-out in a background loop.
- First render bug found and fixed on device: digit halves were centred in a smaller parent (bottom digits invisible); fixed with wrapContentSize(unbounded).
- dream-stop worked (no current dream). No tvlauncher crashes in logcat.
## Concerns
- The brief's `cmd dreams start` is unavailable on this device; script uses service-call fallback.
- Tan patch between digits 3/4 is the Dusk sun orb showing through the gap, not an artifact.
- Card shadow is Compose `shadow(24.dp)` approximation. No AM/PM-hidden-in-24h or seconds-size visual device check (logic covered by tests/code only).
