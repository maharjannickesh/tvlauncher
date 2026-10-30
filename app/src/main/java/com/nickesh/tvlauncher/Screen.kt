package com.nickesh.tvlauncher

import androidx.compose.runtime.saveable.Saver

sealed interface Screen {
    data object Welcome : Screen
    data object Home : Screen
    data class AllApps(val focusSearch: Boolean = false) : Screen
    data object Settings : Screen
    data object Background : Screen
}

internal val ScreenSaver = Saver<Screen, String>(
    save = { if (it is Screen.AllApps) (if (it.focusSearch) "AllApps:1" else "AllApps") else it.toString() },
    restore = {
        when (it) {
            "Welcome" -> Screen.Welcome
            "AllApps" -> Screen.AllApps()
            "AllApps:1" -> Screen.AllApps(focusSearch = true)
            "Settings" -> Screen.Settings
            "Background" -> Screen.Background
            else -> Screen.Home
        }
    },
)
