# Task 5 report: Home screen layout

Implemented per brief: Icons.kt (all mockup SVGs as ImageVectors; extras SunForecast/PartlyCard for the differing stroke widths/fill; Back/Photo are generic placeholders), RoundButton.kt (+ `LocalAccent`, `focusGlow`), HomeViewModel.kt (state types, placeholderWeather/placeholderApps, ticker), HeroClock, TopBar (assist pill + round buttons), WeatherCard, WorldClockCard (Canvas dial), AppsRow (initial focus, letter fallback kept, All apps tile, gap 22), HomeScreen. MainActivity: Home branch -> HomeScreen, ScreenSaver for rememberSaveable `screen`, viewModel{HomeViewModel(container)}; onLaunch/onAction no-ops.

Verification: assembleDebug OK; testDebugUnitTest green; installed on TV. shots/task5-home.png (full layout, first tile focused, 1.1x ring+glow) and shots/task5-focus.png (after 2x D-pad right: Prime Video focused, label at 100%).
Deviations fixed during screenshot pass: clock-card meta line was clipped (Column now wrapContentHeight unbounded + explicit line heights); hero baseline sat ~38 px too high relative to the date line (Row offset y=38 design px).
Concerns: Screen.AllApps/Settings/Background still render HomeScreen (no destination yet, no back handling) - Tasks 8+ replace. Hero y-offset is empirical.
