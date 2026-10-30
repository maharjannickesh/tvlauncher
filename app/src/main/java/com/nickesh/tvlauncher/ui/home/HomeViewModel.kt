package com.nickesh.tvlauncher.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickesh.tvlauncher.AppContainer
import com.nickesh.tvlauncher.data.settings.BackgroundSource
import com.nickesh.tvlauncher.data.settings.Scene
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.domain.apps.AppOrdering
import com.nickesh.tvlauncher.domain.apps.InstalledApp
import com.nickesh.tvlauncher.domain.clock.ClockMath
import com.nickesh.tvlauncher.domain.clock.ClockSnapshot
import com.nickesh.tvlauncher.domain.clock.TimeText
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.withTimeoutOrNull
import com.nickesh.tvlauncher.data.weather.WeatherFreshness
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    private val weatherRepo = container.weather

    /** Latest weather for the configured city, or null until the first load succeeds (also read by the screensaver). */
    val weather = MutableStateFlow<WeatherUi?>(null)

    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            container.settings.settings
                .map { Triple(it.weatherCity, it.unit, it.homeZone) }
                .distinctUntilChanged()
                .collectLatest { (city, unit, zone) ->
                    while (true) {
                        var ok = false
                        try {
                            val snap = weatherRepo.load(city, unit, zone)
                            val ui = snap?.let { weatherRepo.toUi(it, LocalDate.now(ZoneId.of(zone))) }
                            weather.value = ui
                            ok = ui != null && WeatherFreshness.isFresh(snap, city, unit, System.currentTimeMillis())
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                        }
                        // Sleep until the next try (TTL, retry, or local midnight so the day columns roll over),
                        // or until refreshNow() wakes us early.
                        withTimeoutOrNull(WeatherFreshness.sleepMs(System.currentTimeMillis(), zone, ok)) { refresh.first() }
                    }
                }
        }
    }

    /** Wakes the weather loop now (activity start / return from standby); the repository's 30 min cache dedupes fresh data. */
    fun refreshNow() {
        refresh.tryEmit(Unit)
    }

    init {
        viewModelScope.launch {
            container.settings.settings
                .map { (it.backgroundSource == BackgroundSource.SCENES && it.shuffle) to it.changeInterval.minutes }
                .distinctUntilChanged()
                .collectLatest { (enabled, minutes) ->
                    if (enabled && minutes != null) {
                        while (true) {
                            delay(minutes.minutes)
                            container.settings.update {
                                it.copy(scene = Scene.entries[(it.scene.ordinal + 1) % Scene.entries.size])
                            }
                        }
                    }
                }
        }
    }

    private fun build(settings: Settings, allApps: List<AppTile>, weather: WeatherUi?, now: Instant): HomeUiState {
        val home = now.atZone(ZoneId.of(settings.homeZone))
        return HomeUiState(
            greeting = ClockMath.greeting(home.hour),
            homeCity = settings.homeCity,
            hero = ClockMath.timeText(home, settings.use24h),
            dateLine = ClockMath.dateLine(home),
            weather = weather,
            clocks = settings.clocks.map { ClockMath.snapshot(it, home, now, settings.use24h) },
            apps = homeRow(allApps, settings.favourites),
            accent = Color(settings.accentArgb),
            settings = settings,
        )
    }

    val state: StateFlow<HomeUiState> =
        combine(container.settings.settings, container.apps.apps, weather, ticker, ::build)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(Settings(), container.apps.apps.value, null, Instant.now()))
}
