# Agenda Home: device report (2026-11-01)

Branch `v2-agenda` (off `v1` at 4ada414). Spec `docs/superpowers/specs/2026-11-01-agenda-home-design.md`, plan `docs/superpowers/plans/2026-11-01-agenda-home.md`. Built subagent-driven: 11 implementation tasks, each with a task review and fix rounds, then a whole-branch review and one fix wave. 258 unit tests, lint 0 errors.

## Proven on the Chromecast (screenshots in `shots/`)

| Proof | Screenshot |
|---|---|
| Layout G before connecting: both cards "Connect in Settings", permission tile, new forecast columns | `agenda-layout-empty.png` |
| "Read TV listings" system dialog | `agenda-permission.png` |
| Continue watching with Prime Video posters, time left, progress; Select resumed Prime | `agenda-watching.png` |
| Focus lands in the row after Allow / on the off line after Deny | `agenda-grant-focus.png`, `agenda-deny-focus.png` |
| Connect screen with QR + address; wrong code 404; bad link rejected on phone and TV | `agenda-connect.png`, `agenda-connect-failure.png` |
| Token typed on the TV: dots + password keyboard; draft cleared on close | `agenda-connect-token-password-kb.png`, `agenda-connect-token-reopened-empty.png` |
| Server refused while the TV sleeps; new address on wake | `agenda-connect-before-sleep.png`, `agenda-connect-after-wake.png` |
| Settings → Connections rows | `agenda-settings-connections.png` |
| **User walk with real credentials:** two Google calendars + Todoist connected from the phone; "Nothing scheduled" was correct (no upcoming events in those calendars, checked against the feeds); a test event then showed as "Tomorrow 20:00 · Test" untruncated; `Tv test` ticked, undone and ticked again by the user, completed in Todoist; a new phone task appeared after refresh | `agenda-home-full.png` |

Not proven on device: the Settings "Allow…" path after a permanent denial (the permission is granted on this TV), Disconnect with real connections, and the visual check of an "overdue" task.

## Rulings that changed the spec's wording

- **Deferred close.** A tick sends "complete" to Todoist only when its 5-second undo window ends; undo inside the window never touches Todoist. Same on screen; avoids recurring tasks being pushed to their next date by an undo and a tick/undo/re-tick race. Cost: a failure toast arrives ~5 s after the tick, and a tick is lost if the app dies inside the window.
- Nearest-minute rounding for "time left"; LOADING state (empty card body) instead of flashing "Can't reach…" on cold start; Watch Next includes type NEW so a finished episode's next one stays in the row.

All rulings and deferred minors are in the SDD ledger for this plan (git-ignored workspace).
