package com.nickesh.tvlauncher.domain.home

import com.nickesh.tvlauncher.Screen

/** Maps the HOME intent onto the screen the launcher should show. */
object HomeKey {
    /** Screen to show when the HOME intent arrives, or null for no change. */
    fun next(current: Screen): Screen? = when (current) {
        Screen.Home, Screen.Welcome -> null
        is Screen.AllApps, Screen.Settings, Screen.Background -> Screen.Home
    }
}
