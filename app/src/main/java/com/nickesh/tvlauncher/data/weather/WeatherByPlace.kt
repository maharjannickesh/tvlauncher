package com.nickesh.tvlauncher.data.weather

/** A weather value tagged with the exact [Place] it was computed for. */
data class PlaceWeather<T>(val place: Place, val value: T?)

/**
 * Picks a key's weather only if it was computed for the place the settings name now. After a re-pick the entry
 * still belongs to the old place until that slot's loop reloads, so it reads as null rather than showing the old
 * city's sky under the new name.
 */
object WeatherByPlace {
    fun <T> forCurrent(entries: Map<String, PlaceWeather<T>>, place: Place): T? =
        entries[place.key]?.takeIf { it.place == place }?.value
}
