package com.nickesh.tvlauncher.domain.settings

import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.data.settings.WorldClock
import com.nickesh.tvlauncher.data.weather.GeoMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockEditTest {
    private val m = GeoMatch("Lisbon", "Lisboa", "Portugal", 38.72, -9.14, "Europe/Lisbon")
    @Test fun applyMatch() { val c = ClockEdit.applyMatch(WorldClock("Tokyo", "Asia/Tokyo", 35.0, 139.0), m); assertEquals(WorldClock("Lisbon", "Europe/Lisbon", 38.72, -9.14), c) }
    @Test fun withZoneKeepsCoordinates() { assertEquals(WorldClock("Lisbon", "Atlantic/Azores", 38.72, -9.14), ClockEdit.withZone(WorldClock("Lisbon", "Europe/Lisbon", 38.72, -9.14), "Atlantic/Azores")) }
    @Test fun typedQueryIsNotApplied() {
        val names = ClockEdit::class.java.declaredMethods
            .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }.toSet()
        assertEquals(setOf("applyMatch", "applyHomeMatch", "withZone"), names)
    }
    @Test fun applyHomeMatch() { val s = ClockEdit.applyHomeMatch(Settings(), m); assertEquals("Lisbon", s.homeCity); assertEquals("Europe/Lisbon", s.homeZone); assertEquals(38.72, s.homeLat!!, 1e-9) }
}
