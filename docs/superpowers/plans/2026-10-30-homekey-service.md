# Home Key via Accessibility Service — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The remote's Home key opens TV Launcher (short press) or Google TV's quick-settings panel (long press) through an accessibility service, with nothing on the TV disabled, proven on the physical remote and documented.

**Architecture:** A pure `HomeKeyDecision` (DOWN/UP timing → SHORT / LONG / NONE) and a pure `HomeKeyState` (parse the enabled-services setting) carry the logic and the tests; `HomeKeyService` (`AccessibilityService`) is a thin shell around them that starts `MainActivity` with a plain explicit intent (reuses the `singleTask` task → existing `onNewIntent` → `HomeKey` routing). The dead v2.1 switch-over pieces (HOME intent filter, "Google TV Home…" row, `home-take`/`home-restore`) are removed; `tools/tv.sh` gains `homekey-on|off|status` and `remote short|long` (uinput virtual remote). A physical-remote gate runs first.

**Tech Stack:** Kotlin + Compose for TV (as before), JUnit 4, bash + adb + the device's `uinput` tool.

**Spec:** `docs/superpowers/specs/2026-10-30-homekey-service-design.md` (reports it cites: `docs/superpowers/reports/spike-homekey.md`, `docs/superpowers/reports/takeover-switchover.md`; spike code on branch `spike-homekey`).

## Global Constraints

- Branch `v1`, package `com.nickesh.tvlauncher`. Commit after every task; trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Do not push (controller pushes).
- Build/test: `export JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/jdk-21.0.12.1+1/Contents/Home` then `./gradlew :app:testDebugUnitTest lintDebug assembleDebug`. 70 tests green, lint 0 errors at start; must hold at every commit.
- Device: Chromecast 10.0.0.21 over wireless adb (`tools/tv.sh connect`; serial `10.0.0.21:37135` right now, changes on wake/reboot). `input keyevent KEYCODE_HOME` does NOT reach accessibility services; use the uinput virtual remote (`tools/tv.sh remote short|long`, Task 3) or the physical remote (Nickesh).
- Accessibility settings: on = `settings put secure enabled_accessibility_services com.nickesh.tvlauncher/.HomeKeyService` + `settings put secure accessibility_enabled 1`; off = `settings delete secure enabled_accessibility_services` + `settings put secure accessibility_enabled 0`. Baseline values on the TV: `null` / `0`.
- Service component `com.nickesh.tvlauncher/.HomeKeyService`; label `TV Launcher Home key`; description `Makes the remote's Home button open TV Launcher. Long press still opens Google TV's quick settings.`
- `LONG_PRESS_MS = 1000`; `BOOT_WINDOW_MS = 90_000` (elapsedRealtime at bind below this = boot). Short press → `Intent(this, MainActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK)` (never MAIN/HOME). Long press → `performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)`.
- If `startActivity` throws, the UP is NOT consumed (return false) and the failure is logged (`Log.w("HomeKey", …)`).
- **Never** `pm clear`, disable, enable or uninstall any package; never toggle Wi-Fi; the only settings changed are the two accessibility settings above (and `screen_off_timeout` via the existing `idle-set`).
- Settings row copy: label `Home button…`, value `On` / `Off`, helper `Turn on 'TV Launcher Home key' there to make the Home button open this launcher.`, toast fallback `Open TV Settings → System → Accessibility → TV Launcher Home key`.
- Stop rule: with the service on, a short press that lands on a black screen or nothing → `tools/tv.sh homekey-off` immediately and report. Gate rule (Task 1): physical short press with the service bound still opening Google TV Home → stop the epic and report.

## Review Focus

1. Auto-repeat DOWN events while the key is held (some remotes repeat): exactly one SHORT/LONG decision at UP, first downTime kept, no double launch. → `HomeKeyDecisionTest.repeatDownKeepsFirstDownTime` (Task 2).
2. UP with no preceding DOWN (service bound mid-press, or a DOWN that went to the stock launcher): not consumed, nothing launched. → `HomeKeyDecisionTest.upWithoutDown_isNone` (Task 2).
3. Enabled-services list stored fully qualified, with other services around ours and stray whitespace, or `accessibility_enabled` 0 while the list contains us: "On" only when both hold. → `HomeKeyStateTest` (Task 2).
4. Service binds at boot but Android refuses the activity start: no crash, warning logged, boot lands on Google TV. → Task 4 try/catch around the boot launch; Task 6 reboot observation.
5. Short press while Welcome is still running (user mashing Home at boot): Welcome completes once, no restart. → existing `HomeKeyTest.welcomeIgnoresHome`; device check in Task 4 step 7.

