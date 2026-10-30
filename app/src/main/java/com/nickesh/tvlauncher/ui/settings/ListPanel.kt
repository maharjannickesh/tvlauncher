package com.nickesh.tvlauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.ui.components.GlassCard
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

/**
 * Generic right-side pick list for the Settings panel slot. Focus starts on [selectedIndex] (marked with a tick)
 * and stays inside the panel; OK calls [onPick], Back calls [onClose]. The caller closes the panel and
 * returns focus to the row that opened it.
 */
@Composable
fun ListPanel(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }
    val start = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GlassCard(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 28.dpx(), start = 20.dpx(), end = 20.dpx())) {
            Text(
                title, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 30.spx(),
                modifier = Modifier.padding(start = 12.dpx(), bottom = 16.dpx()),
            )
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize().onPreviewKeyEvent { e ->
                    // Left/Right would escape to the Settings list behind the panel.
                    e.type == KeyEventType.KeyDown && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)
                },
                contentPadding = PaddingValues(vertical = 8.dpx(), horizontal = 8.dpx()),
                verticalArrangement = Arrangement.spacedBy(10.dpx()),
            ) {
                itemsIndexed(items) { i, label ->
                    val edge = Modifier.onPreviewKeyEvent { e ->
                        e.type == KeyEventType.KeyDown &&
                            ((i == 0 && e.key == Key.DirectionUp) || (i == items.lastIndex && e.key == Key.DirectionDown))
                    }
                    SettingRow(
                        label,
                        modifier = if (i == start) edge.focusRequester(focus) else edge,
                        onClick = { onPick(i) },
                    ) { if (i == selectedIndex) ValueText("✓") }
                }
            }
        }
    }
}
