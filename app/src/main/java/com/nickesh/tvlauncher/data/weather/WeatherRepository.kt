package com.nickesh.tvlauncher.data.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nickesh.tvlauncher.data.settings.TempUnit
import com.nickesh.tvlauncher.domain.weather.WeatherCode
import com.nickesh.tvlauncher.ui.home.DayForecast
import com.nickesh.tvlauncher.ui.home.WeatherUi
import java.time.LocalDate
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
        val cached = cache.read()
        val now = clock()
        if (cached != null && cached.city == city && cached.unit == unit && now - cached.fetchedAtEpochMs < TTL_MS) {
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

    /** Day names come from the forecast's own dates (already in the requested zone), so [homeDate] is not needed in v1. */
    @Suppress("UNUSED_PARAMETER")
    fun toUi(s: WeatherSnapshot, homeDate: LocalDate): WeatherUi {
        val f = s.forecast
        val d = f.daily
        fun t(v: Double) = "${v.roundToInt()}°"
        val days = (1..4).filter { it < d.time.size && it < d.weather_code.size && it < d.temperature_2m_max.size && it < d.temperature_2m_min.size }
            .map { i ->
                DayForecast(
                    day = LocalDate.parse(d.time[i]).format(DAY),
                    kind = WeatherCode.kind(d.weather_code[i]),
                    hi = t(d.temperature_2m_max[i]),
                    lo = t(d.temperature_2m_min[i]),
                )
            }
        return WeatherUi(
            city = s.city,
            now = t(f.current.temperature_2m),
            condition = WeatherCode.label(f.current.weather_code),
            hi = t(d.temperature_2m_max[0]),
            lo = t(d.temperature_2m_min[0]),
            kind = WeatherCode.kind(f.current.weather_code),
            forecast = days,
        )
    }

    private companion object {
        const val TTL_MS = 30 * 60_000L
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)
    }
}
