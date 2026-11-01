package com.nickesh.tvlauncher.data.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nickesh.tvlauncher.data.net.TextFetcher
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

private class FakeFetcher(val bodies: MutableMap<String, String> = mutableMapOf()) : TextFetcher {
    val failing = mutableSetOf<String>()
    override suspend fun get(url: String, bearer: String?): String {
        if (url in failing) throw IOException("offline")
        return bodies[url] ?: throw IOException("http 404")
    }
}

class CalendarRepositoryTest {
    private val now = 1_793_500_000_000L // 2026-11-01
    private val zone = ZoneId.of("UTC")
    private val urlA = "https://a.example.com/private-aaa/basic.ics"
    private val urlB = "https://b.example.com/private-bbb/basic.ics"

    private fun ics(name: String, vararg events: Pair<String, Long>): String = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//t//EN\r\nX-WR-CALNAME:$name\r\n")
        for ((uid, start) in events) {
            val f = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"))
            val s = f.format(java.time.Instant.ofEpochMilli(start))
            val e = f.format(java.time.Instant.ofEpochMilli(start + 3_600_000))
            append("BEGIN:VEVENT\r\nUID:$uid\r\nSUMMARY:$uid\r\nDTSTART:$s\r\nDTEND:$e\r\nEND:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }

    private val hour = 3_600_000L
    private val logs = mutableListOf<String>()
    private val fetcher = FakeFetcher()
    private val store = MemoryStore()
    private fun repo() = CalendarRepository(fetcher, store, { now }, { logs += it })

    @Test fun refresh_mergesTwoFeeds_andCaches() = runBlocking {
        fetcher.bodies[urlA] = ics("Work", "a1" to now + hour)
        fetcher.bodies[urlB] = ics("Home", "b1" to now + 2 * hour)
        val r = repo()
        val out = r.refresh(listOf(urlA, urlB), zone)
        assertTrue(out.ok)
        assertEquals(setOf("a1", "b1"), out.events.map { it.uid }.toSet())
        assertEquals(setOf("Work", "Home"), out.events.map { it.calendar }.toSet())
        assertEquals(now, out.fetchedAtMs)
        assertEquals(out, r.cached())
    }

    @Test fun refresh_failingFeed_keepsItsOldEvents_andMarksNotOk() = runBlocking {
        fetcher.bodies[urlA] = ics("Work", "a1" to now + hour)
        fetcher.bodies[urlB] = ics("Home", "b1" to now + 2 * hour)
        val r = repo()
        r.refresh(listOf(urlA, urlB), zone)
        fetcher.failing += urlB
        fetcher.bodies[urlA] = ics("Work", "a2" to now + 3 * hour)
        val out = r.refresh(listOf(urlA, urlB), zone)
        assertFalse(out.ok)
        assertEquals(setOf("a2", "b1"), out.events.map { it.uid }.toSet())
    }

    @Test fun refresh_emptyUrls_isEmptyAndOk() = runBlocking {
        val out = repo().refresh(emptyList(), zone)
        assertTrue(out.ok)
        assertTrue(out.events.isEmpty())
    }

    @Test fun cached_corruptBlob_isNull() = runBlocking {
        store.edit { it[stringPreferencesKey("calendar_json")] = "{not json" }
        assertNull(repo().cached())
    }

    @Test fun cached_neverWritten_isNull() = runBlocking {
        assertNull(repo().cached())
    }

    @Test fun validate_nonCalendarBody_failsWithCopy() = runBlocking {
        fetcher.bodies[urlA] = "<html>login</html>"
        val r = repo().validate(urlA)
        assertTrue(r.isFailure)
        assertEquals("That link didn't return a calendar", r.exceptionOrNull()?.message)
    }

    @Test fun validate_transportFailure_failsWithSameCopy() = runBlocking {
        fetcher.failing += urlA
        assertEquals("That link didn't return a calendar", repo().validate(urlA).exceptionOrNull()?.message)
    }

    @Test fun validate_returnsNameAndEventCount() = runBlocking {
        fetcher.bodies[urlA] = ics("Work", "a1" to now + hour, "a2" to now + 2 * hour)
        assertEquals("Work" to 2, repo().validate(urlA).getOrNull())
    }

    @Test fun validate_unnamedCalendar_usesHost() = runBlocking {
        fetcher.bodies[urlA] = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//t//EN\r\nEND:VCALENDAR\r\n"
        assertEquals("a.example.com" to 0, repo().validate(urlA).getOrNull())
    }

    @Test fun log_neverContainsPath() = runBlocking {
        fetcher.bodies[urlA] = ics("Work", "a1" to now + hour)
        fetcher.failing += urlB
        val r = repo()
        r.refresh(listOf(urlA, urlB), zone)
        r.validate(urlB)
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.none { it.contains("private-") })
        assertNotNull(logs.firstOrNull { it.contains("b.example.com") })
    }
}