---

### Task 1: Physical-remote gate

**Files:**
- Create: `docs/superpowers/reports/homekey-gate.md`

**Interfaces:** none (device + human). Uses the spike build on branch `spike-homekey` (service modes via `am broadcast -a com.nickesh.tvlauncher.SPIKE --es mode split --ei ga 4`).

- [ ] **Step 1: Baseline.** `tools/tv.sh connect`; `adb shell settings get secure enabled_accessibility_services` → `null`; `accessibility_enabled` → `0`. Record both.
- [ ] **Step 2: Install the spike build** without touching `v1`: `git worktree add /tmp/tvl-spike spike-homekey` (or `git stash`-free checkout in a worktree), build + install from there (`ANDROID_SERIAL=<serial> ./gradlew installDebug`), then `am start -n com.nickesh.tvlauncher/.MainActivity` once so the process exists, then the broadcast above to set mode `split` and `ga=4`.
- [ ] **Step 3: Enable.** The two `settings put` commands; `dumpsys accessibility | grep -i HomeKeyService` shows the service bound. Open YouTube (`monkey -p com.google.android.youtube.tv 1`).
- [ ] **Step 4: Nickesh presses the physical remote's Home** (short) from YouTube → expected: TV Launcher's Home. `logcat -d -s HomeKey:I` must show `key code=KEYCODE_HOME action=DOWN` / `UP` lines with a `dev=` id different from the uinput device, and `startActivity ok`. Screenshot `shots/homekey-gate-short.png`.
- [ ] **Step 5: Nickesh holds Home ~1.5 s** from YouTube → expected: Google TV quick-settings dashboard. logcat shows `performGlobalAction(4) -> true`. Screenshot `shots/homekey-gate-long.png`.
- [ ] **Step 6: Restore baseline** (`settings delete …`, `accessibility_enabled 0`), remove the worktree, reinstall the `v1` build (`tools/tv.sh install`).
- [ ] **Step 7: Report** `homekey-gate.md`: PASS/FAIL per press with the logcat lines and the `dev=` id; baseline before/after. **If step 4 opened Google TV Home instead of ours, the epic stops here**: write the report, commit, return DONE_WITH_CONCERNS.
- [ ] **Step 8: Commit** — `docs: physical-remote gate for the Home key service`.

---

### Task 2: `HomeKeyDecision` and `HomeKeyState` (pure, tested)

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/domain/home/HomeKeyDecision.kt`
- Create: `app/src/main/java/com/nickesh/tvlauncher/domain/home/HomeKeyState.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/domain/home/HomeKeyDecisionTest.kt`
- Test: `app/src/test/java/com/nickesh/tvlauncher/domain/home/HomeKeyStateTest.kt`

**Interfaces:**
- Produces:
  - `enum class Press { SHORT, LONG, NONE }`
  - `class HomeKeyDecision(private val longPressMs: Long = LONG_PRESS_MS) { fun onDown(eventTimeMs: Long): Boolean; fun onUp(eventTimeMs: Long): Press; companion object { const val LONG_PRESS_MS = 1000L } }` — `onDown` always returns true (consume) and records the first downTime of a hold (a repeat DOWN while held keeps the original); `onUp` returns NONE when no DOWN is pending, else SHORT if `eventTimeMs − downTime < longPressMs`, LONG otherwise, and clears the pending DOWN.
  - `object HomeKeyState { const val PACKAGE = "com.nickesh.tvlauncher"; const val SERVICE = "com.nickesh.tvlauncher.HomeKeyService"; fun isEnabled(enabledServices: String?, accessibilityEnabled: Int): Boolean }` — true iff `accessibilityEnabled == 1` and the colon-separated list contains our component in either `pkg/.HomeKeyService` or `pkg/pkg.HomeKeyService` form (entries trimmed; a class starting with `.` is prefixed with its package).

- [ ] **Step 1: Write the failing tests**

```kotlin
class HomeKeyDecisionTest {
    private val d = HomeKeyDecision()
    @Test fun shortPress() { assertTrue(d.onDown(1_000)); assertEquals(Press.SHORT, d.onUp(1_080)) }
    @Test fun justUnderThreshold_isShort() { d.onDown(0); assertEquals(Press.SHORT, d.onUp(999)) }
    @Test fun atThreshold_isLong() { d.onDown(0); assertEquals(Press.LONG, d.onUp(1_000)) }
    @Test fun longHold_isLong() { d.onDown(0); assertEquals(Press.LONG, d.onUp(6_000)) }
    @Test fun upWithoutDown_isNone() { assertEquals(Press.NONE, d.onUp(500)) }
    @Test fun secondUp_isNone() { d.onDown(0); d.onUp(80); assertEquals(Press.NONE, d.onUp(90)) }
    @Test fun repeatDownKeepsFirstDownTime() { d.onDown(0); assertTrue(d.onDown(600)); assertEquals(Press.LONG, d.onUp(1_200)) }
    @Test fun customThreshold() { val c = HomeKeyDecision(longPressMs = 1_400); c.onDown(0); assertEquals(Press.SHORT, c.onUp(1_300)) }
}

