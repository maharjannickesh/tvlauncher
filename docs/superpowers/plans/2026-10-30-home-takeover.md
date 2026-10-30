# Home-Button Takeover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** TV Launcher becomes the Chromecast's home activity (Home key, boot, standby wake with a 10-minute Welcome replay), the stock Google TV launcher is disabled over adb with a one-command restore, and the switch is proven on the device.

**Architecture:** Two pure decision units with JVM tests (`WelcomeReplay` for the standby gap, `HomeKey` for what the HOME intent does per screen) feed the existing single-activity Compose navigation in `MainActivity`. A `StandbyTracker` (screen-off/on broadcast receiver on the `Application`) supplies the timestamps. Device-side switching lives entirely in `tools/tv.sh`; the app only gains a HOME intent filter, a consumed Back on Home, and one Settings row that opens the stock launcher's app-details page.

**Tech Stack:** Kotlin 2.2 + Compose for TV (as v1), JUnit 4, adb via `tools/tv.sh`; JDK 21 for Gradle.

**Spec:** `docs/superpowers/specs/2026-10-30-home-takeover-design.md` (v1 context: `2026-10-29-tv-launcher-v1-design.md`).

## Global Constraints

- Branch `v1`, package `com.nickesh.tvlauncher`, Android project at repo root. Commit after every task; trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Build/test: `export JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/jdk-21.0.12.1+1/Contents/Home` then `./gradlew :app:testDebugUnitTest lintDebug`. 52 tests green and lint 0 errors at start; both must hold (plus new tests) at every commit.
- Device: Chromecast 10.0.0.21, `tools/tv.sh connect` (port changes on wake; currently 38949). Stay awake is on. **Never `pm clear`, disable, or uninstall anything except `com.google.android.apps.tv.launcherx`; never touch Wi-Fi over adb.**
- Stock home component: `com.google.android.apps.tv.launcherx/.home.HomeActivity`. Ours: `com.nickesh.tvlauncher/.MainActivity`.
- Welcome replay threshold: 10 minutes (`600_000` ms), a constant; debug builds may override it with the Gradle property `-PwelcomeReplayMs=<ms>`.
- Only the screen-off interval counts as asleep; dream time does not. Replay fires on the first arrival at our Home after a qualifying standby, whichever app was in front at wake. Every cold start shows Welcome (unchanged from v1).
- Home on Welcome or Home: no state change. Home on All apps / Settings / Background: `Screen.Home` via the existing 400 ms cross-fade. Back on Home: consumed.
- Stop rule on device: black screen, crash, or dead Home key → `tools/tv.sh home-restore` immediately and stop. Other lost surfaces are reported, not fixed.

## Review Focus

1. Standby that spans a dream: screensaver runs 30 min with the screen on, then the TV sleeps 12 h, then wakes into our Home. Expected: Welcome replays once (gap measured from screen-off). → `StandbyTrackerTest.dreamTimeDoesNotCount` in Task 1.
2. Two short standbys in a row, each under 10 min, with no Home arrival between them. Expected: no replay, and the second cycle does not inherit the first screen-off time. → `StandbyTrackerTest.offAgainWithoutHomeReArms` in Task 1.
3. Repeated HOME intents while Welcome is still running its readiness gate (user mashing Home at boot). Expected: Welcome finishes normally, no restart of the gate, no cross-fade flicker. → `HomeKeyTest.welcomeIgnoresHome` in Task 2 plus device step 1 in Task 6.
4. `onNewIntent` delivered with the HOME intent but no `CATEGORY_HOME` (the `set-home-activity` launch path or a relaunch from the stock apps list). Expected: treated exactly as HOME. → Task 3 routes every `onNewIntent` through `HomeKey`, no category check; device step 3 in Task 6.
5. Stock launcher package missing or hidden from package visibility (future factory reset, or `<queries>` omitted). Expected: the Settings row is simply absent, no crash. → Task 4 wraps the lookup in `runCatching` and the manifest gets the `<queries><package>` entry; device check in Task 4 step 6.

---

