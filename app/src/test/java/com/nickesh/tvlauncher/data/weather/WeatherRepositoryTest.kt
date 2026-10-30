package com.nickesh.tvlauncher.data.weather

import com.nickesh.tvlauncher.data.settings.TempUnit
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeApi(var fail: Boolean = false) : WeatherApi {
    var geocodeCalls = 0
    var forecastCalls = 0
    override suspend fun geocode(city: String): GeoResult? {
        geocodeCalls++
        if (fail) throw IOException("offline")
        return GeoResult(city, 39.96, -82.99, "America/New_York")
    }
    override suspend fun forecast(lat: Double, lon: Double, unit: TempUnit, zone: String): Forecast {
        forecastCalls++
        if (fail) throw IOException("offline")
        return sampleForecast()
    }
}

private class MemoryCache(var value: WeatherSnapshot? = null) : WeatherCache {
    var writes = 0
    override suspend fun read() = value
    override suspend fun write(s: WeatherSnapshot) { value = s; writes++ }
}

private fun sampleForecast() = Forecast(
    Forecast.Current(62.4, 2),
    Forecast.Daily(
        time = listOf("2026-10-29", "2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02"),
        weather_code = listOf(2, 0, 3, 61, 80),
        temperature_2m_max = listOf(64.2, 60.0, 58.5, 55.0, 52.0),
        temperature_2m_min = listOf(47.0, 45.0, 44.0, 41.0, 40.0),
    ),
)

class WeatherRepositoryTest {
    private val now = 10_000_000_000L
    private val min = 60_000L

    private fun snap(city: String = "Columbus", unit: TempUnit = TempUnit.F, at: Long) =
        WeatherSnapshot(city, unit, sampleForecast(), at)

    @Test fun freshFetch_whenNoCache() = runBlocking {
        val api = FakeApi(); val cache = MemoryCache()
        val s = WeatherRepository(api, cache) { now }.load("Columbus", TempUnit.F, "America/New_York")
        assertNotNull(s)
        assertEquals(1, api.forecastCalls)
        assertEquals(1, cache.writes)
        assertEquals(now, cache.value!!.fetchedAtEpochMs)
    }

    @Test fun usesCache_whenYoungerThan30min() = runBlocking {
        val api = FakeApi(); val cache = MemoryCache(snap(at = now - 29 * min))
        val s = WeatherRepository(api, cache) { now }.load("Columbus", TempUnit.F, "America/New_York")
        assertEquals(cache.value, s)
        assertEquals(0, api.forecastCalls)
        assertEquals(0, api.geocodeCalls)
    }

    @Test fun refetch_whenCityChanged() = runBlocking {
        val api = FakeApi(); val cache = MemoryCache(snap(at = now - 1 * min))
        val s = WeatherRepository(api, cache) { now }.load("Denver", TempUnit.F, "America/Denver")
        assertEquals("Denver", s!!.city)
        assertEquals(1, api.forecastCalls)
    }

    @Test fun refetch_whenUnitChanged() = runBlocking {
        val api = FakeApi(); val cache = MemoryCache(snap(at = now - 1 * min))
        val s = WeatherRepository(api, cache) { now }.load("Columbus", TempUnit.C, "America/New_York")
        assertEquals(TempUnit.C, s!!.unit)
        assertEquals(1, api.forecastCalls)
    }

    @Test fun apiFailure_returnsStaleCache() = runBlocking {
        val api = FakeApi(fail = true); val cache = MemoryCache(snap(at = now - 120 * min))
        val s = WeatherRepository(api, cache) { now }.load("Columbus", TempUnit.F, "America/New_York")
        assertEquals(cache.value, s)
    }

    @Test fun apiFailure_noCache_returnsNull() = runBlocking {
        val api = FakeApi(fail = true)
        val s = WeatherRepository(api, MemoryCache()) { now }.load("Columbus", TempUnit.F, "America/New_York")
        assertNull(s)
    }

    @Test fun toUi_roundsAndTakesNext4Days() {
        val ui = WeatherRepository(FakeApi(), MemoryCache()) { now }.toUi(snap(at = now), LocalDate.of(2026, 10, 29))
        assertEquals("62°", ui.now)
        assertEquals("64°", ui.hi)
        assertEquals("47°", ui.lo)
        assertEquals("Partly cloudy", ui.condition)
        assertEquals(4, ui.forecast.size)
        assertEquals("Fri", ui.forecast[0].day)
        assertEquals("59°", ui.forecast[1].hi)
    }
}
