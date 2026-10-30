package com.nickesh.tvlauncher.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMappingTest {
    @Test
    fun defaults_roundTrip() {
        assertEquals(Settings(), Settings().toPreferences().toSettings())
    }

    @Test
    fun custom_roundTrip() {
        val custom = Settings(
            name = "Ada",
            use24h = true,
            unit = TempUnit.C,
            scene = Scene.NIGHT,
            changeInterval = ChangeInterval.NEVER,
            shuffle = false,
            screensaverDim = 0.6f,
            screensaverSeconds = true,
            favourites = listOf("a.b", "c.d"),
            clocks = List(3) { WorldClock("Paris", "Europe/Paris") },
        )
        assertEquals(custom, custom.toPreferences().toSettings())
    }

    @Test
    fun emptyPreferences_yieldsDefaults() {
        assertEquals(Settings(), emptyPreferences().toSettings())
    }

    @Test
    fun zoneOptions_has19_andContainsKathmandu() {
        assertEquals(19, ZONE_OPTIONS.size)
        assertTrue("Asia/Kathmandu" in ZONE_OPTIONS)
    }
}
