# Home takeover switch-over report (2026-10-30)

**Status: BLOCKED at Step 2. The stock launcher was never disabled. The stop rule did not fire.**
The TV is in its original state: `home-status` prints
`com.google.android.apps.tv.launcherx/.home.HomeActivity` / `launcherx disabled: 0`, and the
preferred HOME activity is launcherx again (`dumpsys package preferred-xml`).

## Why it stopped

`tools/tv.sh home-take` ran `set-home-activity com.nickesh.tvlauncher/.MainActivity` (Success), then
its gate re-read HOME and got launcherx, so it aborted before `pm disable-user`, as designed:

    Home resolves to 'com.google.android.apps.tv.launcherx/.home.HomeActivity', not com.nickesh.tvlauncher/.MainActivity; stock launcher left enabled

The reason is the HOME candidates' intent-filter priorities on this device
(`cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME`):

| # | Activity | priority |
|---|----------|----------|
| 0 | `com.google.android.apps.tv.launcherx/.home.HomeActivity` | 2 |
| 1 | `com.google.android.tungsten.setupwraith/.RecoveryActivity` | 1 |
| 2 | `com.nickesh.tvlauncher/.MainActivity` | 0 |
| 3 | `com.android.tv.settings/.system.FallbackHome` | -1000 |

Android only consults the preferred (set-home-activity) choice when the top two candidates have
equal priority; a strictly higher-priority system candidate wins outright. So:

- While launcherx is enabled, `set-home-activity` to ours is recorded (preferred-xml showed our
  component) but has no effect. The verify-before-disable gate can never pass on this device.
- If launcherx were disabled anyway, the top candidate becomes **setupwraith's RecoveryActivity
  (priority 1)**, not us (priority 0). The Home key would most likely open Google TV's setup /
  recovery flow (presumably the screen that offers to restore Google TV Home), i.e. the spec's
  stop condition. Not tried: that is a device-safety call for Nickesh.

Also present: a second user (user 10) with its own preferred HOME = launcherx.

## Options for Nickesh (none taken)

1. Also disable `com.google.android.tungsten.setupwraith` (or just its RecoveryActivity component,
   `pm disable-user`/`pm disable --user 0 <component>`) in `home-take`, and re-enable it in
   `home-restore`. Outside this epic's hard rules (only launcherx may be touched); setupwraith is
   the setup/recovery app, so disabling it carries its own risk (e.g. after a factory reset or
   OTA). Needs a ruling.
2. Change `home-take`'s gate to: set-home-activity, disable launcherx, then verify HOME resolves to
   ours and automatically run home-restore if not. This only helps if RecoveryActivity is not
   actually chosen, which the priority table says it will be; without option 1 it would just
   bounce back to restore.
3. Keep v1's normal-app model (no takeover).

## Before checklist (both launchers enabled)

| Item | Result | Shot |
|------|--------|------|
| Short Home (from our app) | Stock Google TV home, no chooser | `takeover-before-02-home-short` |
| Long-press Home | Quick-settings panel (Screensaver, Wi-Fi, Google Home, Bluetooth, Sleep timer, Audio output, Accessibility, All settings). Hosted by **launcherx** (`launcherx/.dashboard.DashboardHandler`): would be lost after a takeover. | `takeover-before-03-home-long` |
| Assistant (`KEYCODE_ASSIST`) | Gemini overlay, owned by `com.google.android.katniss` (not launcherx) | `takeover-before-04-assist` |
| Back on stock home | Starts the screensaver (our flip clock) - stock behaviour | `takeover-before-05-back-on-stock-home-dreams` |
| TV Settings (`android.settings.SETTINGS`) | `com.android.tv.settings/.MainSettings` | `takeover-before-06-settings` |
| Inputs glass button | Not shown: `com.android.tv.settings.INPUTS` resolves to nothing on this device ("No activity found"), so the button is hidden by design | `takeover-before-07-ours-topbar` |
| YouTube from our row | `com.google.android.youtube.tv/...MainActivity` resumed | `takeover-before-08-youtube` |
| Netflix from our row | `com.netflix.ninja/.MainActivity` resumed (screencap returns 0 bytes: Netflix is a secure surface; dumpsys is the evidence) | none |
| Screensaver (`dream` / `dream-stop`) | Our `ScreensaverService` runs; `dream-stop` returns to stock home | `takeover-before-10-dream`, `takeover-before-11-dream-stop` |

**Step 1 observation:** with both launchers enabled, `KEYCODE_HOME` goes straight to the stock
launcher: no chooser, not ours. Explained by the priority table above (launcherx priority 2).

## Steps 3-7

Not run (no takeover). No `onNewIntent`-from-Home or replay logcat excerpts exist from this run.

## The four check items

1. Second instance: not checkable (no takeover).
2. Restore from the TV (app-details Enable control): not checkable (launcherx never disabled).
   README wording unchanged.
3. `pm` strings: `pm enable com.google.android.apps.tv.launcherx` prints
   `Package com.google.android.apps.tv.launcherx new state: enabled` even when it was already
   enabled, so home-restore's gate matches. `new state: disabled-user` remains unconfirmed.
4. Home with both enabled: stock launcher, no chooser.

Stop rule: never fired (nothing was disabled).
