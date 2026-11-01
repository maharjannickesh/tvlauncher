package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.nickesh.tvlauncher.domain.agenda.WatchTile
import com.nickesh.tvlauncher.ui.background.Background
import com.nickesh.tvlauncher.ui.components.LocalAccent
import com.nickesh.tvlauncher.ui.theme.dpx

@Composable
fun HomeScreen(
    state: HomeUiState,
    agenda: AgendaUi,
    onTick: (String) -> Unit,
    onUndo: (String) -> Unit,
    onResume: (WatchTile) -> Unit,
    onAllowWatching: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenAllAppsSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (AppTile) -> Unit,
    onAction: (SystemAction) -> Unit,
    showInputs: Boolean = true,
) {
    val assist = remember { FocusRequester() }
    val upNext = remember { FocusRequester() }
    val todo = remember { FocusRequester() }
    val apps = remember { FocusRequester() }
    CompositionLocalProvider(LocalAccent provides state.accent) {
        Box(Modifier.fillMaxSize()) {
            Background(state.settings)
            Column(
                Modifier.fillMaxSize().padding(start = 96.dpx(), end = 96.dpx(), top = 60.dpx(), bottom = 56.dpx()),
            ) {
                TopBar(state.greeting, state.homeCity, state.accent, onOpenSettings, onAction, onOpenAllAppsSearch, showInputs, assistRequester = assist)
                // Layout G: left column (clock + weather, world clocks, Continue watching); right column (Up next, To do)
                // stretched to the left column's height.
                MatchHeightColumns(
                    gap = 38.dpx(),
                    modifier = Modifier.padding(top = 24.dpx()),
                    left = {
                        Column(Modifier.width(1104.dpx())) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                // Top: the weather card's top edge lines up with the Up next card's.
                                verticalAlignment = Alignment.Top,
                            ) {
                                HeroClock(state.hero, state.dateLine, digitSpx = 190)
                                WeatherCard(
                                    state.weather,
                                    state.settings.homeCity,
                                    state.accent,
                                    onOpenSettings,
                                    Modifier.focusProperties { right = upNext },
                                    widthPx = 510,
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 20.dpx()),
                                horizontalArrangement = Arrangement.spacedBy(24.dpx()),
                            ) {
                                state.clocks.forEachIndexed { i, c ->
                                    // Up from the left/middle cards has no card above; send it to the Ask pill. The right card keeps the default (weather card).
                                    WorldClockCard(
                                        c,
                                        state.accent,
                                        onOpenSettings,
                                        Modifier.weight(1f).focusProperties { if (i == state.clocks.lastIndex) right = todo },
                                        up = if (i < state.clocks.lastIndex) assist else FocusRequester.Default,
                                        heightPx = 140,
                                        timeSpx = 44,
                                    )
                                }
                            }
                            ContinueWatchingRow(
                                tiles = agenda.watching,
                                state = agenda.watchingState,
                                onResume = onResume,
                                onAllow = onAllowWatching,
                                onOpenSettings = onOpenSettings,
                                modifier = Modifier.padding(top = 20.dpx()),
                                right = todo,
                                down = apps,
                            )
                        }
                    },
                    right = {
                        Column(Modifier.width(586.dpx())) {
                            UpNextCard(agenda.upNext, agenda.upNextStatus, onOpenCalendar, Modifier.fillMaxWidth().height(300.dpx()).focusRequester(upNext))
                            Spacer(Modifier.height(20.dpx()))
                            TodoCard(
                                rows = agenda.todo,
                                open = agenda.todoOpen,
                                status = agenda.todoStatus,
                                pendingIds = agenda.pendingIds,
                                onTick = onTick,
                                onUndo = onUndo,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                entry = todo,
                                up = upNext,
                                down = apps,
                            )
                        }
                    },
                )
                Spacer(Modifier.weight(1f))
                AppsRow(state.apps, state.accent, onLaunch, onOpenAllApps, Modifier.focusRequester(apps), tileHeightPx = 86)
            }
        }
    }
}

/**
 * Lays [left] out at its natural height and [right] at exactly that height, pinned to the right edge. A plain Row with
 * IntrinsicSize.Min cannot do this: the To do card's LazyColumn does not answer intrinsic measurements.
 */
@Composable
private fun MatchHeightColumns(gap: Dp, modifier: Modifier, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Layout(contents = listOf(left, right), modifier = modifier.fillMaxWidth()) { (l, r), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val lp = l.map { it.measure(loose) }
        val h = lp.maxOfOrNull { it.height } ?: 0
        val rightMax = (constraints.maxWidth - (lp.maxOfOrNull { it.width } ?: 0) - gap.roundToPx()).coerceAtLeast(0)
        val rp = r.map { it.measure(Constraints(maxWidth = rightMax, minHeight = h, maxHeight = h)) }
        layout(constraints.maxWidth, h) {
            lp.forEach { it.place(0, 0) }
            rp.forEach { it.place(constraints.maxWidth - it.width, 0) }
        }
    }
}
