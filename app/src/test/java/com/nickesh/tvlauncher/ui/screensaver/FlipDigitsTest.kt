package com.nickesh.tvlauncher.ui.screensaver

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import org.junit.Assert.assertEquals

class FlipDigitsTest {
    private val zone = ZoneId.of("UTC")
    private fun at(h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 10, 29, h, m, s, 0, zone)

    @Test fun twelveHour_9_41() {
        assertEquals(FlipDigits(listOf("09", "41"), "AM"), FlipDigitsCalc.from(at(9, 41), use24h = false, showSeconds = false))
    }

    @Test fun twentyFourHour_midnight() {
        assertEquals(FlipDigits(listOf("00", "05"), ""), FlipDigitsCalc.from(at(0, 5), use24h = true, showSeconds = false))
    }

    @Test fun withSeconds() {
        val d = FlipDigitsCalc.from(at(9, 41, 7), use24h = false, showSeconds = true)
        assertEquals(listOf("09", "41", "07"), d.groups)
    }

    @Test fun noon_12h() {
        assertEquals(FlipDigits(listOf("12", "05"), "PM"), FlipDigitsCalc.from(at(12, 5), use24h = false, showSeconds = false))
    }
}
