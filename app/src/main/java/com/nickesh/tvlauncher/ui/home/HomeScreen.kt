package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nickesh.tvlauncher.ui.background.Background
import com.nickesh.tvlauncher.ui.components.LocalAccent
import com.nickesh.tvlauncher.ui.theme.dpx

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenAllApps: () -> Unit,
    onOpenAllAppsSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (AppTile) -> Unit,
    onAction: (SystemAction) -> Unit,
    showInputs: Boolean = true,
) {
    val assist = remember { FocusRequester() }
    CompositionLocalProvider(LocalAccent provides state.accent) {
        Box(Modifier.fillMaxSize()) {
            Background(state.settings)
            Column(
                Modifier.fillMaxSize().padding(start = 96.dpx(), end = 96.dpx(), top = 60.dpx(), bottom = 56.dpx()),
            ) {
                TopBar(state.greeting, state.homeCity, state.accent, onOpenSettings, onAction, onOpenAllAppsSearch, showInputs, assistRequester = assist)
                Row(
                    Modifier.fillMaxWidth().padding(top = 44.dpx()),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    HeroClock(state.hero, state.dateLine)
                    WeatherCard(state.weather, state.settings.homeCity, state.accent, onOpenSettings)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 40.dpx()),
                    horizontalArrangement = Arrangement.spacedBy(24.dpx()),
                ) {
                    state.clocks.forEachIndexed { i, c ->
                        // Up from the left/middle cards has no card above; send it to the Ask pill. The right card keeps the default (weather card).
                        WorldClockCard(c, state.accent, onOpenSettings, Modifier.weight(1f), up = if (i < state.clocks.lastIndex) assist else FocusRequester.Default)
                    }
                }
                Spacer(Modifier.weight(1f))
                AppsRow(state.apps, state.accent, onLaunch, onOpenAllApps)
            }
        }
    }
}
