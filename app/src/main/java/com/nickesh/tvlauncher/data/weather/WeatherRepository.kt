package com.nickesh.tvlauncher.data.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nickesh.tvlauncher.data.settings.TempUnit
import com.nickesh.tvlauncher.domain.weather.WeatherCode
import com.nickesh.tvlauncher.ui.home.DayForecast
import com.nickesh.tvlauncher.ui.home.WeatherUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WeatherSnapshot(val city: String, val unit: TempUnit, val forecast: Forecast, val fetchedAtEpochMs: Long)

interface WeatherCache {
    suspend fun read(): WeatherSnapshot?
    suspend fun write(s: WeatherSnapshot)

    /** Geocode results are an optimisation; implementations may skip them. */
    suspend fun readGeo(city: String): GeoResult? = null
    suspend fun writeGeo(city: String, geo: GeoResult) {}
}

class DataStoreWeatherCache(
    private val store: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : WeatherCache {
    private val snapshotKey = stringPreferencesKey("weather_json")
    private fun geoKey(city: String) = stringPreferencesKey("weather_geo_${city.trim().lowercase(Locale.US)}")

    // A corrupt or schema-changed blob must read as "no cache", never crash.
    private fun <T> decode(raw: String?, parse: (String) -> T): T? =
        raw?.let { runCatching { parse(it) }.getOrNull() }

    override suspend fun read() = decode(store.data.first()[snapshotKey]) { json.decodeFromString<WeatherSnapshot>(it) }
    override suspend fun write(s: WeatherSnapshot) {
        store.edit { it[snapshotKey] = json.encodeToString(WeatherSnapshot.serializer(), s) }
    }

    override suspend fun readGeo(city: String) =
        decode(store.data.first()[geoKey(city)]) { json.decodeFromString<GeoResult>(it) }

    override suspend fun writeGeo(city: String, geo: GeoResult) {
        store.edit { it[geoKey(city)] = json.encodeToString(GeoResult.serializer(), geo) }
    }
}

class WeatherRepository(
    private val api: WeatherApi,
    private val cache: WeatherCache,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Cached when same city/unit and under 30 min old; otherwise fetched. On any failure: the cache (even stale), else null. */
    suspend fun load(city: String, unit: TempUnit, zone: String): WeatherSnapshot? {
        val cached = try {
            cache.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val now = clock()
        if (cached != null && cached.city == city && cached.unit == unit && now - cached.fetchedAtEpochMs < WeatherFreshness.TTL_MS) {
            return cached
        }
        return try {
            val geo = cache.readGeo(city) ?: (api.geocode(city) ?: error("no geocode match for $city")).also { cache.writeGeo(city, it) }
            val forecast = api.forecast(geo.latitude, geo.longitude, unit, zone)
            WeatherSnapshot(city, unit, forecast, now).also { cache.write(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cached
        }
    }

    /** Null when [homeDate] is not in the forecast or it lacks that day or the next four. Index 0 of the result is [homeDate]; day names come from the forecast's own dates. */
    fun toUi(s: WeatherSnapshot, homeDate: LocalDate): WeatherUi? {
        val f = s.forecast
        val d = f.daily
        val base = d.time.indexOf(homeDate.toString())
        if (base < 0) return null
        val hi = d.temperature_2m_max.getOrNull(base) ?: return null
        val lo = d.temperature_2m_min.getOrNull(base) ?: return null
        fun t(v: Double) = "${v.roundToInt()}°"
        val days = (base + 1..base + 4).mapNotNull { i ->
            val date = d.time.getOrNull(i)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val code = d.weather_code.getOrNull(i)
            val dayHi = d.temperature_2m_max.getOrNull(i)
            val dayLo = d.temperature_2m_min.getOrNull(i)
            if (date == null || code == null || dayHi == null || dayLo == null) null
            else DayForecast(date.format(DAY), WeatherCode.kind(code), t(dayHi), t(dayLo))
        }
        if (days.size < 4) return null
        return WeatherUi(
            city = s.city,
            now = t(f.current.temperature_2m),
            condition = WeatherCode.label(f.current.weather_code),
            hi = t(hi),
            lo = t(lo),
            kind = WeatherCode.kind(f.current.weather_code),
            forecast = days,
        )
    }

    private companion object {
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)
    }
}

/** Decides whether a loaded snapshot counts as a success (next refresh in 30 min) or needs the 90 s retry. */
object WeatherFreshness {
    const val TTL_MS = 30 * 60_000L

    fun isFresh(s: WeatherSnapshot?, city: String, unit: TempUnit, nowMs: Long): Boolean =
        s != null && s.city == city && s.unit == unit && nowMs - s.fetchedAtEpochMs < WeatherFreshness.TTL_MS

    const val RETRY_MS = 90_000L

    /**
     * How long the weather loop sleeps: the TTL after a fresh load, [RETRY_MS] otherwise, but never past the next
     * local midnight in [zone] (plus 1 s), so the day columns re-slice for the new date instead of lagging up to 30 min.
     * An unparseable zone falls back to the TTL/retry so a bad setting cannot stall the loop.
     */
    fun sleepMs(nowMs: Long, zone: String, ok: Boolean): Long {
        val base = if (ok) TTL_MS else RETRY_MS
        val zoneId = runCatching { ZoneId.of(zone) }.getOrNull() ?: return base
        val now = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return minOf(base, midnight - nowMs + 1_000)
    }
}
