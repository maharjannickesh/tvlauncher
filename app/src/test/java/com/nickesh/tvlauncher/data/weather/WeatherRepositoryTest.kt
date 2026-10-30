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
    override suspend fun search(query: String): List<GeoMatch> {
        geocodeCalls++
        if (fail) throw IOException("offline")
        return listOf(GeoMatch(query, null, null, 39.96, -82.99, "America/New_York"))
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
        assertEquals(1, api.geocodeCalls)
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
        val ui = WeatherRepository(FakeApi(), MemoryCache()) { now }.toUi(snap(at = now), LocalDate.of(2026, 10, 29))!!
        assertEquals("62°", ui.now)
        assertEquals("64°", ui.hi)
        assertEquals("47°", ui.lo)
        assertEquals("Partly cloudy", ui.condition)
        assertEquals(4, ui.forecast.size)
        assertEquals("Fri", ui.forecast[0].day)
        assertEquals("59°", ui.forecast[1].hi)
    }

    @Test fun toUi_shortDaily_returnsNull() {
        val repo = WeatherRepository(FakeApi(), MemoryCache()) { now }
        for (n in 0..1) {
            val f = sampleForecast()
            val d = f.daily
            val short = f.copy(daily = Forecast.Daily(d.time.take(n), d.weather_code.take(n), d.temperature_2m_max.take(n), d.temperature_2m_min.take(n)))
            assertNull("n=$n", repo.toUi(WeatherSnapshot("Columbus", TempUnit.F, short, now), LocalDate.of(2026, 10, 29)))
        }
    }

    @Test fun toUi_homeDateDay1_slicesFromThere() {
        val repo = WeatherRepository(FakeApi(), MemoryCache()) { now }
        val d = sampleForecast().daily
        val ui = repo.toUi(snap(at = now), LocalDate.of(2026, 10, 30))
        // only 3 days follow day 1 in the sample, so extend it to 6 days
        assertNull(ui)
        val f = sampleForecast().copy(daily = Forecast.Daily(
            d.time + "2026-11-03", d.weather_code + 0, d.temperature_2m_max + 50.0, d.temperature_2m_min + 38.0))
        val ui2 = repo.toUi(WeatherSnapshot("Columbus", TempUnit.F, f, now), LocalDate.of(2026, 10, 30))!!
        assertEquals("60°", ui2.hi)
        assertEquals("45°", ui2.lo)
        assertEquals(listOf("Sat", "Sun", "Mon", "Tue"), ui2.forecast.map { it.day })
        assertEquals("50°", ui2.forecast[3].hi)
    }

    @Test fun toUi_homeDateMissing_returnsNull() {
        val repo = WeatherRepository(FakeApi(), MemoryCache()) { now }
        assertNull(repo.toUi(snap(at = now), LocalDate.of(2026, 10, 28)))
        assertNull(repo.toUi(snap(at = now), LocalDate.of(2026, 12, 1)))
    }

    @Test fun staleCache_isNotFresh_soRetryIsShort() {
        val stale = snap(at = now - 120 * min)
        assertEquals(false, WeatherFreshness.isFresh(stale, "Columbus", TempUnit.F, now))
        assertEquals(true, WeatherFreshness.isFresh(snap(at = now - 29 * min), "Columbus", TempUnit.F, now))
        assertEquals(false, WeatherFreshness.isFresh(snap(at = now - 1 * min), "Denver", TempUnit.F, now))
        assertEquals(false, WeatherFreshness.isFresh(snap(at = now - 1 * min), "Columbus", TempUnit.C, now))
        assertEquals(false, WeatherFreshness.isFresh(null, "Columbus", TempUnit.F, now))
    }

    @Test fun sleepUntil_isCappedAtTtl_butWakesJustAfterLocalMidnight() {
        val zone = "America/New_York"
        // 23:50 local, fresh: 10 min + 1 s to midnight beats the 30 min TTL.
        val lateEvening = java.time.ZonedDateTime.of(2026, 10, 29, 23, 50, 0, 0, java.time.ZoneId.of(zone)).toInstant().toEpochMilli()
        assertEquals(10 * min + 1_000, WeatherFreshness.sleepMs(lateEvening, zone, ok = true))
        // 23:59:30 local, not fresh: 30 s + 1 s to midnight beats the 90 s retry.
        val lastHalfMinute = lateEvening + 9 * min + 30_000
        assertEquals(31_000L, WeatherFreshness.sleepMs(lastHalfMinute, zone, ok = false))
        // Midday: the TTL / retry win.
        val noon = java.time.ZonedDateTime.of(2026, 10, 29, 12, 0, 0, 0, java.time.ZoneId.of(zone)).toInstant().toEpochMilli()
        assertEquals(WeatherFreshness.TTL_MS, WeatherFreshness.sleepMs(noon, zone, ok = true))
        assertEquals(90_000L, WeatherFreshness.sleepMs(noon, zone, ok = false))
        // Exactly midnight: not zero; next midnight is a full day away so the TTL wins.
        val midnight = lateEvening + 10 * min
        assertEquals(WeatherFreshness.TTL_MS, WeatherFreshness.sleepMs(midnight, zone, ok = true))
        // A bad zone id falls back to the TTL instead of crashing the loop.
        assertEquals(WeatherFreshness.TTL_MS, WeatherFreshness.sleepMs(noon, "Nowhere/Land", ok = true))
    }
}
