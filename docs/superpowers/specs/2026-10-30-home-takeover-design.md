# TV Launcher v2.1 — Home-button takeover

> **Superseded (2026-10-30):** sections 4-6 (disabling the stock launcher and setupwraith's RecoveryActivity over adb) are superseded by `2026-10-30-homekey-service-design.md`. On this Chromecast the adb-disable route cannot work (priority ladder 2/1/0, the shell cannot disable RecoveryActivity, it self-heals; see `docs/superpowers/reports/takeover-switchover.md`), so the Home key is now handled by an accessibility service and nothing is disabled.

Date: 2026-10-30. Builds on `2026-10-29-tv-launcher-v1-design.md` (v1 is complete on branch `v1`, HEAD 5b1becd).

## Intent

v1 runs as an ordinary app that Nickesh opens from the stock Google TV home. The whole point of the project is to *be* the home screen: turn the TV on and see "Welcome Nickesh!!!", then the calm dashboard, then the flip clock when idle — never the stock Google TV rows. This change makes Android treat TV Launcher as the device's home activity, retires the stock launcher (`com.google.android.apps.tv.launcherx`) over adb, and keeps a one-command way back.

Nickesh's words, verbatim where it matters:

- "when i start tv, it should show Welcome Nickesh, and then next is once everything is loaded then the home screen loads, after being idle for certain time in home screen then flip clock should start coming."
- "when i am in home and press home in remote it should do nothing"
- Welcome should replay "after 10 min asleep" (standby), not on a quick power-off/on.

## Goals

1. Pressing Home anywhere (inside Netflix, Settings, the screensaver's dismissal) lands on TV Launcher's Home.
2. Booting the TV lands on Welcome → Home with no stock launcher frame in between.
3. Waking from standby after ≥ 10 minutes asleep replays Welcome, then Home. Shorter standby resumes Home directly.
4. Home pressed while Home is on screen does nothing visible. Back on Home does nothing visible.
5. Home pressed while on All apps / Settings / Background returns to Home (Compose screen change, not an activity restart; no Welcome).
6. The stock launcher is disabled, not uninstalled, and can be restored by one dev-script command, or from the TV itself without the Mac.
7. Nothing else on the TV changes: Assistant mic, Netflix/YouTube remote keys, TV Settings, Inputs, the Google TV screensaver package (`dreamx`) all keep working.

## Non-goals

- Replacing or hiding Google TV's long-press-Home panel, notifications, or quick settings (system-owned).
- Rearranging the apps row, Google Photos, release build, cold-start work (separate v2 items).
- Running without adb for the initial switch-over. `pm disable-user` needs the shell user; the app cannot disable another package itself.
- Any idle timer of our own. Android decides when a screensaver may start; we only set the system value.

## Anti-goals

- **The TV must never be left without a home screen.** A black screen or a crash loop after the switch is a failure even if everything else works. The switch-over order below exists for this reason.
- Never disable, clear or uninstall any package other than `com.google.android.apps.tv.launcherx`. Never `pm clear` anything on the TV in this epic (v1's final review already reset our own settings once).
- No Welcome flash when returning from another app or from a short standby.

## Constraints

- Chromecast with Google TV (Android 14, API 34), wireless adb at 10.0.0.21 via `tools/tv.sh`; adb drops when the TV standbys, so "Stay awake" must be on during device work. The TV was unreachable (standby) when this was written.
- Kotlin + Compose for TV, minSdk 26, JDK 21 for Gradle. Unit tests with JUnit 4 (52 green), lint 0 errors must stay so.
- The remote's power button is standby (`ACTION_SCREEN_OFF`), not power-off. The process usually survives standby, so Welcome replay cannot rely on process death.
- While the screensaver (our `DreamService`) runs, the screen is *on*. Only the screen-off interval counts as "asleep".
- Read from the device 2026-10-30 (awake, Stay awake on = `stay_on_while_plugged_in 7`): stock home component is `com.google.android.apps.tv.launcherx/.home.HomeActivity`; `screen_off_timeout` = 600000 (10 min, the screensaver "When to start"); `sleep_timeout` = 1800000 (30 min of screensaver before standby). The adb connect port changes on every wake (38949 today); `tools/tv.sh` already discovers it.

## Design

### 1. Manifest — become a home activity

`MainActivity` gets a second intent filter alongside the existing `MAIN + LEANBACK_LAUNCHER` one (kept so the app still appears in app lists while both launchers coexist):

```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

Activity attributes: `android:launchMode="singleTask"`, `android:excludeFromRecents="true"`, `android:stateNotNeeded="true"` (the standard home-app set; AOSP Launcher3 uses the same). `clearTaskOnLaunch` is unnecessary because all our screens are Compose state inside one activity.

### 2. Home key semantics (goal 4 and 5)

Android does not deliver `KEYCODE_HOME` to apps; it starts the home activity with the HOME intent. With `singleTask` that arrives as `onNewIntent` when we are already running. Handling:

- `onNewIntent` with `CATEGORY_HOME`: if the current screen is Welcome or Home → no-op (no state change, no recomposition side effects). Otherwise → set `screen = Screen.Home`. The 400 ms cross-fade that already exists is the only visible motion.
- Back on Home: a `BackHandler` that consumes the event (today Back finishes the activity, which after the switch would re-launch Home with a flash). Back on All apps / Settings / Background keeps its current behaviour (returns to Home).

### 3. Welcome replay after standby (goal 3)

A small `StandbyTracker` in `AppContainer`, registered from `TvLauncherApp.onCreate` for `ACTION_SCREEN_OFF` and `ACTION_SCREEN_ON`, records `screenOffAt` / `screenOnAt` (elapsedRealtime, in memory only; if the process died during standby the next launch is a cold start and shows Welcome anyway, which matches the rule).

Pure decision, unit-tested: `WelcomeReplay.shouldReplay(screenOffAt, screenOnAt, thresholdMs = 10 min)` is true when both timestamps exist, `screenOnAt > screenOffAt`, and the gap ≥ threshold. Dream time does not count (screen stays on). Threshold is a constant, not a setting (builder call: Nickesh named one number; no UI until he asks).

**Which wake counts (author's call, reviewer question):** the replay fires on the *first arrival at our Home* after a qualifying standby, whichever app was in front when the TV woke. Falling asleep with Netflix on, waking into Netflix and pressing Home in the morning therefore greets; returning to Home a second time that day does not. The wake is consumed when the replay fires (or when Home is first shown after it), never on `ACTION_SCREEN_ON` itself.

**Cold starts (author's call, reviewer question):** every cold start of the process shows Welcome, exactly as v1 does today, including the rare case where Android killed our process in the background while another app was in front. Persisting standby timestamps to tell "boot or long standby" from "background kill" is deliberately not built; if the background-kill flash turns out to happen in practice, that is a follow-up with its own data design.

`MainActivity` checks on `ON_START`: if `shouldReplay` and the tracker's wake has not been consumed yet → `welcomeDone = false; screen = Screen.Welcome`, and mark the wake consumed so returning from Netflix afterwards does not replay. The Welcome screen's existing readiness gate (apps ready, settings emitted, ≥ 1.2 s, ≤ 4 s cap) runs unchanged, so a replay looks exactly like a cold start.

### 4. Dev-script switch-over and restore (goals 2, 6)

`tools/tv.sh` gains:

- `home-take` — `cmd package set-home-activity com.nickesh.tvlauncher/.MainActivity`, then `pm disable-user --user 0 com.google.android.apps.tv.launcherx`, then prints `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` so the terminal proves which activity now owns Home.
- `home-restore` — `pm enable com.google.android.apps.tv.launcherx`, then `cmd package set-home-activity com.google.android.apps.tv.launcherx/<its home activity>` (`com.google.android.apps.tv.launcherx/.home.HomeActivity`, read from the device 2026-10-30), then the same proof line.
- `home-status` — the proof line plus `pm list packages -d | grep launcherx`.
- `idle-set <minutes>` — `settings put system screen_off_timeout <ms>`; the TV's "Screen saver → When to start" reads this value (currently 10 min; `secure sleep_timeout`, currently 30 min, is how long the screensaver runs before standby and is left alone). Builder confirms the mapping once by changing the value in TV Settings and re-reading it. Default left as the TV has it; Nickesh picks the number later.

README's dev loop section documents all four and the "Stay awake" caveat.

### 5. Restore path from the TV itself (goal 6)

Settings screen gets a last row **"Google TV Home…"**, shown only when `launcherx` is installed. Pressing it opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for that package, where the stock Enable/Disable button lives, so the stock home can be re-enabled with the remote alone. Fallback text if the intent does not resolve: Toast "Open TV Settings → Apps → See all apps → Google TV Home". The row never performs the enable itself (impossible without shell).

**What this achieves (author's call, reviewer question):** re-enabling only. Android may keep our persisted home preference, so Home can still land on TV Launcher afterwards. The complete swap-back without the Mac is: this row → Enable, then TV Settings → Apps → TV Launcher → Uninstall. The row's helper text says so in one line, and the README repeats it. The one-command full restore remains `tools/tv.sh home-restore`.

### 6. Switch-over order (anti-goal 1)

Done by the builder on the device, in this order, each step screenshotted into `shots/`:

1. Install the new build. Open it from the stock launcher. Confirm Welcome → Home, Back on Home does nothing, Home from Settings returns to Home.
2. Press Home on the remote with *both* launchers enabled. Record what Android does (chooser, stays on stock, or ours). No disabling yet.
3. `home-take`. Press Home from inside another app (YouTube): expect our Home with no Welcome. Press Home on Home: expect nothing.
4. Standby test: `input keyevent KEYCODE_SLEEP`, wait 30 s, `KEYCODE_WAKEUP` → Home resumes with no Welcome. Repeat with the threshold temporarily lowered via a debug-only override (builder call: a `-Pwelcome.replayMs=` style BuildConfig field, debug only) → Welcome replays.
5. `adb reboot`. Expect Welcome → Home with no stock launcher frame (poll screencaps from the moment adb reconnects).
6. `home-restore` then `home-take` again, proving both directions work before leaving the TV in the taken-over state.

If step 3 or 5 shows a black screen, a crash, or a dead Home key, run `home-restore` immediately and stop; the epic is not done.

**Other losses (author's call, reviewer question):** `launcherx` may host surfaces beyond the home rows (long-press-Home panel, notification panel, Assistant result cards). Before `home-take` the builder records a checklist with screenshots: short Home, long-press Home, Assistant mic button, Netflix and YouTube remote keys, TV Settings, Inputs, and the screensaver. After `home-take` the same list is re-run. Anything that stopped working is *reported, not a stop condition*; the TV stays taken over and Nickesh decides whether the loss is acceptable or `home-restore` runs. Only black screen / crash / dead Home key trigger the automatic restore.

## Left to the builder

- Exact `StandbyTracker` wiring (receiver vs. `PowerManager.isInteractive` polled on lifecycle) as long as the pure decision function is tested and the three device cases in §6.4 pass.
- Whether the `onNewIntent` no-op needs `Intent.FLAG_ACTIVITY_*` filtering for the `set-home-activity` launch path; verify on device.
- Shape of the debug threshold override.
- Log lines (`Log.i("Home", …)`) for the HOME intent and the replay decision so device runs can be read from logcat.

## Verification

- Unit tests: `WelcomeReplay` (gap below/at/above threshold, missing timestamps, off-after-on ordering); `onNewIntent` routing as a pure function of (current screen, intent categories) → next screen.
- Lint 0 errors; 52 + new tests green.
- Device: the six-step order in §6 with screenshots `shots/takeover-*.png`; logcat excerpt of one replay decision.
- README and `docs/superpowers/reports/` updated; branch `v1` (user has not asked to merge to `main`).

## Amendment 2026-10-30 (after the first switch-over attempt)

On this Chromecast the HOME candidates are ranked by intent-filter priority: `launcherx/.home.HomeActivity` 2, `com.google.android.tungsten.setupwraith/.RecoveryActivity` 1, ours 0 (non-privileged apps are capped at 0), `com.android.tv.settings/.system.FallbackHome` −1000. Android honours `set-home-activity` only when the top two candidates tie, so disabling `launcherx` alone would hand Home to the setup wizard's RecoveryActivity. Nickesh ruled: `home-take` also disables **that one component** (`pm disable-user --user 0 com.google.android.tungsten.setupwraith/.RecoveryActivity`, never the whole package), and `home-restore` re-enables both. The anti-goal's "never disable anything other than launcherx" is widened to exactly these two. The verify-before-disable gate becomes verify-after-disable: if HOME does not resolve to ours once both are disabled, `home-take` runs the restore itself and exits non-zero. Known loss, accepted: the long-press-Home quick-settings panel is hosted by `launcherx` and disappears while taken over; TV Settings stays reachable from the top bar.
