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

## Deferred minors and rulings (from the SDD ledgers, 2026-10-30)

- Task 2: minor (deferred): entry.trim() called three times; untested parser edges (`pkg/`, full-form under other package). Carry to Task 4: a stale pending DOWN (UP never delivered) makes the next press LONG — the service should reset the decision on disconnect / when a DOWN arrives more than ~5 s after the pending one (ruling: Task 4 may add a staleness reset in the service layer, not in HomeKeyDecision's tested contract).
- Task 3: minor (deferred): redundant `settings delete secure accessibility_enabled` before the put 0; component written fully qualified (functionally same); usage text `remote short|long|idle-set` ambiguous; homekey-status does not check settings-get exit; bare `grep HomeKeyService` could match an enabled-but-unbound service.
- Task 4: minor (deferred): staleness guard should also require event.repeatCount == 0; serviceInfo null guard; long press consumed even when performGlobalAction returns false (spec wording); extra notificationTimeout="0" in XML; docs: force-stop drops the service from the enabled list.
- Task 5: minor (deferred): the Settings row value is read once per Settings open (spec's `remember`), so it stays "Off" after returning from the Accessibility page until Settings is reopened; reader calls not wrapped in runCatching.
- Task 6: fix round 1/5 (1 addressed — README settings list now names the Home button row, verified by the controller in commit 57fc954 (docs-only, one line); 0 open; commits fe0efba..57fc954). Ruling: scoped re-review skipped for a one-line README addition verified by the controller directly — the whole-branch final review covers README anyway. Costs if wrong: none beyond a doc line.
- Task 6: minor (deferred): report's 11:54 press sequence does not say which app each press started from; per-press physical shots not taken; dev= id not logged by the service.
- Final review (opus, 5b1becd..57fc954): With fixes. Important 1 (Settings row stale after returning from the Accessibility page) → fix wave. Important 2 (replay owed indefinitely until next Home press) — Ruling: keep as is — the takeover spec §3 "which wake counts" (author's call, approved by the user) says the replay fires on the first arrival at our Home after a qualifying standby whichever app was in front at wake; the review's scenario is exactly that rule. Costs if wrong: a Welcome the user did not want after waking into another app; one-line freshness window to add later.
- Ruling: work in place on branch `v1` (no worktree) — v1 is the project's dev branch (main is behind it, user has not asked to merge), and the device loop `tools/tv.sh` installs from this checkout — costs nothing unless the user wanted main untouched, which it is.
- Task 1: minor (deferred): StandbyTracker.register has no double-registration guard (call once from Application); timestamps plain vars (main thread both sides); onReceive param shadows outer context.
- Task 3: Ruling: reviewer Critical #1 (Welcome gate can be left with no coroutine when the activity stops mid-gate and a replay re-targets Welcome; brief Step 4 said "confirm no code change is needed") — finding stands, brief text overruled: the spec's anti-goal forbids a stuck screen, and after takeover there is no escape from Welcome. Fix = replay-generation counter keying the gate. Costs if wrong: an unnecessary counter.
- Task 3: minor (deferred): duplicate "onStart replay=" log; Log import order + inline PowerManager FQN; no test that onInteractiveStart does not overwrite a real onScreenOn.
- Task 4: minor (deferred): openStockHomeDetails catches only ActivityNotFoundException (perform() also catches SecurityException); no Log.i line; helper Text has an 8 dpx start padding not in the brief.
- Task 5: implementer DONE_WITH_CONCERNS at b8846e2 — Settings-UI half of the idle-set mapping check not done (Google TV's screensaver page shows Start now / Settings; "When to start" not found); mapping proven via idle-set 5 → 300000, idle-set 10 → 600000. Ruling: accept — the value is the documented AOSP key and the user can confirm the TV-Settings label later; costs a wrong README sentence if Google TV's screensaver timer reads a different key.
- Task 5: Ruling: Important #1 (home-take disables launcherx even if set-home-activity failed) and #2 (plan's literal `grep -c launcherx` unanchored) — both findings stand over the brief text; the spec's anti-goal (TV never without a home) is binding. Costs if wrong: a stricter script. Minor octal `08` bug fixed in the same round.
- Task 5: minor (deferred → Task 6 pre-step): home_status treats adb error text as "0" (2>&1 capture) and legitimately empty `pm list packages -d` as "unknown" (would make home-restore exit 1 after success). Ruling: Task 6 implementer applies the one-line fix (2>/dev/null + exit status; empty = 0) before the switch-over, since Task 6 exercises both paths. Costs if wrong: a stray exit code.
