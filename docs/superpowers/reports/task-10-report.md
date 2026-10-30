# Task 10 report (transcribed by the controller from the implementer's hand-back; the implementer did not write this file)

Status: BLOCKED on device verification only. Code implemented and committed as 5c0dc7f "feat: background and settings screens with persisted choices".

Built and tested: compileDebugKotlin clean; full testDebugUnitTest passes; `tools/tv.sh install` succeeded ("Installed on 1 device"); then the TV's adb went offline (wireless debugging off), so NO device checks ran: no task10-* screenshots, Welcome-name check, focus/back behaviour, left/right consumption, inline text fields with IME, Photos row focusability, or cold-start persistence. Settings were not changed on the device (still defaults).

Implemented:
- ui/settings/BackgroundScreen.kt (new): header, SOURCE rows (Photos 50 % alpha, "Coming soon", canFocus = false), interval chips, Shuffle switch, scene previews (real Background composables, clipped) or the four colour circles.
- ui/settings/SettingsScreen.kt (new) + ui/settings/SettingsRows.kt (new, shared row code): every row on the list, left/right via onPreviewKeyEvent.
- MainActivity.kt: Settings and Background branches with BackHandlers; writes via lifecycleScope.launch { container.settings.update }.
- HomeViewModel.kt: shuffle loop keyed on (enabled, minutes) only, under collectLatest.

Implementer concerns: rows use plain Surface rather than tv-material ListItem; BackgroundScreen radio dot is a nested non-focusable Pick (clumsy); text fields are BasicTextField inside the row (commit on IME Done, Back cancels, empty ignored) — whether OK opens the keyboard on the real TV is untested; scenes are chosen on OK, not on focus (mockup picks on focus).

## Fix report (review findings)
1. CHANGE PHOTO chips now in a FlowRow (12 gaps, 24 horizontal padding).
2. Initial focus: Settings requests focus on the Name row (or the "Background..." row when returning from Background, via a rememberSaveable `cameFromBackground` flag in MainActivity); Background focuses the selected source row. All wrapped in runCatching.
3. Zone cycling (home and clocks) and accent cycling now compute the next value inside `onUpdate { it.copy(...) }` from the latest settings.
Minors: text editing cancels (draft discarded) when the field loses focus after having had it; the radio dot is now a plain Box ring + dot.
Verification: full `testDebugUnitTest` green; TV still off adb, so no device checks run.

## Fix report 2
- `Modifier.focusRequester(bgRow)` now attached to the "Background..." SettingRow (it was unattached).
- TextSettingRow takes a `rowRequester` (Name row passes `firstRow`); the row surface carries it, and after Done/Back ends editing (not when focus moved away) focus is returned to the row via runCatching.
- Command: `JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/jdk-21.0.12.1+1/Contents/Home ./gradlew testDebugUnitTest` -> last lines: `> Task :app:testDebugUnitTest` / `BUILD SUCCESSFUL in 915ms` / `24 actionable tasks: 5 executed, 19 up-to-date`.
- `adb devices` lists no device; Step 4 device checks remain pending.
