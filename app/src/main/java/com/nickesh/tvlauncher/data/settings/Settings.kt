package com.nickesh.tvlauncher.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey

enum class TempUnit { F, C }
enum class BackgroundSource { PHOTOS, SCENES, COLOR } // PHOTOS exists but is disabled in v1 UI
enum class Scene { DUSK, NIGHT, FOREST }
enum class ChangeInterval(val minutes: Int?) { M5(5), M15(15), H1(60), DAILY(1440), NEVER(null) }

data class WorldClock(val city: String, val zoneId: String, val lat: Double? = null, val lon: Double? = null)

val DEFAULT_FAVOURITES = listOf(
    "com.google.android.youtube.tv",
    "com.netflix.ninja",
    "com.amazon.amazonvideo.livingroom",
    "com.disney.disneyplus",
    "com.spotify.tv.android",
    "com.plexapp.android",
    "com.nirajgtm.xtreamtv",
    "com.android.vending",
)

val ZONE_OPTIONS: List<String> = listOf(
    "Pacific/Honolulu", "America/Los_Angeles", "America/Denver", "America/Chicago",
    "America/New_York", "America/Sao_Paulo", "UTC", "Europe/London", "Europe/Paris",
    "Europe/Berlin", "Africa/Cairo", "Asia/Dubai", "Asia/Kolkata", "Asia/Kathmandu",
    "Asia/Singapore", "Asia/Shanghai", "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland",
)

val SOLID_COLOR_OPTIONS = listOf(0xFF0F1115, 0xFF1B2238, 0xFF24302C, 0xFF3A2A2E)

data class Settings(
    val name: String = "Nickesh",
    val homeCity: String = "Columbus",
    val homeZone: String = "America/New_York",
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val clocks: List<WorldClock> = listOf(
        WorldClock("Kathmandu", "Asia/Kathmandu"),
        WorldClock("London", "Europe/London"),
        WorldClock("Tokyo", "Asia/Tokyo"),
    ),
    /** Read from the v1 `weather_city` key, never written; lets old installs keep their home weather. */
    val legacyWeatherCity: String? = null,
    val unit: TempUnit = TempUnit.F,
    val use24h: Boolean = false,
    val accentArgb: Long = 0xFF8FB8FF,
    val backgroundSource: BackgroundSource = BackgroundSource.SCENES,
    val scene: Scene = Scene.DUSK,
    val solidColorArgb: Long = 0xFF0F1115,
    val changeInterval: ChangeInterval = ChangeInterval.H1,
    val shuffle: Boolean = true,
    val screensaverDim: Float = 0.35f,
    val screensaverSeconds: Boolean = false,
    val favourites: List<String> = DEFAULT_FAVOURITES,
)

internal const val CLOCK_SLOTS = 3
private const val FAVOURITES_SEPARATOR = "|"

internal object SettingsKeys {
    val name = stringPreferencesKey("name")
    val homeCity = stringPreferencesKey("home_city")
    val homeZone = stringPreferencesKey("home_zone")
    fun clockCity(i: Int) = stringPreferencesKey("clock${i}_city")
    fun clockZone(i: Int) = stringPreferencesKey("clock${i}_zone")
    val legacyWeatherCity = stringPreferencesKey("weather_city")
    val homeLat = doublePreferencesKey("home_lat")
    val homeLon = doublePreferencesKey("home_lon")
    fun clockLat(i: Int) = doublePreferencesKey("clock${i}_lat")
    fun clockLon(i: Int) = doublePreferencesKey("clock${i}_lon")
    val unit = stringPreferencesKey("unit")
    val use24h = booleanPreferencesKey("use_24h")
    val accentArgb = longPreferencesKey("accent_argb")
    val backgroundSource = stringPreferencesKey("background_source")
    val scene = stringPreferencesKey("scene")
    val solidColorArgb = longPreferencesKey("solid_color_argb")
    val changeInterval = stringPreferencesKey("change_interval")
    val shuffle = booleanPreferencesKey("shuffle")
    val screensaverDim = floatPreferencesKey("screensaver_dim")
    val screensaverSeconds = booleanPreferencesKey("screensaver_seconds")
    val favourites = stringPreferencesKey("favourites")

