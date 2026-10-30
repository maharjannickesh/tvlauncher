package com.nickesh.tvlauncher.domain.weather

import com.nickesh.tvlauncher.ui.home.WeatherKind

/** Maps WMO weather codes (as returned by Open-Meteo) to an icon kind and a short label. */
object WeatherCode {
    fun kind(code: Int): WeatherKind = when (code) {
        0, 1 -> WeatherKind.SUN
        2 -> WeatherKind.PARTLY
        in 51..57, in 61..67, in 71..77, in 80..82, 85, 86, in 95..99 -> WeatherKind.RAIN
        else -> WeatherKind.CLOUD
    }

    fun label(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Rain showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "Cloudy"
    }
}
