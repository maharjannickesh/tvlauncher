# TV Launcher v2.2 — Home key via accessibility service

Date: 2026-10-30. Supersedes the switch-over part (§4–6) of `2026-10-30-home-takeover-design.md`; the app-side behaviour of that spec (Home semantics, Welcome replay) stays. Evidence: `docs/superpowers/reports/takeover-switchover.md` (why adb-disable cannot work) and `docs/superpowers/reports/spike-homekey.md` (what the accessibility route can do), spike code on branch `spike-homekey`.

## Intent

Make the remote's Home key open TV Launcher without disabling anything on the TV. On this Google TV build the stock launcher cannot be unseated (priority-ranked HOME candidates, shell-protected recovery component that self-heals), so instead an `AccessibilityService` in our app owns the Home key: short press → our Home; long press → Google TV's quick-settings panel, as today.

Nickesh's rules, unchanged: Welcome on boot and after ≥ 10 min asleep; Home on Home does nothing; Back on Home does nothing.

## Goals

1. Short Home press from anywhere (YouTube, Netflix, TV Settings, the stock launcher, the screensaver's dismissal) brings our Home to the front, warm, no Welcome (unless a ≥ 10 min standby preceded it, which the existing replay handles).
2. Long Home press (≥ 1 s held) opens Google TV's quick-settings dashboard, exactly what long-press does today.
3. Short press while our Home is on screen: nothing. While on All apps / Settings / Background: back to Home (existing `HomeKey` routing via `onNewIntent`).
4. The service survives standby and process restarts; once enabled it stays enabled across reboots (Android persists accessibility services).
5. Boot: best effort. If the service can start our activity when it binds at boot, boot lands on Welcome → Home; otherwise boot lands on Google TV and the first Home press brings us up. The device decides; the design accepts either and the README says which one was observed.
6. One switch on the TV turns it on or off: Settings → System → Accessibility → "TV Launcher Home key". A dev-script command does the same over adb.
7. Nothing on the TV is disabled, cleared, or uninstalled. Google TV Home stays installed and the default home; it is simply never reached by a short Home press while the service is on.

## Non-goals

- Owning the Home key without the user enabling an accessibility service (impossible without root or device-owner).
- Catching the Home key at the exact moment of boot before the service binds.
- Play Store distribution (accessibility-based key capture faces policy review; this is a sideloaded personal app).
- Hiding Google TV's own surfaces (notifications, Assistant), remapping any other key.

## Anti-goals

- A press that lands on a black screen or on nothing. If the service cannot start our activity, it must **not** consume the event (let the stock launcher handle it) and log why.
- Swallowing the long press without opening the dashboard.
- Any accessibility event work beyond key filtering: the service requests the minimum (`flagRequestFilterKeyEvents`, one event type) and does nothing in `onAccessibilityEvent`.
- Leaving the HOME intent filter from v2.1: it causes a second `type=home` instance and a cold Welcome when anything sends us the HOME intent. It goes.

## Constraints

- Chromecast with Google TV, Android 14, remote over Bluetooth. `input keyevent` does not reach accessibility services; testing uses the `uinput` virtual remote from the spike (JSON at `/data/local/tmp/u.json`, scripts to be committed under `tools/`), plus one physical-remote press by Nickesh.
- Settings to flip over adb: `settings put secure enabled_accessibility_services com.nickesh.tvlauncher/.HomeKeyService` and `settings put secure accessibility_enabled 1`; off = `settings delete secure enabled_accessibility_services` + `accessibility_enabled 0` (the TV's values before any of this: null / 0).
- Wireless debugging dies on every reboot and often on standby; the reboot check needs Nickesh to re-enable it afterwards.
- Lint 0 errors, tests green, same build/commit rules as before. Branch `v1`.

## Design

### 1. `HomeKeyService` (`AccessibilityService`)

- Manifest: `<service android:name=".HomeKeyService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="true" android:label="@string/homekey_service_label">` with the `android.accessibilityservice.AccessibilityService` intent filter and `<meta-data android:name="android.accessibilityservice" android:resource="@xml/homekey_service" />`. Label "TV Launcher Home key". Description string (shown in the TV's Accessibility settings): "Makes the remote's Home button open TV Launcher. Long press still opens Google TV's quick settings."
- `res/xml/homekey_service.xml`: `canRequestFilterKeyEvents="true"`, `accessibilityFlags="flagRequestFilterKeyEvents"`, `accessibilityEventTypes="typeWindowStateChanged"`, `accessibilityFeedbackType="feedbackGeneric"`, `description="@string/homekey_service_description"`.
- `onKeyEvent(event)`: only `KEYCODE_HOME` is handled; everything else returns false. Logic is a pure, tested decision object:
  - `HomeKeyDecision.onDown()` → consume (return true), remember `downTime`.
  - `HomeKeyDecision.onUp(heldMs)` → `Short` if `heldMs < LONG_PRESS_MS` (1000 ms, builder may tune to match Google TV's 1.46 s feel but must stay below it), else `Long`. Also handle a missing DOWN (UP without DOWN → do nothing, consume=false) and a DOWN with no UP within 5 s (reset).
  - `Short` → `startActivity(Intent(this, MainActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK))` wrapped in try/catch; on any exception log and return **false** for the UP (anti-goal 1; the DOWN was already consumed, which is acceptable: the stock launcher reacts on UP). Log `Log.i("HomeKey", "short → home")`.
  - `Long` → `performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)`; log the boolean result.
- `onServiceConnected`: log; set `serviceInfo.flags |= FLAG_REQUEST_FILTER_KEY_EVENTS` defensively; if `SystemClock.elapsedRealtime() < BOOT_WINDOW_MS` (90 s, i.e. the service is binding because the device just booted) start `MainActivity` once (goal 5, best effort, try/catch, logged).
- `onInterrupt`, `onAccessibilityEvent`: empty.
- Spec-level caveat recorded for the builder: the service must never block the main thread; `startActivity` from `onKeyEvent` was proven non-blocking in the spike.

### 2. `MainActivity` and manifest clean-up (removes the dead v2.1 switch-over pieces)

- Remove the `MAIN + HOME + DEFAULT` intent filter (anti-goal 4). Keep `launchMode="singleTask"` and `excludeFromRecents="true"` (needed so the service's plain intent reuses the task via `onNewIntent`). Keep `onNewIntent` → `homePresses` → `HomeKey.next` exactly as built.
- Remove the Settings row "Google TV Home…", `SystemIntents.stockHomeInstalled/openStockHomeDetails`, the `launcherx` `<queries>` package line and the `APPLICATION_DETAILS_SETTINGS` query (nothing is disabled anymore, so there is nothing to restore). Add instead a Settings row **"Home button…"** that opens the TV's Accessibility settings (`Settings.ACTION_ACCESSIBILITY_SETTINGS`), trailing value "On" / "Off" read from `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` containing our component (re-read on each Settings open; one helper `HomeKeyState.isEnabled(contentResolver)`), helper line "Turn on 'TV Launcher Home key' there to make the Home button open this launcher."
- Back on Home consumed, Welcome replay, `StandbyTracker` — unchanged.

### 3. `tools/tv.sh`

- Remove `home-take`, `home-restore`, `home-status` (dead route). Keep `idle-set`.
- Add `homekey-on`, `homekey-off`, `homekey-status` (the two settings above; status prints the enabled list and whether `dumpsys accessibility` shows our service bound).
- Add `remote <short|long>`: the spike's `uinput` virtual-remote press (commit the JSON and the helper under `tools/uinput/`), so Home presses can be driven over adb in future sessions.

### 4. README and spec corrections

- README "Home button" section rewritten: how to enable (TV Settings path and `tools/tv.sh homekey-on`), short vs long press, boot behaviour as observed, the "nothing is disabled" statement, and the dead-route note with a pointer to the switch-over report. The "Why two disables" paragraph goes.
- `2026-10-30-home-takeover-design.md`: a one-paragraph note at the top that §4–6 are superseded by this document and why.

### 0. Physical-remote gate (author's call, reviewer question) — before anything else is built

The spike only drove a virtual `uinput` remote. The first task of the plan is a 30-second gate on the physical remote: reset the TV's accessibility settings to baseline, install the spike build (branch `spike-homekey`, mode `split`) or the new service once it exists, `homekey-on`, and Nickesh presses Home short and long from YouTube. If a short press with the service bound still opens Google TV Home (the key never reached `onKeyEvent`), the epic stops there and the report says so; that outcome is a stop condition, not a documented limitation. Only when the physical remote passes does the rest get built.

### 0b. Baseline and tooling (author's calls, reviewer questions)

- The TV's state after the spike is unknown (service possibly still enabled in `split` mode, `accessibility_enabled` possibly 1, Wireless debugging off). The first device step of every task is `tools/tv.sh homekey-status`; the gate task resets to baseline (`settings delete secure enabled_accessibility_services`, `accessibility_enabled 0`) before the control press.
- The spike's `uinput` scripts were recovered from the session scratchpad and committed under `tools/uinput/` with this document; `tools/tv.sh remote short|long` wraps them.
- "Home button…" row fallback when `ACTION_ACCESSIBILITY_SETTINGS` does not resolve: toast "Open TV Settings → System → Accessibility → TV Launcher Home key", matching the existing precedent.
- `HomeKeyState.isEnabled` normalises each entry of the colon-separated list with `ComponentName.unflattenFromString` and requires `accessibility_enabled == 1` as well.

### 5. Verification on the device (in order)

1. Install; `tools/tv.sh homekey-status` shows off. Short press via `tools/tv.sh remote short` from YouTube with the service off → stock launcher (control).
2. `tools/tv.sh homekey-on`; bound per status. Short press from YouTube → our Home, no Welcome, logcat `short → home` and `onNewIntent`. Short press on Home → nothing. Short press on our Settings → Home.
3. Long press (`remote long`) from YouTube → Google TV dashboard opens (screenshot).
4. Sleep 30 s, wake, short press → Home, no Welcome. Then the `-PwelcomeReplayMs=20000` build: sleep 30 s, wake, short press → Welcome → Home. Reinstall default build.
5. `dumpsys activity activities`: exactly one `MainActivity` task after all of the above.
6. Physical remote: Nickesh presses Home short and long once each from YouTube; both must behave as in 2 and 3 (the only step that needs a human).
7. Reboot (Nickesh re-enables Wireless debugging afterwards): record where boot lands and whether the boot-window launch fired (logcat), then press Home. README gets the observed answer.
8. `homekey-off`, short press → stock launcher again (proves the switch), then `homekey-on` and leave it on.

Stop rule: if, with the service on, a short press ever lands on a black screen or nothing (not our Home, not the stock launcher), `homekey-off` immediately and report.

## Left to the builder

- Exact `LONG_PRESS_MS` (≤ 1400 ms) and the boot-window length; the `HomeKeyDecision` tests pin whatever is chosen.
- Whether to debounce a second DOWN arriving before the UP (treat as repeat, ignore).
- Shape of `tools/uinput/` (the spike's scripts are the starting point).

## Verification summary

- Unit: `HomeKeyDecision` (short, long at the boundary, UP without DOWN, DOWN timeout, non-Home keys untouched); `HomeKeyState.isEnabled` parsing of the colon-separated services list.
- Lint 0 errors; suite green (70 + new).
- Device: the eight steps above with screenshots `shots/homekey-*.png` and a report `docs/superpowers/reports/homekey-service.md`.