    /** Keys that are only written when non-null, so they must be removed when a setting clears them. */
    val optionalKeys: List<Preferences.Key<*>> =
        listOf(homeLat, homeLon) + (0 until CLOCK_SLOTS).flatMap { listOf(clockLat(it), clockLon(it)) }
}

/** Writes every present key of [s] and removes optional keys that [s] no longer has. */
fun MutablePreferences.applySettings(s: Settings) {
    val map = s.toPreferences().asMap()
    map.forEach { (k, v) ->
        @Suppress("UNCHECKED_CAST")
        this[k as Preferences.Key<Any>] = v
    }
    SettingsKeys.optionalKeys.forEach { if (it !in map) remove(it) }
}

fun Settings.toPreferences(): Preferences {
    val prefs = mutablePreferencesOf()
    prefs[SettingsKeys.name] = name
    prefs[SettingsKeys.homeCity] = homeCity
    prefs[SettingsKeys.homeZone] = homeZone
    homeLat?.let { prefs[SettingsKeys.homeLat] = it }
    homeLon?.let { prefs[SettingsKeys.homeLon] = it }
    clocks.take(CLOCK_SLOTS).forEachIndexed { i, clock ->
        prefs[SettingsKeys.clockCity(i)] = clock.city
        prefs[SettingsKeys.clockZone(i)] = clock.zoneId
        clock.lat?.let { prefs[SettingsKeys.clockLat(i)] = it }
        clock.lon?.let { prefs[SettingsKeys.clockLon(i)] = it }
    }
    prefs[SettingsKeys.unit] = unit.name
    prefs[SettingsKeys.use24h] = use24h
    prefs[SettingsKeys.accentArgb] = accentArgb
    prefs[SettingsKeys.backgroundSource] = backgroundSource.name
    prefs[SettingsKeys.scene] = scene.name
    prefs[SettingsKeys.solidColorArgb] = solidColorArgb
    prefs[SettingsKeys.changeInterval] = changeInterval.name
    prefs[SettingsKeys.shuffle] = shuffle
    prefs[SettingsKeys.screensaverDim] = screensaverDim
    prefs[SettingsKeys.screensaverSeconds] = screensaverSeconds
    prefs[SettingsKeys.favourites] = favourites.joinToString(FAVOURITES_SEPARATOR)
    return prefs
}

private inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: default

fun Preferences.toSettings(): Settings {
    val d = Settings()
    val clocks = d.clocks.mapIndexed { i, fallback ->
        WorldClock(
            city = this[SettingsKeys.clockCity(i)] ?: fallback.city,
            zoneId = this[SettingsKeys.clockZone(i)] ?: fallback.zoneId,
            lat = this[SettingsKeys.clockLat(i)],
            lon = this[SettingsKeys.clockLon(i)],
        )
    }
    return Settings(
        name = this[SettingsKeys.name] ?: d.name,
        homeCity = this[SettingsKeys.homeCity] ?: d.homeCity,
        homeZone = this[SettingsKeys.homeZone] ?: d.homeZone,
        homeLat = this[SettingsKeys.homeLat],
        homeLon = this[SettingsKeys.homeLon],
        clocks = clocks,
        legacyWeatherCity = this[SettingsKeys.legacyWeatherCity],
        unit = this[SettingsKeys.unit].toEnumOr(d.unit),
        use24h = this[SettingsKeys.use24h] ?: d.use24h,
        accentArgb = this[SettingsKeys.accentArgb] ?: d.accentArgb,
        backgroundSource = this[SettingsKeys.backgroundSource].toEnumOr(d.backgroundSource),
        scene = this[SettingsKeys.scene].toEnumOr(d.scene),
        solidColorArgb = this[SettingsKeys.solidColorArgb] ?: d.solidColorArgb,
        changeInterval = this[SettingsKeys.changeInterval].toEnumOr(d.changeInterval),
        shuffle = this[SettingsKeys.shuffle] ?: d.shuffle,
        screensaverDim = this[SettingsKeys.screensaverDim] ?: d.screensaverDim,
        screensaverSeconds = this[SettingsKeys.screensaverSeconds] ?: d.screensaverSeconds,
        favourites = this[SettingsKeys.favourites]?.let { s ->
            if (s.isEmpty()) emptyList() else s.split(FAVOURITES_SEPARATOR)
        } ?: d.favourites,
    )
}
