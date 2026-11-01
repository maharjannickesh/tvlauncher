package com.nickesh.tvlauncher.domain.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectRulesTest {
    @Test fun webcal_isNormalisedToHttps() = assertEquals("https://x/basic.ics", ConnectRules.normaliseCalendarUrl(" webcal://x/basic.ics\n"))
    @Test fun http_isRejected() = assertNull(ConnectRules.normaliseCalendarUrl("http://x/basic.ics"))
    @Test fun empty_isRejected() = assertNull(ConnectRules.normaliseCalendarUrl("   "))
    @Test fun add_empty_leavesUnchanged() = assertEquals(listOf("https://a"), ConnectRules.addCalendar(listOf("https://a"), "  ").getOrThrow())
    @Test fun add_duplicate_leavesUnchanged() = assertEquals(listOf("https://a"), ConnectRules.addCalendar(listOf("https://a"), "https://a").getOrThrow())
    @Test fun add_invalid_failsWithCopy() {
        assertEquals("That link didn't return a calendar", ConnectRules.addCalendar(emptyList(), "http://x").exceptionOrNull()!!.message)
    }
    @Test fun add_fifth_fails_withCopy() {
        val r = ConnectRules.addCalendar(List(4) { "https://c$it" }, "https://new")
        assertEquals("Up to 4 calendars", r.exceptionOrNull()!!.message)
    }
    @Test fun add_appends() = assertEquals(listOf("https://a", "https://b"), ConnectRules.addCalendar(listOf("https://a"), "https://b").getOrThrow())
    @Test fun remove_dropsUrl() = assertEquals(listOf("https://a"), ConnectRules.removeCalendar(listOf("https://a", "https://b"), "https://b"))
    @Test fun token_isTrimmed_andEmptyIsNull() { assertEquals("abc", ConnectRules.normaliseToken(" abc\n")); assertNull(ConnectRules.normaliseToken("  ")) }
    @Test fun maskToken_showsLastFour() = assertEquals("••••1a2b", ConnectRules.maskToken("abcdef1a2b"))
    @Test fun maskToken_short_isWhole() = assertEquals("abc", ConnectRules.maskToken("abc"))
}
