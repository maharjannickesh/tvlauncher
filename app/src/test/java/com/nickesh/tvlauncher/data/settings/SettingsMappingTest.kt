package com.nickesh.tvlauncher.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nickesh.tvlauncher.domain.settings.ZoneCatalog
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
    fun zoneCatalog_offersEveryDefaultAndFormerShortlistZone() {
        // The full catalog replaced the 19-entry shortlist: nothing that list offered may disappear.
        val former = listOf(
            "Pacific/Honolulu", "America/Los_Angeles", "America/Denver", "America/Chicago",
            "America/New_York", "America/Sao_Paulo", "UTC", "Europe/London", "Europe/Paris",
            "Europe/Berlin", "Africa/Cairo", "Asia/Dubai", "Asia/Kolkata", "Asia/Kathmandu",
            "Asia/Singapore", "Asia/Shanghai", "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland",
        )
        val d = Settings()
        for (z in former + d.homeZone + d.clocks.map { it.zoneId }) {
            assertTrue(z, z in ZoneCatalog.zones(ZoneCatalog.regionOf(z)))
        }
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

    @Test fun applySettings_removesClearedCoordinates_andOverwritesPresent() {
        val prefs = Settings(homeLat = 1.0, homeLon = 2.0, clocks = List(3) { WorldClock("P", "UTC", 3.0, 4.0) }).toPreferences().toMutablePreferences()
        prefs.applySettings(Settings(homeLat = null, homeLon = 5.0, clocks = List(3) { WorldClock("P", "UTC", if (it == 0) null else 6.0, 4.0) }))
        val back = prefs.toSettings()
        assertNull(prefs[doublePreferencesKey("home_lat")]); assertNull(back.homeLat); assertEquals(5.0, back.homeLon!!, 1e-9)
        assertNull(prefs[doublePreferencesKey("clock0_lat")]); assertNull(back.clocks[0].lat); assertEquals(6.0, back.clocks[1].lat!!, 1e-9)
    }
}
