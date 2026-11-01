package com.nickesh.tvlauncher.domain.agenda

import com.nickesh.tvlauncher.data.watchnext.WatchNextRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinueWatchingTest {
    private fun row(
        id: Long = 1, type: Int = 0, engaged: Long = 0, browsable: Boolean = true,
        season: String? = null, episode: String? = null, pos: Long? = null, dur: Long? = null,
    ) = WatchNextRow(id, "pkg", "T$id", season, episode, type, engaged, pos, dur, "p", "i", browsable)

    @Test fun select_filtersBrowsableAndTypes_sortsByEngagement_limits3() {
        val rows = listOf(
            row(1, engaged = 10), row(2, engaged = 50), row(3, type = 3, engaged = 99),
            row(4, browsable = false, engaged = 98), row(5, type = 2, engaged = 30), row(6, type = 1, engaged = 40),
        )
        assertEquals(listOf(2L, 6L, 5L), ContinueWatching.select(rows).map { it.id })
    }

    @Test fun meta_episodeWithTimeLeft() =
        assertEquals("S3 E22 · 9 min left", ContinueWatching.meta(row(season = "3", episode = "22", pos = 842000, dur = 1364000)))

    @Test fun meta_filmHoursMinutes() =
        assertEquals("1h 56m left", ContinueWatching.meta(row(pos = 5687000, dur = 12675000)))

    @Test fun meta_exactHour_andUnderMinute() {
        assertEquals("1h left", ContinueWatching.meta(row(pos = 0, dur = 3600000)))
        assertEquals("< 1 min left", ContinueWatching.meta(row(pos = 0, dur = 59000)))
        assertEquals("< 1 min left", ContinueWatching.meta(row(pos = 99, dur = 50)))
    }

    @Test fun meta_newType_isUpNext() =
        assertEquals("S2 E1 · Up next", ContinueWatching.meta(row(type = 2, season = "2", episode = "1", pos = 5, dur = 100)))

    @Test fun meta_partialAndEmpty() {
        assertEquals("S3 · Up next", ContinueWatching.meta(row(type = 1, season = "3")))
        assertEquals("E22 · Up next", ContinueWatching.meta(row(type = 0, episode = "22")))
        assertEquals("Up next", ContinueWatching.meta(row(type = 1)))
    }

    @Test fun progress_clampsPastDuration_andNullWithoutDuration() {
        assertEquals(0.5f, ContinueWatching.progress(row(pos = 50, dur = 100))!!, 0.0001f)
        assertEquals(1f, ContinueWatching.progress(row(pos = 500, dur = 100))!!, 0f)
        assertEquals(0f, ContinueWatching.progress(row(pos = -5, dur = 100))!!, 0f)
        assertNull(ContinueWatching.progress(row(pos = 5, dur = null)))
        assertNull(ContinueWatching.progress(row(pos = 5, dur = 0)))
        assertNull(ContinueWatching.progress(row(pos = null, dur = 100)))
    }

    @Test fun tiles_mapsSelectedRows() {
        val t = ContinueWatching.tiles(listOf(row(7, engaged = 1, pos = 50, dur = 100)))
        assertEquals(1, t.size)
        assertEquals(WatchTile(7, "pkg", "T7", "< 1 min left", 0.5f, "p", "i"), t[0])
    }
}
