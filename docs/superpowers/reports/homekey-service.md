# Home key service: device report (2026-10-30)

Device: Chromecast with Google TV, wireless adb. Shots are in `shots/` (git-ignored).

## Tasks 4/5 device results
- Short press from YouTube opens our Home: `homekey-short-from-youtube`, `homekey-after-short`.
- Long press opens the Google TV quick-settings dashboard: `homekey-long-dashboard`.
- Welcome mash and 10-minute standby replay still behave: `homekey-welcome-mash`, `homekey-replay`.
- Settings row "Home button... On/Off": `homekey-settings-on`, `homekey-settings-off`; Accessibility page: `homekey-accessibility-page`.
- Virtual remote control check: `homekey-remote-control`.
- Key log lines: short from YouTube -> `short → home` then `Home: onNewIntent`; long -> `long → dashboard true`; after 10 min standby (replay build) -> `Home: welcome replay after standby`.
- Physical-remote gate (before building): `homekey-gate.md`.

## Reboot (Task 6)
- Service stayed enabled and bound across the reboot (`homekey-status`: on, bound yes).
- Observed by the human: Google TV launcher first, then about 30 s later TV Launcher's Home appeared by itself.
- System events agree: service process started 11:53:39 (about 41 s after boot), `MainActivity` created 11:53:58 (about 60 s after boot). Shot `homekey-after-boot` was taken later, when Settings was on top (the human had navigated).
- Log gap: `connected uptime=` and `boot → home` were not in logcat. `HomeKeyService.onServiceConnected` does log `Log.i("HomeKey", "connected uptime=$uptime")` and `openHome("boot")` logs `boot → home` at Info (or `boot → home failed` at Warn), so tag and level match; the earliest `HomeKey` line retained in the main buffer was 11:54:22, so those lines were most likely lost to buffer rotation during the busy boot (load average about 19). The boot launch itself is therefore inferred from the activity events, not from a `boot → home` line. Not verified further; no code changed.
- Later `homekey-on` (re-enable) logged `connected uptime=273163`, over the 90 s window, so no launch: the window does not fire outside boot.

## Physical remote
- Human, from TV Settings (11:54): long press then short press then long press; log `long → dashboard true`, `short → home` + `onNewIntent`, `long → dashboard true`.
- Human, from YouTube: short Home opened TV Launcher Home; a hold of about 1.5 s opened the quick-settings panel. Log: `short → home` (11:56:54, 11:57:00 incl. `onNewIntent`) and `long → dashboard true` (11:57:08). Shot `homekey-physical-after`.
- The `dev=` id of the Chromecast Remote does not appear in these HomeKey lines (the service does not log it), so the physical origin rests on the human's report.
- Result: PASS.

## Switch proof
- `homekey-off` then `remote short`: top resumed activity `com.google.android.apps.tv.launcherx/.home.HomeActivity` (shot `homekey-switch-off`).
- `homekey-on` then `remote short`: top resumed `com.nickesh.tvlauncher/.MainActivity` (shot `homekey-switch-on`).
- Result: PASS. Left ON.

## Final state
`tools/tv.sh homekey-status`: enabled_accessibility_services=com.nickesh.tvlauncher/com.nickesh.tvlauncher.HomeKeyService, accessibility_enabled=1, bound: yes.

## Did not match the spec / caveats
- Boot: Google TV shows first for about 30 s; ours takes over after (best effort, accepted by the user).
- Boot-log lines missing (above).
- `am force-stop` switches the service off; a long press is consumed even if the dashboard call returns false.
