package com.nickesh.tvlauncher.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherByPlaceTest {
    private val tokyo = Place("clock0", "Tokyo", "Tokyo", 35.0, 139.0)
    private val lisbon = Place("clock0", "Lisbon", "Lisbon", 38.72, -9.14)

    @Test fun samePlace_returnsValue() {
        val entries = mapOf("clock0" to PlaceWeather(tokyo, "21°"))
        assertEquals("21°", WeatherByPlace.forCurrent(entries, tokyo))
    }

    @Test fun rePickedPlace_dropsTheOldValue() {
        // The loop for clock0 has not reloaded yet: the entry still belongs to Tokyo.
        val entries = mapOf("clock0" to PlaceWeather(tokyo, "21°"))
        assertNull(WeatherByPlace.forCurrent(entries, lisbon))
        // Same name, moved coordinates: still a different place.
        assertNull(WeatherByPlace.forCurrent(entries, tokyo.copy(lat = 35.7)))
        // Home re-pick: a new legacy-free query is a different place too.
        val home = Place(Places.HOME, "Columbus", "Columbus, OH", null, null)
        val homeEntries = mapOf(Places.HOME to PlaceWeather(home, "64°"))
        assertNull(WeatherByPlace.forCurrent(homeEntries, home.copy(name = "Denver", queryName = "Denver")))
    }

    @Test fun missingKey_isNull() {
        assertNull(WeatherByPlace.forCurrent(emptyMap<String, PlaceWeather<String>>(), tokyo))
        assertNull(WeatherByPlace.forCurrent(mapOf("clock0" to PlaceWeather(tokyo, "21°")), tokyo.copy(key = "clock1")))
    }
}
