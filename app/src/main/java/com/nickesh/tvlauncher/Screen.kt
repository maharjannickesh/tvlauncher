package com.nickesh.tvlauncher

import androidx.compose.runtime.saveable.Saver

sealed interface Screen {
    data object Welcome : Screen
    data object Home : Screen
    data object AllApps : Screen
    data object Settings : Screen
    data object Background : Screen
}

internal val ScreenSaver = Saver<Screen, String>(
    save = { it.toString() },
    restore = {
        when (it) {
            "Welcome" -> Screen.Welcome
            "AllApps" -> Screen.AllApps
            "Settings" -> Screen.Settings
            "Background" -> Screen.Background
            else -> Screen.Home
        }
    },
)
