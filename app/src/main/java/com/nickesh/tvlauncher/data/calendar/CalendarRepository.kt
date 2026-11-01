package com.nickesh.tvlauncher.data.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nickesh.tvlauncher.data.net.TextFetcher
import com.nickesh.tvlauncher.domain.connect.CalendarOwner
import java.io.IOException
import java.net.URI
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** [names] maps each feed URL to the calendar name its events carry, so a failing feed's old events can be found. */
@Serializable
data class CalendarCache(
    val events: List<CalendarEvent>,
    val fetchedAtMs: Long,
    val ok: Boolean,
    val names: Map<String, String> = emptyMap(),
)

class CalendarRepository(
    private val fetcher: TextFetcher,
    private val store: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val key = stringPreferencesKey("calendar_json")

    /** Pure read; a corrupt blob reads as null. */
    suspend fun cached(): CalendarCache? =
        store.data.first()[key]?.let { runCatching { json.decodeFromString<CalendarCache>(it) }.getOrNull() }

    suspend fun refresh(urls: List<String>, zone: ZoneId): CalendarCache {
        val now = clock()
        val previous = cached()
        val events = mutableListOf<CalendarEvent>()
        val names = mutableMapOf<String, String>()
        var ok = true
        for (url in urls) {
            val host = hostOf(url)
            try {
                val body = fetcher.get(url)
                if (!isCalendar(body)) throw IOException("not a calendar")
                val name = IcsParser.calendarName(body) ?: host
                val parsed = IcsParser.parse(body, now - DAY_MS, now + WINDOW_DAYS * DAY_MS, zone, CalendarOwner.emailFromUrl(url), name)
                events += parsed
                names[url] = name
                log("$host fetched ${parsed.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ok = false
                val name = previous?.names?.get(url) ?: host
                events += previous?.events.orEmpty().filter { it.calendar == name }
                names[url] = name
                log("$host failed: ${e.message}")
            }
        }
        val out = CalendarCache(events, now, ok, names)
        store.edit { it[key] = json.encodeToString(CalendarCache.serializer(), out) }
        return out
    }

    /** (calendar name, event count in the refresh window) or a failure with the user-facing copy. */
    suspend fun validate(url: String): Result<Pair<String, Int>> {
        val host = hostOf(url)
        val body = try {
            fetcher.get(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            log("$host validate failed: ${e.message}")
            return Result.failure(IOException(NOT_A_CALENDAR))
        }
        if (!isCalendar(body)) {
            log("$host validate: not a calendar")
            return Result.failure(IOException(NOT_A_CALENDAR))
        }
        val now = clock()
        val name = IcsParser.calendarName(body) ?: host
        val count = IcsParser.parse(body, now - DAY_MS, now + WINDOW_DAYS * DAY_MS, ZoneId.systemDefault(), CalendarOwner.emailFromUrl(url), name).size
        return Result.success(name to count)
    }

    private fun isCalendar(body: String) = body.contains("BEGIN:VCALENDAR")

    private fun hostOf(url: String) = runCatching { URI(url.trim()).host }.getOrNull() ?: "unknown-host"

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val WINDOW_DAYS = 60L
        const val NOT_A_CALENDAR = "That link didn't return a calendar"
    }
}
