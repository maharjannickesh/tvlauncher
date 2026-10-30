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
private data class GeoResponse(val results: List<GeoResult>? = null)

interface WeatherApi {
    /** Null when nothing matches; throws on transport failure. */
    suspend fun geocode(city: String): GeoResult?
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

    override suspend fun geocode(city: String): GeoResult? =
        json.decodeFromString<GeoResponse>(
            get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(city)}&count=1"),
        ).results?.firstOrNull()

    override suspend fun forecast(lat: Double, lon: Double, unit: TempUnit, zone: String): Forecast {
        val u = if (unit == TempUnit.F) "fahrenheit" else "celsius"
        return json.decodeFromString(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&temperature_unit=$u&timezone=${enc(zone)}&forecast_days=5",
            ),
        )
    }
}
