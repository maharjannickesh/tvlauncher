package com.nickesh.tvlauncher.domain.agenda

import com.nickesh.tvlauncher.data.calendar.CalendarEvent
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpNextTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = ms(2026, 11, 1, 14, 44)

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun ev(
        title: String,
        start: Long,
        end: Long = start + 3_600_000,
        allDay: Boolean = false,
    ) = CalendarEvent(title, title, start, end, allDay, "Cal")

    private fun allDay(title: String, y: Int, mo: Int, d: Int, days: Int = 1) =
        ev(title, ms(y, mo, d, 0, 0), ms(y, mo, d, 0, 0) + 0, true).copy(
            endMs = LocalDateTime.of(y, mo, d, 0, 0).plusDays(days.toLong()).atZone(zone).toInstant().toEpochMilli(),
        )

    @Test fun select_dropsEnded_sortsByStart_limitsToThree() {
        val ended = ev("ended", ms(2026, 11, 1, 9, 0))
        val a = ev("a", ms(2026, 11, 1, 16, 0))
        val b = ev("b", ms(2026, 11, 1, 15, 0))
        val c = ev("c", ms(2026, 11, 1, 15, 0))
        val d = ev("d", ms(2026, 11, 2, 9, 0))
        val e = ev("e", ms(2026, 11, 3, 9, 0))
        val out = UpNext.select(listOf(e, d, c, ended, a, b), now)
        assertEquals(listOf("b", "c", "a"), out.map { it.title })
        assertEquals(4, UpNext.select(listOf(e, d, c, ended, a, b), now, limit = 4).size)
    }

    @Test fun multiDayAllDay_spanningToday_isIncluded_asAllDay() {
        val e = allDay("trip", 2026, 10, 30, days = 4)
        assertEquals(listOf(e), UpNext.select(listOf(e), now))
        assertEquals("All day", UpNext.timeColumn(e, now, zone, false))
    }

    @Test fun timeColumn_today_12h() =
        assertEquals("3:30 PM", UpNext.timeColumn(ev("x", ms(2026, 11, 1, 15, 30)), now, zone, false))

    @Test fun timeColumn_today_24h() =
        assertEquals("15:30", UpNext.timeColumn(ev("x", ms(2026, 11, 1, 15, 30)), now, zone, true))

    @Test fun timeColumn_tomorrow() =
        assertEquals("Tomorrow 10:00 AM", UpNext.timeColumn(ev("x", ms(2026, 11, 2, 10, 0)), now, zone, false))

    @Test fun timeColumn_withinWeek() =
        assertEquals("Sat 10:00 AM", UpNext.timeColumn(ev("x", ms(2026, 11, 7, 10, 0)), now, zone, false))

    @Test fun timeColumn_later() =
        assertEquals("12 Nov", UpNext.timeColumn(ev("x", ms(2026, 11, 12, 10, 0)), now, zone, false))

    @Test fun timeColumn_allDayVariants() {
        assertEquals("All day", UpNext.timeColumn(allDay("x", 2026, 11, 1), now, zone, false))
        assertEquals("Tomorrow · All day", UpNext.timeColumn(allDay("x", 2026, 11, 2), now, zone, false))
        assertEquals("Sat · All day", UpNext.timeColumn(allDay("x", 2026, 11, 7), now, zone, false))
        assertEquals("12 Nov · All day", UpNext.timeColumn(allDay("x", 2026, 11, 12), now, zone, false))
    }

    @Test fun suffix_minutes_hours_null_now() {
        assertEquals("in 46 min", UpNext.suffix(ev("x", ms(2026, 11, 1, 15, 30)), now))
        assertEquals("in 3 h", UpNext.suffix(ev("x", ms(2026, 11, 1, 18, 0)), now))
        assertNull(UpNext.suffix(ev("x", ms(2026, 11, 3, 9, 0)), now))
        assertEquals("now", UpNext.suffix(ev("x", ms(2026, 11, 1, 14, 0), ms(2026, 11, 1, 15, 0)), now))
        assertNull(UpNext.suffix(allDay("x", 2026, 11, 1), now))
    }

    @Test fun rows_onlyFirstHasSuffix() {
        val rows = UpNext.rows(
            listOf(ev("b", ms(2026, 11, 1, 18, 0)), ev("a", ms(2026, 11, 1, 15, 30))),
            now, zone, false,
        )
        assertEquals(
            listOf(UpNextRow("3:30 PM", "a", "in 46 min"), UpNextRow("6:00 PM", "b", null)),
            rows,
        )
    }
}
