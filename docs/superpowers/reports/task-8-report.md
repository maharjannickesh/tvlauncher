# Task 8 report
Implemented data/system/SystemIntents.kt (candidates/isAvailable/perform, logs chosen action via Log.i "SystemIntents"), AppContainer.systemIntents, MainActivity onAction + Toast "Not available on this TV" + showInputs (remember), HomeScreen/TopBar showInputs pass-through (Inputs button hidden when unavailable), manifest <queries> for 8 actions.
Verification: assembleDebug + testDebugUnitTest green. Device: DEFERRED, TV unreachable (adb devices empty, tools/tv.sh connect failed). Resolved candidates per action not recorded.
Tests: skipped (Intent is an Android class).
Files: SystemIntents.kt, AppContainer.kt, MainActivity.kt, HomeScreen.kt, TopBar.kt, AndroidManifest.xml.
Concern: isAvailable(INPUTS) uses resolveActivity; on-device behavior unverified.
