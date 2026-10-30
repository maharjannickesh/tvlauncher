# Task 6 report

Commit 2a1b520 on `v1`: feat: real leanback apps row with banners, All apps grid, welcome readiness gate.

## Implemented
- `domain/apps/AppOrdering.kt` (InstalledApp, AppOrdering.order: installed favourites in order, rest label-sorted case-insensitively, take(max)).
- `data/apps/AppsRepository.kt`: TONES, apps/ready StateFlows, IO-scope load (leanback query, own package excluded, banner 172x104*scale, icon 64*scale, label-sorted, tone index%8), launch() (leanback intent else launcher intent, NEW_TASK, catches ActivityNotFoundException/SecurityException), refresh() cancels an in-flight load; `ready` flips after first load even if empty or failed.
- `TvLauncherApp`: package ADDED/REMOVED/CHANGED/REPLACED receiver (scheme "package", RECEIVER_NOT_EXPORTED) -> `container.apps.refresh()`. `AppContainer.apps`.
- `HomeViewModel`: placeholderApps removed; `homeRow()` = AppOrdering(8) mapped back to tiles + synthetic All apps tile; combines settings, apps, ticker. placeholderWeather kept.
- `AppsRow`: shared public `AppTileButton` (banner / icon / letter fallback / Grid icon for ""), tiles wrapped in `key(packageName)`.
- `AllAppsScreen`: Background(dim 0.5, scrim false), Back RoundButton + "All apps" 48 spx W400, 6-col LazyVerticalGrid of AppTileButton (keyed), BackHandler, initial focus on first item. Signature adds `settings` and `accent` params to the brief's.
- `MainActivity`: gate = `withTimeoutOrNull(4000) { apps.ready.first{it}; settings.settings.first() }` then delay to 1200 ms minimum; homeState collected only inside Home and AllApps branches (ruling 1); launch failure -> Toast "Can't open <label>".

## TDD
RED: `./gradlew :app:testDebugUnitTest --tests '*AppOrderingTest*'` -> compileDebugUnitTestKotlin FAILED (AppOrdering/InstalledApp unresolved).
GREEN: same command -> BUILD SUCCESSFUL, XML: tests="6" skipped="0" failures="0". Full `:app:testDebugUnitTest` green before commit.

## Device (10.0.0.21:38507)
- shots/task6-home.png: real banners (Google Play Store banner, Settings), "All apps" Grid tile last. This TV has only two leanback apps besides ours, so the row is Play Store, Settings, All apps.
- OK on first tile -> topResumedActivity = com.android.vending/...TvMainActivity. Back -> com.nickesh.tvlauncher/.MainActivity.
- shots/task6-allapps.png: header + Back button, 2 real-banner tiles, first focused. Back from All apps -> shots/task6-back.png shows Home.
- Cold start (force-stop, am start): shots/task6-welcome.png shows "Welcome Nickesh!!!" (saved name); shots/task6-cold-home.png / top activity shows Home after ~1.2 s past first frame.

## Concerns
- Debug cold start to first frame is ~3.6 s on this armv7 TV ("Skipped 140 frames"). Same on the baseline commit (verified via stash), so not a Task 6 regression; likely debug Compose without a baseline profile. Worth a release/baseline-profile look later.
- Commit trailer uses the global-constraints Fable 5.1 line.
- Dismissing focus on a removed app and the receiver path are by code read only (no uninstalls, per brief).

## Fix report: package visibility
Cause: Android 11+ filters queryIntentActivities without a manifest `<queries>`; the app saw 2 of 17 leanback apps (my earlier "sparse TV" concern was wrong).
Change: `AndroidManifest.xml` gets `<queries>` with MAIN+LEANBACK_LAUNCHER and MAIN+LAUNCHER intents (no QUERY_ALL_PACKAGES).
Verification: unit task green; rebuilt, installed, cold-started. shots/task6-home.png: YouTube, Netflix, Prime Video, Disney+, Stream Everything, Play Store (favourites in order), then Hulu, Instagram (alphabetical fill), All apps; all real banners. shots/task6-allapps.png: 17 tiles in a 6-column grid (3 rows), label-sorted, first focused, real banners. Commit: see git log (fix: declare package queries ...).
Note: an earlier pass of my screenshots mis-timed (Welcome/Instagram captured) because debug cold start is ~3.6 s; retaken with longer waits.

## Fix report 2: refresh race
Changes in `AppsRepository`: `refresh(debounceMs = 300)` bumps an AtomicInteger generation, cancels the previous job, delays 300 ms; loads run under a Mutex; the generation is re-checked after acquiring the lock and again (with `ensureActive()`) after `load()` before publishing, so a stale load can never overwrite a newer one; the init load uses debounce 0. Icon decode skipped when a banner exists. Banner decoded via `toCroppedBitmap` (cover-scale + centre-crop, no stretch).
Extra fix found during verification: first Home screenshot showed focus on "All apps" (row initially had only that tile, which held the first focusRequester). `AppsRow` focus effect is now keyed on the first tile's package name so focus lands on the first real tile once apps load.
Verification: `./gradlew :app:testDebugUnitTest --rerun-tasks` BUILD SUCCESSFUL; installed, cold start; shots/task6-fix-home.png shows YouTube (focused), Netflix, Prime Video, Disney+, Stream Everything, Play Store, Hulu, Instagram, All apps with real banners. The race itself is not unit-tested (Android APIs); verified by code read.
