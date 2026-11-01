package com.nickesh.tvlauncher.data.calendar

import com.nickesh.tvlauncher.domain.connect.CalendarOwner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsParserTest {
    private val ny = ZoneId.of("America/New_York")
    private val winStart = LocalDate.of(2026, 11, 1).atStartOfDay(ny).toInstant().toEpochMilli()
    private val winEnd = LocalDate.of(2026, 12, 31).atStartOfDay(ny).toInstant().toEpochMilli()

    private fun ms(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0): Long =
        ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), ny).toInstant().toEpochMilli()

    private fun parse(file: String, owner: String? = null): List<CalendarEvent> =
        IcsParser.parse(
            javaClass.getResource("/ics/$file")!!.readText(),
            winStart, winEnd, ny, owner, "Nickesh",
        )

    private fun starts(file: String) = parse(file).map { it.startMs }

    @Test fun timedEvent_isParsed() {
        val e = parse("plain.ics").single()
        assertEquals("Dentist", e.title)
        assertEquals("dentist@x", e.uid)
        assertEquals(ms(2026, 11, 3, 15, 30), e.startMs)
        assertEquals(ms(2026, 11, 3, 16, 0), e.endMs)
        assertEquals(false, e.allDay)
        assertEquals("Nickesh", e.calendar)
    }

    @Test fun allDay_usesZoneMidnight() {
        val e = parse("allday.ics").single()
        assertEquals(ms(2026, 11, 5), e.startMs)
        assertEquals(ms(2026, 11, 6), e.endMs)
        assertTrue(e.allDay)
    }

    @Test fun weeklyRecurrence_expands_andHonoursExdate() {
        val s = starts("weekly.ics")
        assertEquals(
            listOf(
                ms(2026, 11, 2, 10), ms(2026, 11, 16, 10), ms(2026, 11, 23, 10),
                ms(2026, 11, 30, 10), ms(2026, 12, 7, 10), ms(2026, 12, 14, 10),
                ms(2026, 12, 21, 10), ms(2026, 12, 28, 10),
            ),
            s,
        )
    }

    @Test fun movedInstance_replacesMasterOccurrence() {
        val s = starts("moved.ics")
        assertTrue(ms(2026, 11, 16, 10) !in s)
        assertEquals(1, s.count { it == ms(2026, 11, 17, 10) })
        assertTrue(ms(2026, 11, 9, 10) in s)
        assertTrue(ms(2026, 11, 23, 10) in s)
    }

    @Test fun cancelled_isDropped() {
        assertEquals(listOf("Kept"), parse("cancelled.ics").map { it.title })
    }

    @Test fun declinedByOwner_isDropped_othersKept() {
        assertEquals(listOf("ColleagueDeclined"), parse("declined.ics", "nick@gmail.com").map { it.title })
    }

    @Test fun noOwner_keepsDeclined() {
        assertEquals(2, parse("declined.ics", null).size)
    }

    @Test fun dstCrossover_keepsWallClock() {
        val s = starts("dst.ics")
        // window starts Nov 1, so the Oct 26 occurrence (13:00Z) is outside it; assert Nov 2 and Nov 9.
        assertEquals(java.time.Instant.parse("2026-11-02T14:00:00Z").toEpochMilli(), s[0])
        assertEquals(java.time.Instant.parse("2026-11-09T14:00:00Z").toEpochMilli(), s[1])
        val wide = IcsParser.parse(
            javaClass.getResource("/ics/dst.ics")!!.readText(),
            ms(2026, 10, 25), winEnd, ny, null, "Nickesh",
        )
        assertEquals(java.time.Instant.parse("2026-10-26T13:00:00Z").toEpochMilli(), wide[0].startMs)
    }

    @Test fun untilAndCount_stopExpansion() {
        assertEquals(
            listOf(ms(2026, 11, 2, 9), ms(2026, 11, 9, 9), ms(2026, 11, 16, 9)),
            starts("count.ics"),
        )
        assertEquals(
            listOf(ms(2026, 11, 2, 9), ms(2026, 11, 9, 9)),
            starts("until.ics"),
        )
    }

    @Test fun missingDtend_timed_isZeroLength() {
        val e = parse("timed-noend.ics").single()
        assertEquals(e.startMs, e.endMs)
    }

    @Test fun missingDtend_allDay_isOneDay() {
        val e = parse("allday-noend.ics").single()
        assertEquals(ms(2026, 11, 6), e.endMs)
    }

    @Test fun malformedEvent_isSkipped_feedStillParses() {
        assertEquals(listOf("Good"), parse("malformed.ics").map { it.title })
    }

    @Test fun garbageFeed_returnsEmpty() {
        assertEquals(emptyList<CalendarEvent>(), IcsParser.parse("not a calendar", winStart, winEnd, ny, null, "x"))
    }

    @Test fun calendarName_fromXWrCalname() {
        assertEquals("Nickesh", IcsParser.calendarName(javaClass.getResource("/ics/plain.ics")!!.readText()))
    }

    @Test fun ownerEmail_fromGooglePrivateUrl() = assertEquals(
        "nick@gmail.com",
        CalendarOwner.emailFromUrl("https://calendar.google.com/calendar/ical/nick%40gmail.com/private-abc/basic.ics"),
    )

    @Test fun ownerEmail_nullForGroupAndForeign() {
        assertNull(CalendarOwner.emailFromUrl("https://calendar.google.com/calendar/ical/abc%40group.calendar.google.com/private-x/basic.ics"))
        assertNull(CalendarOwner.emailFromUrl("https://example.com/x.ics"))
    }
}
