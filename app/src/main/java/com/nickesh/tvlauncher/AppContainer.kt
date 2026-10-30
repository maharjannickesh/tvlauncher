package com.nickesh.tvlauncher

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nickesh.tvlauncher.data.apps.AppsRepository
import com.nickesh.tvlauncher.data.settings.SettingsRepository

class AppContainer(val context: Context) {
    // Shared preferences store; the weather cache reuses it in a later task.
    val settingsStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) { context.preferencesDataStoreFile("settings") }
    val settings = SettingsRepository(settingsStore)
    val apps = AppsRepository(context)
}
