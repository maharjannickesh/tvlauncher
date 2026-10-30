package com.nickesh.tvlauncher.data.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.data.settings.TempUnit
import com.nickesh.tvlauncher.domain.clock.ClockWeather
import com.nickesh.tvlauncher.domain.weather.WeatherCode
import com.nickesh.tvlauncher.ui.home.DayForecast
import com.nickesh.tvlauncher.ui.home.WeatherUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A place whose weather is loaded: [name] is what the card shows and what cache freshness matches on;
 * [queryName] is what gets geocoded when [lat]/[lon] are null (the home place's legacy `weather_city` lives here).
 */
data class Place(val key: String, val name: String, val queryName: String, val lat: Double?, val lon: Double?)

object Places {
    const val HOME = "home"
    fun clock(i: Int) = "clock$i"

    /** Home first, then one place per world clock, keyed `clock0..`. */
    fun fromSettings(s: Settings): List<Place> =
        listOf(Place(HOME, s.homeCity, s.legacyWeatherCity ?: s.homeCity, s.homeLat, s.homeLon)) +
            s.clocks.mapIndexed { i, c -> Place(clock(i), c.city, c.city, c.lat, c.lon) }
}

/** [lat]/[lon] are the place's own coordinates when it had them, null when it was geocoded by name. */
@Serializable
data class WeatherSnapshot(
    val city: String,
    val lat: Double?,
    val lon: Double?,
    val unit: TempUnit,
    val forecast: Forecast,
    val fetchedAtEpochMs: Long,
)

interface WeatherCache {
    suspend fun read(key: String): WeatherSnapshot?
    suspend fun write(key: String, s: WeatherSnapshot)

    /** Geocode results are an optimisation; implementations may skip them. */
    suspend fun readGeo(city: String): GeoResult? = null
    suspend fun writeGeo(city: String, geo: GeoResult) {}
}

class DataStoreWeatherCache(
    private val store: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : WeatherCache {
    // One blob per place key; the v1 single `weather_json` key is abandoned (never read).
    private fun snapshotKey(key: String) = stringPreferencesKey("weather_json_$key")
    private fun geoKey(city: String) = stringPreferencesKey("weather_geo_${city.trim().lowercase(Locale.US)}")

    // A corrupt or schema-changed blob must read as "no cache", never crash.
    private fun <T> decode(raw: String?, parse: (String) -> T): T? =
        raw?.let { runCatching { parse(it) }.getOrNull() }

    override suspend fun read(key: String) =
        decode(store.data.first()[snapshotKey(key)]) { json.decodeFromString<WeatherSnapshot>(it) }

    override suspend fun write(key: String, s: WeatherSnapshot) {
        store.edit { it[snapshotKey(key)] = json.encodeToString(WeatherSnapshot.serializer(), s) }
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
    private val log: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Cached when fresh for [place] (same location and unit, under 30 min old); otherwise fetched — straight from
     * the place's coordinates, or by geocoding [Place.queryName] when it has none. On any failure: the cache (even
     * stale) when it is for the same place and unit, else null.
     */
    suspend fun load(place: Place, unit: TempUnit, zone: String): WeatherSnapshot? {
        val cached = try {
            cache.read(place.key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val now = clock()
        if (WeatherFreshness.isFresh(cached, place, unit, now)) {
            log("${place.key} ${place.name} cached")
            return cached
        }
        return try {
            val (lat, lon) = if (place.lat != null && place.lon != null) {
                place.lat to place.lon
            } else {
                val q = place.queryName
                val geo = cache.readGeo(q) ?: (api.geocode(q) ?: error("no geocode match for $q")).also { cache.writeGeo(q, it) }
                geo.latitude to geo.longitude
            }
            val forecast = api.forecast(lat, lon, unit, zone)
            WeatherSnapshot(place.name, place.lat, place.lon, unit, forecast, now)
                .also { cache.write(place.key, it) }
                .also { log("${place.key} ${place.name} fetched") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("${place.key} ${place.name} failed: ${e.message}")
            // Stale is fine; another place's (or another unit's) weather is not (a re-picked clock must not show the old city's sky).
            cached?.takeIf { it.unit == unit && WeatherFreshness.samePlace(it, place) }
        }
    }

    /** Current conditions only, for a clock card. */
    fun toCurrent(s: WeatherSnapshot): ClockWeather =
        ClockWeather(WeatherCode.kind(s.forecast.current.weather_code), "${s.forecast.current.temperature_2m.roundToInt()}°")

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

    /** Under the TTL, same unit, and the same place: coordinates within 1e-6 when both sides have them, the name (any case) when neither does, never when only one does. */
    fun isFresh(s: WeatherSnapshot?, place: Place, unit: TempUnit, nowMs: Long): Boolean =
        s != null && s.unit == unit && nowMs - s.fetchedAtEpochMs < TTL_MS && samePlace(s, place)

    fun samePlace(s: WeatherSnapshot, place: Place): Boolean {
        val snapHas = s.lat != null && s.lon != null
        val placeHas = place.lat != null && place.lon != null
        return when {
            snapHas && placeHas -> abs(s.lat!! - place.lat!!) < 1e-6 && abs(s.lon!! - place.lon!!) < 1e-6
            snapHas != placeHas -> false // mixed: one side was geocoded by name, the other picked — never trust the name
            else -> s.city.equals(place.name, ignoreCase = true)
        }
    }

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
