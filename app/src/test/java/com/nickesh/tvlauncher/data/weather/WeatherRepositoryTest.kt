package com.nickesh.tvlauncher.data.weather

import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.data.settings.TempUnit
import com.nickesh.tvlauncher.domain.clock.ClockWeather
import com.nickesh.tvlauncher.ui.home.WeatherKind
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

private class MemoryCache(initial: WeatherSnapshot? = null) : WeatherCache {
    val byKey = mutableMapOf<String, WeatherSnapshot>().apply { if (initial != null) put(Places.HOME, initial) }
    var writes = 0
    val value: WeatherSnapshot? get() = byKey[Places.HOME]
    override suspend fun read(key: String) = byKey[key]
    override suspend fun write(key: String, s: WeatherSnapshot) { byKey[key] = s; writes++ }
}

private fun place(city: String, key: String = Places.HOME) = Place(key, city, city, null, null)

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
        WeatherSnapshot(city, null, null, unit, sampleForecast(), at)

    @Test fun freshFetch_whenNoCache() = runBlocking {
        val api = FakeApi(); val cache = MemoryCache()
        val s = WeatherRepository(api, cache) { now }.load(place("Columbus"), TempUnit.F, "America/New_York")
        assertNotNull(s)
        assertEquals(1, api.geocodeCalls)
        assertEquals(1, api.forecastCalls)
        assertEquals(1, cache.writes)
        assertEquals(now, cache.value!!.fetchedAtEpochMs)
    }

    @Test fun usesCache_whenYoungerThan30min() = runBlocking {
        val api = FakeApi(); val cache = MemoryCache(snap(at = now - 29 * min))
        val s = WeatherRepository(api, cache) { now }.load(place("Columbus"), TempUnit.F, "America/New_York")
        assertEquals(cache.value, s)
        assertEquals(0, api.forecastCalls)
        assertEquals(0, api.geocodeCalls)
    }

    @Test fun refetch_whenCityChanged() = runBlocking {
        val api = FakeApi(); val cache = MemoryCache(snap(at = now - 1 * min))
        val s = WeatherRepository(api, cache) { now }.load(place("Denver"), TempUnit.F, "America/Denver")
        assertEquals("Denver", s!!.city)
        assertEquals(1, api.forecastCalls)
    }

    @Test fun refetch_whenUnitChanged() = runBlocking {
        val api = FakeApi(); val cache = MemoryCache(snap(at = now - 1 * min))
        val s = WeatherRepository(api, cache) { now }.load(place("Columbus"), TempUnit.C, "America/New_York")
        assertEquals(TempUnit.C, s!!.unit)
        assertEquals(1, api.forecastCalls)
    }

    @Test fun apiFailure_returnsStaleCache() = runBlocking {
        val api = FakeApi(fail = true); val cache = MemoryCache(snap(at = now - 120 * min))
        val s = WeatherRepository(api, cache) { now }.load(place("Columbus"), TempUnit.F, "America/New_York")
        assertEquals(cache.value, s)
    }

    @Test fun apiFailure_staleFallback_requiresSamePlaceAndUnit() = runBlocking {
        val api = FakeApi(fail = true); val cache = MemoryCache()
        val tokyo = snap(at = now - 120 * min).copy(city = "Tokyo", lat = 35.0, lon = 139.0)
        cache.byKey[Places.clock(0)] = tokyo
        val repo = WeatherRepository(api, cache) { now }
        // Re-picked to Lisbon while offline: Tokyo's sky must not be served.
        assertNull(repo.load(Place(Places.clock(0), "Lisbon", "Lisbon", 38.72, -9.14), TempUnit.F, "America/New_York"))
        // Same place: the stale snapshot is fine.
        assertEquals(tokyo, repo.load(Place(Places.clock(0), "Tokyo", "Tokyo", 35.0, 139.0), TempUnit.F, "America/New_York"))
        // Same place, unit switched: a °F snapshot never renders as °C.
        assertNull(repo.load(Place(Places.clock(0), "Tokyo", "Tokyo", 35.0, 139.0), TempUnit.C, "America/New_York"))
    }

    @Test fun apiFailure_noCache_returnsNull() = runBlocking {
        val api = FakeApi(fail = true)
        val s = WeatherRepository(api, MemoryCache()) { now }.load(place("Columbus"), TempUnit.F, "America/New_York")
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
            assertNull("n=$n", repo.toUi(WeatherSnapshot("Columbus", null, null, TempUnit.F, short, now), LocalDate.of(2026, 10, 29)))
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
        val ui2 = repo.toUi(WeatherSnapshot("Columbus", null, null, TempUnit.F, f, now), LocalDate.of(2026, 10, 30))!!
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
        assertEquals(false, WeatherFreshness.isFresh(stale, place("Columbus"), TempUnit.F, now))
        assertEquals(true, WeatherFreshness.isFresh(snap(at = now - 29 * min), place("Columbus"), TempUnit.F, now))
        assertEquals(false, WeatherFreshness.isFresh(snap(at = now - 1 * min), place("Denver"), TempUnit.F, now))
        assertEquals(false, WeatherFreshness.isFresh(snap(at = now - 1 * min), place("Columbus"), TempUnit.C, now))
        assertEquals(false, WeatherFreshness.isFresh(null, place("Columbus"), TempUnit.F, now))
    }

    @Test fun perKeyCache_isIndependent() = runBlocking {
        val api = FakeApi(); val cache = MemoryCache()
        val repo = WeatherRepository(api, cache) { now }
        repo.load(place("Columbus"), TempUnit.F, "America/New_York")
        repo.load(place("Tokyo", Places.clock(0)), TempUnit.F, "America/New_York")
        assertEquals("Columbus", cache.read(Places.HOME)!!.city)
        assertEquals("Tokyo", cache.read(Places.clock(0))!!.city)
        assertNull(cache.read(Places.clock(1)))
        // Each key is fresh on its own: reloading both hits the cache, not the network.
        repo.load(place("Columbus"), TempUnit.F, "America/New_York")
        repo.load(place("Tokyo", Places.clock(0)), TempUnit.F, "America/New_York")
        assertEquals(2, api.forecastCalls)
    }

    @Test fun locationMismatch_isNotFresh() {
        val tokyo = snap(at = now - 1 * min).copy(city = "Tokyo", lat = 35.0, lon = 139.0)
        assertFalse(WeatherFreshness.isFresh(tokyo, Place("clock0", "Lisbon", "Lisbon", 38.72, -9.14), TempUnit.F, now))
        assertTrue(WeatherFreshness.isFresh(tokyo, Place("clock0", "Tokyo", "Tokyo", 35.0, 139.0), TempUnit.F, now))
        assertTrue(WeatherFreshness.isFresh(tokyo.copy(lat = null, lon = null), Place("clock0", "tokyo", "tokyo", null, null), TempUnit.F, now))
    }
    @Test fun samePlace_mixedCoordinates_neverMatchesByName() {
        val tokyo = snap(at = now - 1 * min).copy(city = "Tokyo", lat = 35.0, lon = 139.0)
        assertFalse(WeatherFreshness.samePlace(tokyo, Place("clock0", "Tokyo", "Tokyo", null, null)))
        assertFalse(WeatherFreshness.samePlace(tokyo.copy(lat = null, lon = null), Place("clock0", "Tokyo", "Tokyo", 35.0, 139.0)))
    }

    @Test fun legacyWeatherCity_isHomeQuery() {
        val places = Places.fromSettings(Settings(homeCity = "Columbus", legacyWeatherCity = "Columbus, OH"))
        val p = places[0]
        assertEquals(Places.HOME, p.key)
        assertEquals("Columbus, OH", p.queryName)
        assertEquals("Columbus", p.name)
        assertEquals(listOf("home", "clock0", "clock1", "clock2"), places.map { it.key })
        assertEquals("Kathmandu", places[1].queryName)
        assertEquals("Columbus", Places.fromSettings(Settings(homeCity = "Columbus"))[0].queryName)
    }

    @Test fun coordinatesSkipGeocode() = runBlocking {
        val api = FakeApi(); val cache = MemoryCache()
        val s = WeatherRepository(api, cache) { now }
            .load(Place(Places.clock(1), "Lisbon", "Lisbon", 38.72, -9.14), TempUnit.C, "America/New_York")
        assertEquals(0, api.geocodeCalls)
        assertEquals(1, api.forecastCalls)
        assertEquals(38.72, s!!.lat!!, 1e-9)
        assertEquals("Lisbon", cache.read(Places.clock(1))!!.city)
    }

    @Test fun toCurrent_mapsCodeAndTemp() {
        val repo = WeatherRepository(FakeApi(), MemoryCache()) { now }
        assertEquals(ClockWeather(WeatherKind.PARTLY, "62°"), repo.toCurrent(snap(at = now)))
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