### Task 1: Standby decision and tracker

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/domain/home/WelcomeReplay.kt`
- Create: `app/src/main/java/com/nickesh/tvlauncher/data/system/StandbyTracker.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/domain/home/WelcomeReplayTest.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/data/system/StandbyTrackerTest.kt`

**Interfaces:**
- Produces:
  - `object WelcomeReplay { const val THRESHOLD_MS = 600_000L; fun shouldReplay(screenOffAt: Long?, screenOnAt: Long?, thresholdMs: Long = THRESHOLD_MS): Boolean }`
  - `class StandbyTracker(private val clock: () -> Long = SystemClock::elapsedRealtime) { fun onScreenOff(); fun onScreenOn(); fun takeReplay(thresholdMs: Long = WelcomeReplay.THRESHOLD_MS): Boolean; fun register(context: Context) }` — `takeReplay` returns the decision for the most recent off→on cycle and clears both timestamps (one answer per wake). `register` listens for `Intent.ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON` via `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` and logs `Log.i("Standby", ...)` on each.

- [ ] **Step 1: Write the failing tests**

```kotlin
class WelcomeReplayTest {
    private val min = 60_000L
    @Test fun gapAtOrOverThreshold_replays() {
        assertTrue(WelcomeReplay.shouldReplay(1_000L, 1_000L + 10 * min))
        assertTrue(WelcomeReplay.shouldReplay(1_000L, 1_000L + 11 * min))
    }
    @Test fun shortGap_doesNotReplay() { assertFalse(WelcomeReplay.shouldReplay(1_000L, 1_000L + 9 * min + 59_000)) }
    @Test fun missingTimestamps_doNotReplay() {
        assertFalse(WelcomeReplay.shouldReplay(null, 5L)); assertFalse(WelcomeReplay.shouldReplay(5L, null)); assertFalse(WelcomeReplay.shouldReplay(null, null))
    }
    @Test fun onBeforeOff_doesNotReplay() { assertFalse(WelcomeReplay.shouldReplay(20 * min, 5 * min)) }
    @Test fun customThreshold() { assertTrue(WelcomeReplay.shouldReplay(0L, 30_000L, thresholdMs = 30_000L)) }
}

