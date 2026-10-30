# Task 11 report

Repo: https://github.com/maharjannickesh/tvlauncher (PRIVATE), branch v1, pushed HEAD bb48882 (`git log origin/v1 --oneline -1` = bb48882). No PR created.

## Task 10 device checks (all on 10.0.0.21:33591)
- Settings via top-bar button: opens, focus on Name row (task10-settings): PASS.
- Down to "Background..." + OK: focus on selected source row (Built-in scenes), five CHANGE PHOTO chips fully visible (task10-background): PASS.
- Night scene -> Back -> Back: Settings shows Background... focused after Back; Home on Night (task10-night): PASS.
- 24-hour: hero "23:37", clock cards "09:22/03:37/12:37", no AM/PM anywhere (task10-24h): PASS.
- Name -> OK opens the TV keyboard (IME shown, screenshot with Gboard); cleared, typed Nick, Enter -> Done; cold start shows "Welcome Nick!!!" (task10-name): PASS.
- Defect found and fixed (commit 9ce5180): while editing, the text field's value sat at the field's left edge (x~520) instead of where the read-only value is drawn; now textAlign End.
- Restored: name Nickesh, 12-hour, scene Dusk (verified by screenshot).

## Focus fixes (a514a10)
- Up from Kathmandu and London now goes to the Ask pill (FocusRequester hoisted in HomeScreen, `focusProperties { up }` on WorldClockCard; Tokyo keeps default -> weather card).
- Left from the Ask pill cancelled (`FocusRequester.Cancel`).
- Verified: task11-focus-up (pill focused after Up from Kathmandu), task11-focus-left (pill still focused after Left), London Up checked (t11-london-up2).

## Mockup comparison (final-* screenshots: welcome, home, home-focus, allapps, settings, background, dream)
All screenshots opened. Measured geometry matches the mockup values (top bar 56, weather card 580 wide, clock cards 152 high, 44/40 gaps, 96/60 margins, glass fill/border, focus ring/scale). No deviation > 4 design px or colour/weight mismatch found, so no "polish deviations" commit was needed (the sun disc shows through the weather card since there is no backdrop blur: known, per global constraints). Could not render the mockup HTML side by side (Chrome tool refuses file:// URLs), so comparison was against the mockup source values, not pixel diffs.

## Cold start
`am start -W` TotalTime (debug, armv7): 4467, 4049 ms (polled runs saw first frame 3.1-4.4 s). Welcome holds >= 1.2 s after first frame + 400 ms crossfade, so Home settles ~5-6 s after launch. Screencap takes 1-2 s each on this device, so the 250 ms poll was not possible. The design's ~2 s goal is NOT met on the debug build; release build not measured. Recorded in README.

## Tests and lint
`:app:testDebugUnitTest lintDebug`: BUILD SUCCESSFUL; 47 tests, 0 failures/errors. Lint: 0 errors, 21 warnings, 1 hint: dependency/AGP/Gradle/Kotlin newer-version notices (14), VectorRaster on banner.xml, DiscouragedApi (fixed orientation), MissingApplicationIcon, UseKtx (AppsRepository:119), ModifierParameter x2 (Scenes.kt:79, TopBar.kt:43), ConfigurationScreenWidthHeight (Theme.kt:85).

## Commits
9ce5180 fix: right-align edit field; a514a10 fix: D-pad dead ends; bb48882 docs: README (carries lint/test summary). Files: ui/settings/SettingsRows.kt, ui/home/{HomeScreen,TopBar,WorldClockCard}.kt, README.md.

## Self-review / concerns
- Deleted screenshots that captured the user's Google account (final-allapps first attempt hit the system Accounts page; keyboard suggestion showed an email). shots/ and .superpowers/ are git-ignored; `git grep gmail` finds nothing in the repo.
- Cold-start target unmet on debug build (above).
- TV left in: Dusk, 12-hour, name Nickesh, screensaver component set (dream-set was re-run), Home showing.
- MissingApplicationIcon lint warning left (no launcher icon, only banner).