class HomeKeyStateTest {
    private val short = "com.nickesh.tvlauncher/.HomeKeyService"
    private val full = "com.nickesh.tvlauncher/com.nickesh.tvlauncher.HomeKeyService"
    @Test fun shortForm_enabled() { assertTrue(HomeKeyState.isEnabled(short, 1)) }
    @Test fun fullForm_amongOthers_withWhitespace() {
        assertTrue(HomeKeyState.isEnabled("com.google.android.marvin.talkback/.TalkBackService: $full :", 1))
    }
    @Test fun listed_butAccessibilityOff() { assertFalse(HomeKeyState.isEnabled(short, 0)) }
    @Test fun nullOrEmpty_isOff() { assertFalse(HomeKeyState.isEnabled(null, 1)); assertFalse(HomeKeyState.isEnabled("", 1)) }
    @Test fun otherServiceOnly_isOff() { assertFalse(HomeKeyState.isEnabled("com.other/.HomeKeyService", 1)) }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :app:testDebugUnitTest --tests '*HomeKeyDecisionTest*' --tests '*HomeKeyStateTest*'`; expected: unresolved references.
- [ ] **Step 3: Implement** both as declared (no Android imports in either file).
- [ ] **Step 4: Full suite + lint** — expected 83 tests, 0 failures, lint 0 errors.
- [ ] **Step 5: Commit** — `feat: HomeKeyDecision and HomeKeyState`.

---

### Task 3: `tools/tv.sh` — homekey commands, uinput remote, remove the dead switch-over

**Files:**
- Modify: `tools/tv.sh` (remove `home-take`, `home-restore`, `home-status`, `home_status`, `recovery_disabled`, the `STOCK*`/`RECOVERY*` variables and the `OURS` variable if unused; keep `idle-set`; add the new cases; usage line)
- Rewrite: `tools/uinput/` → one script `tools/uinput/press.sh <serial> <hold_ms>` plus the generated JSON (replace the spike's `mk`, `press`, `hw`, `step`, `yt`, `a`, `u.json`, which reference a scratchpad path)

**Interfaces:**
- Produces: `tools/tv.sh homekey-on`, `homekey-off`, `homekey-status` (prints `enabled_accessibility_services=<value>`, `accessibility_enabled=<value>`, `bound: yes|no` from `dumpsys accessibility | grep -q HomeKeyService`), `tools/tv.sh remote short|long` (hold 80 ms / 1500 ms, scancode 172 KEY_HOMEPAGE on a virtual bluetooth device named `TV Launcher Remote`, via `uinput /data/local/tmp/tvlauncher-remote.json`).

- [ ] **Step 1: `press.sh`.** Generates the JSON (register device with `vid 6353 pid 1 bus bluetooth`, keys `[172]`, delay 1500, inject DOWN, delay `<hold_ms>`, inject UP, delay 800), pushes it to `/data/local/tmp/tvlauncher-remote.json`, runs `adb -s <serial> shell uinput /data/local/tmp/tvlauncher-remote.json`. Delete the spike's seven files.
- [ ] **Step 2: `tv.sh` cases** with the usual `serial()` guard; `remote` validates its argument (`short|long`), `homekey-off` uses `settings delete` (not `put ""`).
- [ ] **Step 3: Verify** — `bash -n tools/tv.sh tools/uinput/press.sh`; `tools/tv.sh homekey-status` → `null` / `0` / `bound: no`; `tools/tv.sh remote short` with the v1 build (no service) → the stock launcher opens (screenshot `shots/homekey-remote-control.png`), proving the virtual remote reaches the system.
- [ ] **Step 4: Commit** — `feat(tools): homekey-on/off/status and a uinput virtual remote; drop home-take/restore`.

---

### Task 4: `HomeKeyService`

**Files:**
- Create: `app/src/main/java/com/nickesh/tvlauncher/HomeKeyService.kt`
- Create: `app/src/main/res/xml/homekey_service.xml`
- Modify: `app/src/main/res/values/strings.xml` (`homekey_service_label`, `homekey_service_description`)
- Modify: `app/src/main/AndroidManifest.xml` (add the `<service>` block; the spike's block on `spike-homekey` is the template, with `android:label="@string/homekey_service_label"`)

**Interfaces:**
- Consumes: `HomeKeyDecision`, `Press` (Task 2); `tools/tv.sh homekey-*`, `remote` (Task 3).
- Produces: `class HomeKeyService : AccessibilityService()` with `companion object { const val BOOT_WINDOW_MS = 90_000L; const val TAG = "HomeKey" }`.

- [ ] **Step 1: XML** — `canRequestFilterKeyEvents="true"`, `accessibilityFlags="flagRequestFilterKeyEvents"`, `accessibilityEventTypes="typeWindowStateChanged"`, `accessibilityFeedbackType="feedbackGeneric"`, `description="@string/homekey_service_description"`.
- [ ] **Step 2: Service.** `onServiceConnected`: `serviceInfo = serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS }`; `Log.i(TAG, "connected uptime=…")`; if `SystemClock.elapsedRealtime() < BOOT_WINDOW_MS` → `openHome("boot")`. `onKeyEvent(event)`: non-Home → false; `ACTION_DOWN` → `decision.onDown(event.eventTime)`; `ACTION_UP` → `when (decision.onUp(event.eventTime)) { SHORT -> openHome("short"); LONG -> { Log.i(TAG, "long → dashboard ${performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)}"); true }; NONE -> false }`; other actions → false. `private fun openHome(why: String): Boolean` = start the plain explicit intent with `FLAG_ACTIVITY_NEW_TASK` inside `try`, `Log.i(TAG, "$why → home")`, return true; `catch (e: Exception)` → `Log.w(TAG, "$why → home failed", e)`, return false. `onAccessibilityEvent`, `onInterrupt`: empty. No receivers, no prefs, no modes.
- [ ] **Step 3: Build, test, lint** — 83 tests, lint 0 errors.
- [ ] **Step 4: Device — control.** `tools/tv.sh install`; `homekey-status` → off. `tools/tv.sh remote short` from YouTube → stock launcher (control).
- [ ] **Step 5: Device — short press.** `tools/tv.sh homekey-on`; status `bound: yes`. From YouTube: `remote short` → our Home, no Welcome; logcat `short → home` and the `onNewIntent` line (`shots/homekey-short-from-youtube.png`). `remote short` on Home → unchanged. Open our Settings, `remote short` → Home.
- [ ] **Step 6: Device — long press.** From YouTube: `remote long` → Google TV dashboard (`shots/homekey-long-dashboard.png`), logcat `long → dashboard true`.
- [ ] **Step 7: Device — Welcome mash.** `am force-stop com.nickesh.tvlauncher`; `remote short` five times within ~3 s → Welcome once, then Home (`shots/homekey-welcome-mash.png`).
- [ ] **Step 8: Device — standby.** `input keyevent KEYCODE_SLEEP`, 30 s, `KEYCODE_WAKEUP`, `remote short` → Home, no Welcome. Then `./gradlew installDebug -PwelcomeReplayMs=20000`, sleep 30 s, wake, `remote short` → Welcome → Home (`shots/homekey-replay.png`). Reinstall default build (`tools/tv.sh install`). `dumpsys activity activities | grep -c 'MainActivity'` → one task.
- [ ] **Step 9: Commit** — `feat: HomeKeyService — Home key opens TV Launcher, long press opens quick settings`. Leave the service ON.

---

### Task 5: App clean-up and the "Home button…" row

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (remove the MAIN+HOME+DEFAULT filter; remove the `APPLICATION_DETAILS_SETTINGS` query and the `<package launcherx>` line; add `<intent><action android:name="android.settings.ACCESSIBILITY_SETTINGS" /></intent>`; keep `singleTask` + `excludeFromRecents`)
- Modify: `app/src/main/java/com/nickesh/tvlauncher/data/system/SystemIntents.kt` (remove `STOCK_HOME_PACKAGE`, `stockHomeInstalled`, `openStockHomeDetails`; add `fun openAccessibilitySettings(): Boolean` — `Settings.ACTION_ACCESSIBILITY_SETTINGS` + `FLAG_ACTIVITY_NEW_TASK`, catches `ActivityNotFoundException` and `SecurityException`, logs like `perform`)
- Create: `app/src/main/java/com/nickesh/tvlauncher/data/system/HomeKeyStateReader.kt` — `fun HomeKeyState.read(context: Context): Boolean`, a top-level extension (the pure `HomeKeyState` from Task 2 stays Android-free) using `Settings.Secure.getString(resolver, ENABLED_ACCESSIBILITY_SERVICES)` and `Settings.Secure.getInt(resolver, ACCESSIBILITY_ENABLED, 0)`.
- Modify: `app/src/main/java/com/nickesh/tvlauncher/ui/settings/SettingsScreen.kt` (replace the `stockHomeInstalled`/`onOpenStockHome` parameters and row with `homeKeyEnabled: Boolean = false, onOpenHomeKey: () -> Unit = {}` and the row `SettingRow("Home button…", onClick = onOpenHomeKey) { ValueText(if (homeKeyEnabled) "On  ›" else "Off  ›") }` + the helper line, same style as the one being replaced)
- Modify: `app/src/main/java/com/nickesh/tvlauncher/MainActivity.kt` (Settings branch: `homeKeyEnabled = remember { HomeKeyState.read(this@MainActivity) }`, `onOpenHomeKey = { if (!container.systemIntents.openAccessibilitySettings()) Toast(… fallback copy …, LENGTH_LONG) }`)

**Interfaces:**
- Consumes: `HomeKeyState.isEnabled` (Task 2).
- Produces: `SystemIntents.openAccessibilitySettings(): Boolean`; `HomeKeyState.read(context): Boolean`.

- [ ] **Step 1: Make the changes above.**
- [ ] **Step 2: Build, test, lint** — 83 tests, lint 0 errors.
- [ ] **Step 3: Device.** Install (service still on from Task 4). Settings → last row shows `Home button…  On ›` (`shots/homekey-settings-on.png`); OK opens the TV's Accessibility settings page (`shots/homekey-accessibility-page.png`); Back returns to our Settings. `tools/tv.sh homekey-off`, reopen Settings → `Off ›`; `homekey-on` again. `remote short` from YouTube → Home (the HOME filter removal changed nothing). `dumpsys activity activities` → one MainActivity task.
- [ ] **Step 4: Commit** — `refactor: drop the HOME intent filter and the stock-launcher row; add the Home button settings row`.

---

### Task 6: Docs, reboot, physical remote, report

**Files:**
- Modify: `README.md` (rewrite "Home button": enable via TV Settings → System → Accessibility → TV Launcher Home key or `tools/tv.sh homekey-on`; short vs long press; nothing disabled; boot behaviour as observed; the dead-route note pointing to `docs/superpowers/reports/takeover-switchover.md`; remove the "Why two disables" paragraph and the `home-take` lines in the dev-loop section; `remote short|long` and `homekey-*` in the dev loop)
- Modify: `docs/superpowers/specs/2026-10-30-home-takeover-design.md` (one paragraph at the top: §4–6 superseded by the homekey-service design, why)
- Create: `docs/superpowers/reports/homekey-service.md`

- [ ] **Step 1: README + spec note.**
- [ ] **Step 2: Reboot.** `adb reboot`. Nickesh re-enables Wireless debugging afterwards and tells the controller the pairing code if asked; `tools/tv.sh connect`. Record: where boot landed (Nickesh's observation and a screenshot once adb is back), logcat `connected uptime=` and whether `boot → home` or `boot → home failed` appeared, `homekey-status` still on. Put the observed boot behaviour into the README (goal 5).
- [ ] **Step 3: Physical remote (Nickesh).** Short press from YouTube → our Home; long press → dashboard. logcat lines with the remote's `dev=` id, screenshots `shots/homekey-physical-short.png`, `-long.png`.
- [ ] **Step 4: Switch proof.** `homekey-off`, `remote short` → stock launcher; `homekey-on`, `remote short` → ours. Leave on.
- [ ] **Step 5: Report** `homekey-service.md`: results of Task 4/5 device steps (shot names), reboot observation, physical-remote result, final `homekey-status`, anything that did not match the spec.
- [ ] **Step 6: Commit** — `docs: Home key service README, spec note, device report`.
