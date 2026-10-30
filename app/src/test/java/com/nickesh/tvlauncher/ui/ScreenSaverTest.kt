package com.nickesh.tvlauncher.ui

import androidx.compose.runtime.saveable.SaverScope
import com.nickesh.tvlauncher.Screen
import com.nickesh.tvlauncher.ScreenSaver
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenSaverTest {
    private val scope = SaverScope { true }

    private fun roundTrip(screen: Screen): Screen? =
        with(ScreenSaver) { scope.save(screen) }?.let { ScreenSaver.restore(it) }

    @Test fun allAppsKeepsTheFocusSearchFlag() {
        assertEquals(Screen.AllApps(true), roundTrip(Screen.AllApps(true)))
        assertEquals(Screen.AllApps(false), roundTrip(Screen.AllApps(false)))
    }

    @Test fun otherScreensSurvive() {
        listOf(Screen.Welcome, Screen.Home, Screen.Settings, Screen.Background).forEach {
            assertEquals(it, roundTrip(it))
        }
    }
}
