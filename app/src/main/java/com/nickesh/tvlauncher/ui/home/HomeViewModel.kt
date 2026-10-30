package com.nickesh.tvlauncher.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickesh.tvlauncher.AppContainer
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.data.settings.TempUnit
import com.nickesh.tvlauncher.domain.apps.AppOrdering
import com.nickesh.tvlauncher.domain.apps.InstalledApp
import com.nickesh.tvlauncher.domain.clock.ClockMath
import com.nickesh.tvlauncher.domain.clock.ClockSnapshot
import com.nickesh.tvlauncher.domain.clock.TimeText
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

enum class WeatherKind { SUN, PARTLY, CLOUD, RAIN }

data class DayForecast(val day: String, val kind: WeatherKind, val hi: String, val lo: String)

data class WeatherUi(
    val city: String,
    val now: String,
    val condition: String,
    val hi: String,
    val lo: String,
    val kind: WeatherKind,
    val forecast: List<DayForecast>,
)

data class AppTile(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val tone: Color,
)

data class HomeUiState(
    val greeting: String,
    val homeCity: String,
    val hero: TimeText,
    val dateLine: String,
    val weather: WeatherUi?,
    val clocks: List<ClockSnapshot>,
    val apps: List<AppTile>,
    val accent: Color,
    val settings: Settings,
)

enum class SystemAction { ASSIST, SEARCH, INPUTS, PROFILE }

/** Static stand-in until Task 7 (weather). Temperatures are authored in F and converted for the unit. */
internal fun placeholderWeather(homeDate: ZonedDateTime, settings: Settings): WeatherUi {
    fun t(f: Int) = (if (settings.unit == TempUnit.C) ((f - 32) * 5 / 9.0).roundToInt() else f).toString() + "°"
    val sample = listOf(
        Triple(WeatherKind.SUN, 64, 46),
        Triple(WeatherKind.CLOUD, 59, 44),
        Triple(WeatherKind.RAIN, 55, 41),
        Triple(WeatherKind.PARTLY, 60, 43),
    )
    val forecast = sample.mapIndexed { i, (kind, hi, lo) ->
        val day = homeDate.dayOfWeek.plus((i + 1).toLong())
        DayForecast(day.getDisplayName(TextStyle.SHORT, Locale.US), kind, t(hi), t(lo))
    }
    return WeatherUi(settings.weatherCity, t(58), "Partly cloudy", t(63), t(47), WeatherKind.PARTLY, forecast)
}

private val AllAppsTile = AppTile("", "All apps", null, null, Color(0x1FFFFFFF))

/** Favourites first, then alphabetical, capped at 8, plus the trailing synthetic "All apps" tile. */
internal fun homeRow(all: List<AppTile>, favourites: List<String>): List<AppTile> {
    val byPackage = all.associateBy { it.packageName }
    val ordered = AppOrdering.order(all.map { InstalledApp(it.packageName, it.label) }, favourites, 8)
    return ordered.mapNotNull { byPackage[it.packageName] } + AllAppsTile
}

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val ticker = flow {
        while (true) {
            val now = Instant.now()
            emit(now)
            delay(1000 - now.toEpochMilli() % 1000)
        }
    }

    private fun build(settings: Settings, allApps: List<AppTile>, now: Instant): HomeUiState {
        val home = now.atZone(ZoneId.of(settings.homeZone))
        return HomeUiState(
            greeting = ClockMath.greeting(home.hour),
            homeCity = settings.homeCity,
            hero = ClockMath.timeText(home, settings.use24h),
            dateLine = ClockMath.dateLine(home),
            weather = placeholderWeather(home, settings),
            clocks = settings.clocks.map { ClockMath.snapshot(it, home, now, settings.use24h) },
            apps = homeRow(allApps, settings.favourites),
            accent = Color(settings.accentArgb),
            settings = settings,
        )
    }

    val state: StateFlow<HomeUiState> =
        combine(container.settings.settings, container.apps.apps, ticker, ::build)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(Settings(), container.apps.apps.value, Instant.now()))
}
