package com.nickesh.tvlauncher

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nickesh.tvlauncher.data.apps.AppsRepository
import com.nickesh.tvlauncher.data.settings.SettingsRepository
import com.nickesh.tvlauncher.data.system.StandbyTracker
import com.nickesh.tvlauncher.data.system.SystemIntents
import com.nickesh.tvlauncher.data.weather.DataStoreWeatherCache
import com.nickesh.tvlauncher.data.weather.OpenMeteoClient
import com.nickesh.tvlauncher.data.weather.WeatherApi
import com.nickesh.tvlauncher.data.weather.WeatherRepository
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class AppContainer(val context: Context) {
    // Shared preferences store; the weather cache lives in it too.
    val settingsStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) { context.preferencesDataStoreFile("settings") }
    val settings = SettingsRepository(settingsStore)
    val apps = AppsRepository(context)
    val systemIntents = SystemIntents(context)
    val standby = StandbyTracker()
    val weatherCache = DataStoreWeatherCache(settingsStore)
    /** Open-Meteo client: weather loads go through [weather]; the Settings city picker searches it directly. */
    val weatherApi: WeatherApi = OpenMeteoClient(
        OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
        Json { ignoreUnknownKeys = true },
    )
    val weather = WeatherRepository(
        weatherApi,
        weatherCache,
        log = { Log.i("Weather", it) },
    )
}
