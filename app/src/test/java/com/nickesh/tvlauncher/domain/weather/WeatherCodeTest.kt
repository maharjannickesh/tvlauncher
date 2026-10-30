package com.nickesh.tvlauncher.domain.weather

import com.nickesh.tvlauncher.ui.home.WeatherKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodeTest {
    private fun check(codes: IntRange, kind: WeatherKind, label: String) = codes.forEach {
        assertEquals("kind $it", kind, WeatherCode.kind(it))
        assertEquals("label $it", label, WeatherCode.label(it))
    }

    @Test fun clear() = check(0..0, WeatherKind.SUN, "Clear sky")
    @Test fun mainlyClear() = check(1..1, WeatherKind.SUN, "Mainly clear")
    @Test fun partlyCloudy() = check(2..2, WeatherKind.PARTLY, "Partly cloudy")
    @Test fun overcast() = check(3..3, WeatherKind.CLOUD, "Overcast")
    @Test fun fog() { check(45..45, WeatherKind.CLOUD, "Fog"); check(48..48, WeatherKind.CLOUD, "Fog") }
    @Test fun drizzle() = check(51..57, WeatherKind.RAIN, "Drizzle")
    @Test fun rain() = check(61..67, WeatherKind.RAIN, "Rain")
    @Test fun snow() = check(71..77, WeatherKind.RAIN, "Snow")
    @Test fun rainShowers() = check(80..82, WeatherKind.RAIN, "Rain showers")
    @Test fun snowShowers() = check(85..86, WeatherKind.RAIN, "Snow showers")
    @Test fun thunderstorm() = check(95..99, WeatherKind.RAIN, "Thunderstorm")
    @Test fun unknownCode_isCloudy() {
        listOf(4, 44, 58, 68, 78, 83, 90, 100, -1).forEach { check(it..it, WeatherKind.CLOUD, "Cloudy") }
    }
}
