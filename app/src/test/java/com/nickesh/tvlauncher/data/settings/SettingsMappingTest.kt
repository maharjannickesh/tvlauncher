package com.nickesh.tvlauncher.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertNull
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

    @Test fun clockCoordinates_roundTrip() {
        val s = Settings(clocks = listOf(WorldClock("Lisbon", "Europe/Lisbon", 38.72, -9.14), WorldClock("London", "Europe/London"), WorldClock("Tokyo", "Asia/Tokyo")), homeLat = 39.96, homeLon = -82.99)
        val back = s.toPreferences().toSettings()
        assertEquals(38.72, back.clocks[0].lat!!, 1e-9); assertEquals(-9.14, back.clocks[0].lon!!, 1e-9); assertNull(back.clocks[1].lat)
        assertEquals(39.96, back.homeLat!!, 1e-9)
    }

    @Test fun legacyWeatherCity_isReadNeverWritten() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("weather_city") to "Columbus, OH")
        assertEquals("Columbus, OH", prefs.toSettings().legacyWeatherCity)
        assertNull(Settings(legacyWeatherCity = "X").toPreferences()[stringPreferencesKey("weather_city")])
    }
}
