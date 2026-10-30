package com.nickesh.tvlauncher.domain.clock

import com.nickesh.tvlauncher.data.settings.WorldClock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockMathTest {
    private val newYork = ZoneId.of("America/New_York")
    private val homeNy = ZonedDateTime.parse("2026-10-29T21:00:00-04:00[America/New_York]")

    private fun other(home: ZonedDateTime, zone: String) =
        home.toInstant().atZone(ZoneId.of(zone))

    @Test fun kathmandu_from_newYork_isQuarterHour() {
        assertEquals("Tomorrow  ·  +9h 45m", ClockMath.offsetMeta(homeNy, other(homeNy, "Asia/Kathmandu")))
    }

    @Test fun tokyo_from_newYork_isTomorrow() {
        assertEquals("Tomorrow  ·  +13h", ClockMath.offsetMeta(homeNy, other(homeNy, "Asia/Tokyo")))
    }

    @Test fun newYork_from_tokyo_isYesterday() {
        val home = ZonedDateTime.parse("2026-10-30T03:00:00+09:00[Asia/Tokyo]")
        assertEquals("Yesterday  ·  −13h", ClockMath.offsetMeta(home, other(home, "America/New_York")))
    }

    @Test fun sameZone_isSameTime() {
        assertEquals("Today  ·  Same time", ClockMath.offsetMeta(homeNy, other(homeNy, "America/New_York")))
    }

    @Test fun midnight_12h() {
        val t = ZonedDateTime.of(2026, 10, 29, 0, 5, 0, 0, newYork)
        assertEquals(TimeText("12:05", "AM"), ClockMath.timeText(t, false))
    }

    @Test fun noon_12h() {
        val t = ZonedDateTime.of(2026, 10, 29, 12, 5, 0, 0, newYork)
        assertEquals(TimeText("12:05", "PM"), ClockMath.timeText(t, false))
    }

    @Test fun midnight_24h() {
        val t = ZonedDateTime.of(2026, 10, 29, 0, 5, 0, 0, newYork)
        assertEquals(TimeText("00:05", ""), ClockMath.timeText(t, true))
    }

    @Test fun morning_12h_hasNoLeadingZero() {
        val t = ZonedDateTime.of(2026, 10, 29, 9, 5, 0, 0, newYork)
        assertEquals(TimeText("9:05", "AM"), ClockMath.timeText(t, false))
    }

    @Test fun greeting_boundaries() {
        assertEquals("Good Night", ClockMath.greeting(4, ""))
        assertEquals("Good Morning", ClockMath.greeting(5, ""))
        assertEquals("Good Morning", ClockMath.greeting(11, ""))
        assertEquals("Good Afternoon", ClockMath.greeting(12, ""))
        assertEquals("Good Afternoon", ClockMath.greeting(16, ""))
        assertEquals("Good Evening", ClockMath.greeting(17, ""))
        assertEquals("Good Evening", ClockMath.greeting(23, ""))
    }

    @Test fun greeting_titleCaseWithName() {
        assertEquals("Good Afternoon, Nickesh", ClockMath.greeting(14, "Nickesh")); assertEquals("Good Morning, Nickesh", ClockMath.greeting(9, "Nickesh"))
        assertEquals("Good Evening, Nickesh", ClockMath.greeting(20, "Nickesh")); assertEquals("Good Night, Nickesh", ClockMath.greeting(3, "Nickesh"))
    }
    @Test fun greeting_blankName_noComma() { assertEquals("Good Afternoon", ClockMath.greeting(14, "  ")) }

    @Test fun dateLine_format() {
        assertEquals("Thursday, October 29", ClockMath.dateLine(homeNy))
    }

    @Test fun dialAngles_at_3_30_15() {
        val t = ZonedDateTime.of(2026, 10, 29, 3, 30, 15, 0, newYork)
        val (h, m, s) = ClockMath.dialAngles(t)
        assertEquals(105f, h, 0.001f)
        assertEquals(181.5f, m, 0.001f)
        assertEquals(90f, s, 0.001f)
    }

    @Test fun isDay_bounds() {
        assertFalse(ClockMath.isDay(5))
        assertTrue(ClockMath.isDay(6))
        assertTrue(ClockMath.isDay(17))
        assertFalse(ClockMath.isDay(18))
    }

    @Test fun snapshot_composesFields() {
        val now = Instant.parse("2026-10-30T01:00:00Z") // 21:00 NY, 06:45 Kathmandu
        val home = now.atZone(newYork)
        val s = ClockMath.snapshot(WorldClock("Kathmandu", "Asia/Kathmandu"), home, now, false)
        assertEquals("Kathmandu", s.city)
        assertEquals(TimeText("6:45", "AM"), s.text)
        assertEquals("Tomorrow  ·  +9h 45m", s.meta)
        assertTrue(s.isDay)
    }
}
