package com.nickesh.tvlauncher.data.weather

import com.nickesh.tvlauncher.data.settings.TempUnit
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class GeoResult(val name: String, val latitude: Double, val longitude: Double, val timezone: String)

@Serializable
data class Forecast(val current: Current, val daily: Daily) {
    @Serializable
    data class Current(val temperature_2m: Double, val weather_code: Int)

    @Serializable
    data class Daily(
        val time: List<String>,
        val weather_code: List<Int>,
        val temperature_2m_max: List<Double>,
        val temperature_2m_min: List<Double>,
    )
}

@Serializable
data class GeoMatch(
    val name: String,
    val admin1: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
) {
    val label: String get() = listOfNotNull(name, admin1, country).joinToString(", ")
}

@Serializable
internal data class GeoResponse(val results: List<GeoMatch>? = null)

interface WeatherApi {
    /** Up to 8 matches, empty when nothing matches; throws on transport failure. */
    suspend fun search(query: String): List<GeoMatch>

    /** Null when nothing matches; throws on transport failure. */
    suspend fun geocode(city: String): GeoResult? =
        search(city).firstOrNull()?.let { GeoResult(it.name, it.latitude, it.longitude, it.timezone) }

    suspend fun forecast(lat: Double, lon: Double, unit: TempUnit, zone: String): Forecast
}

class OpenMeteoClient(private val http: OkHttpClient, private val json: Json) : WeatherApi {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string() ?: throw IOException("empty body")
        }
    }

    override suspend fun search(query: String): List<GeoMatch> =
        json.decodeFromString<GeoResponse>(
            get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(query)}&count=8&language=en"),
        ).results.orEmpty()

    override suspend fun forecast(lat: Double, lon: Double, unit: TempUnit, zone: String): Forecast {
        val u = if (unit == TempUnit.F) "fahrenheit" else "celsius"
        return json.decodeFromString(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&temperature_unit=$u&timezone=${enc(zone)}&forecast_days=7",
            ),
        )
    }
}
