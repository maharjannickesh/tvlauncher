package com.nickesh.tvlauncher.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            transform(prefs.toSettings()).toPreferences().asMap().forEach { (k, v) ->
                @Suppress("UNCHECKED_CAST")
                prefs[k as Preferences.Key<Any>] = v
            }
        }
    }
}