class StandbyTrackerTest {
    private var now = 0L
    private val tracker = StandbyTracker { now }
    private val min = 60_000L
    @Test fun longStandby_replaysOnce() {
        tracker.onScreenOff(); now += 11 * min; tracker.onScreenOn()
        assertTrue(tracker.takeReplay()); assertFalse(tracker.takeReplay())
    }
    @Test fun shortStandby_noReplay_andClears() {
        tracker.onScreenOff(); now += 30_000; tracker.onScreenOn()
        assertFalse(tracker.takeReplay()); now += 60 * min; assertFalse(tracker.takeReplay())
    }
    @Test fun coldProcess_noReplay() { assertFalse(tracker.takeReplay()) }
    @Test fun dreamTimeDoesNotCount() {
        // Dream ran 30 min with the screen on; only the off interval (12 h) counts and it qualifies.
        now += 30 * min; tracker.onScreenOff(); now += 12 * 60 * min; tracker.onScreenOn()
        assertTrue(tracker.takeReplay())
    }
    @Test fun offAgainWithoutHomeReArms() {
        tracker.onScreenOff(); now += 5 * min; tracker.onScreenOn()
        now += 1 * min; tracker.onScreenOff(); now += 5 * min; tracker.onScreenOn()
        assertFalse(tracker.takeReplay()) // second cycle measured from its own screen-off, not the first
    }
    @Test fun stillAsleep_noReplay() { tracker.onScreenOff(); now += 20 * min; assertFalse(tracker.takeReplay()) }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :app:testDebugUnitTest --tests '*WelcomeReplayTest*' --tests '*StandbyTrackerTest*'`; expected: compilation error, unresolved `WelcomeReplay` / `StandbyTracker`.

- [ ] **Step 3: Implement both classes** as declared in Interfaces. `onScreenOff` sets `screenOffAt = clock()` and clears `screenOnAt`; `onScreenOn` sets `screenOnAt = clock()`; `takeReplay` computes `WelcomeReplay.shouldReplay(screenOffAt, screenOnAt, thresholdMs)`, then nulls both, then returns the result. `register` needs no unit test (Android `Context`).

- [ ] **Step 4: Run the full suite** — `./gradlew :app:testDebugUnitTest lintDebug`; expected: 63 tests, 0 failures, lint 0 errors.

- [ ] **Step 5: Commit** — `git commit -m "feat: standby tracker and Welcome replay decision"`.

---

### Task 2: `Screen` file and HOME-key routing

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/Screen.kt` (move `sealed interface Screen` and `ScreenSaver` out of `MainActivity.kt`, `ScreenSaver` made `internal`)
- Create: `app/src/main/java/com/nickesh/tvlauncher/domain/home/HomeKey.kt`
- Modify: `app/src/main/java/com/nickesh/tvlauncher/MainActivity.kt` (remove the moved declarations)
- Test: `app/src/test/java/com/nickesh/tvlauncher/domain/home/HomeKeyTest.kt`

**Interfaces:**
- Produces: `object HomeKey { /** Screen to show when the HOME intent arrives, or null for no change. */ fun next(current: Screen): Screen? }`

- [ ] **Step 1: Write the failing test**

```kotlin
class HomeKeyTest {
    @Test fun homeOnHome_isNoop() { assertNull(HomeKey.next(Screen.Home)) }
    @Test fun welcomeIgnoresHome() { assertNull(HomeKey.next(Screen.Welcome)) }
    @Test fun subScreensReturnHome() {
        assertEquals(Screen.Home, HomeKey.next(Screen.AllApps))
        assertEquals(Screen.Home, HomeKey.next(Screen.Settings))
        assertEquals(Screen.Home, HomeKey.next(Screen.Background))
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :app:testDebugUnitTest --tests '*HomeKeyTest*'`; expected: unresolved `HomeKey`.
- [ ] **Step 3: Move `Screen` + `ScreenSaver` to `Screen.kt`; implement `HomeKey.next`.**
- [ ] **Step 4: Run the full suite** — expected 66 tests, 0 failures, lint 0 errors; `assembleDebug` compiles.
- [ ] **Step 5: Commit** — `git commit -m "feat: HomeKey routing; Screen in its own file"`.

---

### Task 3: Manifest, HOME intent handling, Back on Home, Welcome replay

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (activity block lines 35–45; `<queries>` block)
- Modify: `app/build.gradle.kts` (`buildFeatures`, `buildTypes.debug`, `defaultConfig`)
- Modify: `app/src/main/java/com/nickesh/tvlauncher/AppContainer.kt`
- Modify: `app/src/main/java/com/nickesh/tvlauncher/TvLauncherApp.kt`
- Modify: `app/src/main/java/com/nickesh/tvlauncher/MainActivity.kt`

**Interfaces:**
- Consumes: `HomeKey.next`, `StandbyTracker`, `WelcomeReplay.THRESHOLD_MS` (Tasks 1–2).
- Produces: `AppContainer.standby: StandbyTracker`; `BuildConfig.WELCOME_REPLAY_MS: Long`.

- [ ] **Step 1: Manifest.** Add to `MainActivity` a second `<intent-filter>` with `MAIN` + `category.HOME` + `category.DEFAULT` (keep the `LEANBACK_LAUNCHER` filter). Add attributes `android:launchMode="singleTask"` and `android:excludeFromRecents="true"`. Do **not** add `stateNotNeeded` (it suppresses `savedInstanceState`, which `rememberSaveable` relies on; builder call refining the spec's "standard set"). Add `<package android:name="com.google.android.apps.tv.launcherx" />` inside `<queries>` (Task 4 needs the visibility).
- [ ] **Step 2: Gradle.** `buildFeatures { compose = true; buildConfig = true }`. In `defaultConfig`: `buildConfigField("long", "WELCOME_REPLAY_MS", "600000L")`. In `buildTypes.debug`: `(project.findProperty("welcomeReplayMs") as String?)?.let { buildConfigField("long", "WELCOME_REPLAY_MS", "${it}L") }`.
- [ ] **Step 3: Wiring.** `AppContainer`: `val standby = StandbyTracker()`. `TvLauncherApp.onCreate`: `container.standby.register(this)` after the package receiver.
- [ ] **Step 4: MainActivity.**
  - Field `private val homePresses = mutableStateOf(0)`; `override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); Log.i("Home", "onNewIntent ${intent.action} ${intent.categories}"); homePresses.value++ }` (no category filtering, Review Focus 4).
  - In `setContent`, after `screen` is declared: `LaunchedEffect(homePresses.value) { if (homePresses.value > 0) HomeKey.next(screen)?.let { screen = it } }`.
  - Extend the existing `LifecycleEventObserver` `ON_START` branch: `if (container.standby.takeReplay(BuildConfig.WELCOME_REPLAY_MS)) { Log.i("Home", "welcome replay after standby"); welcomeDone = false; screen = Screen.Welcome }`. Note `welcomeDone`/`screen` are Compose state declared in the same `setContent` scope; keep the observer inside it (it already is).
  - Welcome branch: the `LaunchedEffect(Unit)` gate is keyed to composition, so a replay (a fresh `Screen.Welcome` composition) re-runs it; confirm no code change is needed.
  - Home branch (`else ->`): add `BackHandler { }` before `HomeScreen(...)`.
- [ ] **Step 5: Build, test, lint** — `./gradlew :app:testDebugUnitTest lintDebug assembleDebug`; expected: 66 tests green, lint 0 errors.
- [ ] **Step 6: Device check (no takeover yet).** `tools/tv.sh connect && tools/tv.sh install`. Then:
  - Back on Home: `adb shell input keyevent KEYCODE_BACK` → `tools/tv.sh shot takeover-back-on-home` shows Home (not the stock launcher).
  - HOME intent while on Settings: open Settings on the TV (navigate with `input keyevent` DPAD to the gear or use the Settings glass button), then `adb shell am start -n com.nickesh.tvlauncher/.MainActivity -a android.intent.action.MAIN -c android.intent.category.HOME` → shot `takeover-home-from-settings` shows Home, logcat shows the `onNewIntent` line. Repeat on Home → shot unchanged, no Welcome.
  - Replay: build with `-PwelcomeReplayMs=20000`, install, then `adb shell input keyevent KEYCODE_SLEEP`, wait 30 s, `KEYCODE_WAKEUP`, wait 3 s → shot `takeover-replay` shows Welcome; logcat has "welcome replay after standby". Then sleep/wake with only 5 s between → shot shows Home, no Welcome. Reinstall the default-threshold build afterwards.
  - Welcome-mash (Review Focus 3): `am force-stop` ours, start it, fire the `am start ... -c HOME` command 5 times within 2 s → Welcome completes once into Home.
- [ ] **Step 7: Commit** — `git commit -m "feat: HOME intent filter, Home-key routing, Back on Home, Welcome replay after standby"`.

---

### Task 4: Settings row "Google TV Home…"

**Files:**
- Modify: `app/src/main/java/com/nickesh/tvlauncher/data/system/SystemIntents.kt`
- Modify: `app/src/main/java/com/nickesh/tvlauncher/ui/settings/SettingsScreen.kt` (after the `Background…` row)
- Modify: `app/src/main/java/com/nickesh/tvlauncher/MainActivity.kt` (Settings branch)

**Interfaces:**
- Produces in `SystemIntents`: `const val STOCK_HOME_PACKAGE = "com.google.android.apps.tv.launcherx"`; `fun stockHomeInstalled(): Boolean` (`runCatching { packageManager.getApplicationInfo(STOCK_HOME_PACKAGE, 0) }.isSuccess`, true for disabled packages too); `fun openStockHomeDetails(): Boolean` (starts `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with `Uri.parse("package:$STOCK_HOME_PACKAGE")` + `FLAG_ACTIVITY_NEW_TASK`, false on `ActivityNotFoundException`).
- `SettingsScreen` gains parameters `stockHomeInstalled: Boolean = false, onOpenStockHome: () -> Unit = {}`.

- [ ] **Step 1: SystemIntents additions** as above; add `<intent><action android:name="android.settings.APPLICATION_DETAILS_SETTINGS" /></intent>` to the manifest `<queries>`.
- [ ] **Step 2: SettingsScreen row.** When `stockHomeInstalled`: `SettingRow("Google TV Home…", onClick = onOpenStockHome) { ValueText("›") }` followed by a one-line helper `Text` in the same style as existing secondary text (colour `Color(0x99FFFFFF)`, Outfit W300, 18 design px): exact copy `Re-enable it there. To go back fully, also uninstall TV Launcher under TV Settings → Apps.`
- [ ] **Step 3: MainActivity Settings branch:** pass `stockHomeInstalled = remember { container.systemIntents.stockHomeInstalled() }` and `onOpenStockHome = { if (!container.systemIntents.openStockHomeDetails()) Toast.makeText(this@MainActivity, "Open TV Settings → Apps → See all apps → Google TV Home", Toast.LENGTH_LONG).show() }`.
- [ ] **Step 4: Build, test, lint** — expected 66 tests, lint 0 errors.
- [ ] **Step 5: Device check.** Install; open Settings; DPAD down to the last row → `tools/tv.sh shot takeover-settings-row`; press OK → shot `takeover-stock-home-details` shows the system app-details page for Google TV Home with Enable/Disable visible. Back returns to our Settings.
- [ ] **Step 6: Visibility check (Review Focus 5).** Temporarily remove the `<queries><package>` line, install, confirm the row is absent and nothing crashes; restore the line and reinstall. (Just a build + screenshot; do not commit the removal.)
- [ ] **Step 7: Commit** — `git commit -m "feat: Settings row opening the stock launcher's app details"`.

---

### Task 5: `tools/tv.sh` switch-over commands and README

**Files:**
- Modify: `tools/tv.sh` (new cases before `*)`; usage line)
- Modify: `README.md` (dev loop bullets; replace the first "v2 backlog" bullet with a "Home button" section; intro sentence "v1 runs as a normal app…" updated)

**Interfaces:**
- Produces subcommands: `home-take`, `home-restore`, `home-status`, `idle-set <minutes>`.

- [ ] **Step 1: Implement.** With `s="$(serial)"` guard like the others:
  - `home-status`: `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1`, then `pm list packages -d | grep -c launcherx` printed as `launcherx disabled: <0|1>`.
  - `home-take`: `cmd package set-home-activity com.nickesh.tvlauncher/.MainActivity`; `pm disable-user --user 0 com.google.android.apps.tv.launcherx`; then the `home-status` output.
  - `home-restore`: `pm enable com.google.android.apps.tv.launcherx`; `cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity`; then `home-status`.
  - `idle-set <minutes>`: validate integer ≥ 1; `settings put system screen_off_timeout $((minutes*60000))`; print the value read back.
  Each command refuses to run without a serial, exactly like `install`.
- [ ] **Step 2: Mapping check for `idle-set`.** Read `settings get system screen_off_timeout` (expect 600000), change "Screen saver → When to start" on the TV to 5 minutes via TV Settings (DPAD or ask Nickesh), read again (expect 300000), set it back with `tools/tv.sh idle-set 10` and read 600000. Record the three values in the task report.
- [ ] **Step 3: README.** Document the four commands, the stop rule, the remote-only restore path (Settings row → Enable, then uninstall TV Launcher), and the "Wireless debugging turns itself off on standby; Stay awake is on" caveat. Update the intro and the v2 backlog bullet.
- [ ] **Step 4: Shell check** — `bash -n tools/tv.sh`; `tools/tv.sh home-status` prints the stock component and `launcherx disabled: 0`.
- [ ] **Step 5: Commit** — `git commit -m "feat(tools): home-take / home-restore / home-status / idle-set; README"`.

---

### Task 6: Switch-over on the device

**Files:**
- Create: `docs/superpowers/reports/takeover-switchover.md`
- Screenshots: `shots/takeover-*.png` (git-ignored)

**Interfaces:** consumes Task 5 commands and the Task 3/4 build already installed.

- [ ] **Step 1: Before checklist** (both launchers enabled). Screenshot each: short Home press (`input keyevent KEYCODE_HOME`), long-press Home (`input keyevent --longpress KEYCODE_HOME`), Assistant (`input keyevent KEYCODE_ASSIST` or the remote mic), TV Settings (`am start -a android.settings.SETTINGS`), Inputs glass button, Netflix and YouTube launched from our row, screensaver (`tools/tv.sh dream` then `dream-stop`). Note what `KEYCODE_HOME` does with both launchers enabled (chooser / stock / ours).
- [ ] **Step 2: `tools/tv.sh home-take`.** Output must name `com.nickesh.tvlauncher/.MainActivity` and `launcherx disabled: 1`.
- [ ] **Step 3: Home semantics.** Launch YouTube from our row, wait 5 s, `KEYCODE_HOME` → shot `takeover-home-from-youtube` shows our Home, no Welcome, logcat `onNewIntent`. Press `KEYCODE_HOME` again on Home → shot identical, no Welcome. Open Settings, `KEYCODE_HOME` → Home.
- [ ] **Step 4: Standby.** `KEYCODE_SLEEP`, 30 s, `KEYCODE_WAKEUP` → Home, no Welcome (threshold 10 min). Then the `-PwelcomeReplayMs=20000` build: sleep 30 s, wake → Welcome → Home (shots `takeover-standby-short`, `takeover-standby-replay`). Reinstall the default build.
- [ ] **Step 5: Reboot.** `adb reboot`; loop `tools/tv.sh connect` until it succeeds, then screencap every 2 s for 60 s into `shots/takeover-boot-NN.png`. Expected: no stock-launcher frame; Welcome then Home. Ask Nickesh to watch the TV during this step since adb reconnects late in boot.
- [ ] **Step 6: After checklist.** Repeat Step 1's list. Record each item as unchanged / changed / gone.
- [ ] **Step 7: Both directions.** `tools/tv.sh home-restore` → `home-status` names launcherx, `KEYCODE_HOME` lands on the stock launcher (shot `takeover-restored`). Then `tools/tv.sh home-take` again → `home-status` names ours, `KEYCODE_HOME` lands on our Home. Leave the TV taken over.
- [ ] **Step 8: Report.** Write `takeover-switchover.md`: the before/after checklist table, the Step 1 observation about both launchers, shot names, logcat excerpts for one `onNewIntent` and one replay, anything that stopped working (for Nickesh's ruling), and whether the stop rule ever fired.
- [ ] **Step 9: Commit** — `git commit -m "docs: Home takeover switch-over report"`; `git push origin v1`.
