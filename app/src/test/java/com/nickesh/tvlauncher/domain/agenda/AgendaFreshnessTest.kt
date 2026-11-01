package com.nickesh.tvlauncher.domain.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaFreshnessTest {
    private val now = 1_000_000_000L

    @Test fun dueOnResume_whenNeverFetched() = assertTrue(AgendaFreshness.dueOnResume(null, now))

    @Test fun dueOnResume_whenOlderThanFiveMinutes() =
        assertTrue(AgendaFreshness.dueOnResume(now - 6 * 60_000L, now))

    @Test fun notDueOnResume_whenFetchedAMinuteAgo() =
        assertFalse(AgendaFreshness.dueOnResume(now - 60_000L, now))

    @Test fun sleep_isPeriodWhenOk_ninetySecondsOtherwise() {
        assertEquals(AgendaFreshness.CALENDAR_MS, AgendaFreshness.sleepMs(AgendaFreshness.CALENDAR_MS, true))
        assertEquals(90_000L, AgendaFreshness.sleepMs(AgendaFreshness.CALENDAR_MS, false))
        assertEquals(90_000L, AgendaFreshness.sleepMs(AgendaFreshness.TODO_MS, false))
    }

    @Test fun periods() {
        assertEquals(15 * 60_000L, AgendaFreshness.CALENDAR_MS)
        assertEquals(5 * 60_000L, AgendaFreshness.TODO_MS)
        assertEquals(5 * 60_000L, AgendaFreshness.STALE_MS)
    }
}
