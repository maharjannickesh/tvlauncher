package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.domain.agenda.BlockStatus
import com.nickesh.tvlauncher.domain.agenda.TodoRow
import com.nickesh.tvlauncher.ui.components.Icons
import com.nickesh.tvlauncher.ui.components.LocalAccent
import com.nickesh.tvlauncher.ui.components.focusGlow
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

/**
 * Open Todoist tasks. With rows, each row is a focus stop (Select ticks it, Select on a ticked row undoes it) and [entry]
 * lands on the last-focused row (the first one at first); without rows the whole card is the focus stop for [entry].
 * [up] is where Up leaves the first row; [down] where Down leaves the last one.
 */
@Composable
fun TodoCard(
    rows: List<TodoRow>,
    open: Int,
    status: BlockStatus,
    pendingIds: Set<String>,
    onTick: (String) -> Unit,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
    entry: FocusRequester = remember { FocusRequester() },
    up: FocusRequester = FocusRequester.Default,
    down: FocusRequester = FocusRequester.Default,
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(28.dpx())
    val source = if (status == BlockStatus.OK) "Todoist · $open open" else "Todoist"
    val showRows = status == BlockStatus.OK && rows.isNotEmpty()
    // Focus follow-through: when the focused row leaves (ticked and released, or the list switches to the status
    // card), focus goes to the next row, else the previous one, else the card, never out of the card to Compose's fallback.
    val ids = if (showRows) rows.map { it.id } else emptyList()
    val currentIds by rememberUpdatedState(ids)
    var focusedId by remember { mutableStateOf<String?>(null) }
    var previousIds by remember { mutableStateOf(ids) }
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val listState = rememberLazyListState()
    LaunchedEffect(ids) {
        val gone = focusedId
        val before = previousIds
        previousIds = ids
        rowRequesters.keys.retainAll(ids.toSet())
        if (gone == null || gone in ids) return@LaunchedEffect
        val at = before.indexOf(gone)
        val target = before.drop(at + 1).firstOrNull { it in ids }
            ?: before.take(maxOf(at, 0)).lastOrNull { it in ids }
        if (target == null) {
            focusedId = null
            runCatching { entry.requestFocus() } // the card's Surface (no rows left)
        } else {
            runCatching { listState.scrollToItem(ids.indexOf(target)) }
            // The row may not be composed until the next frame after the scroll; retry once, then fall back to the card.
            fun focusTarget(): Boolean = runCatching { rowRequesters[target]?.requestFocus() == true }.getOrDefault(false)
            if (!focusTarget()) {
                withFrameNanos { }
                if (!focusTarget()) runCatching { entry.requestFocus() }
            }
        }
    }
    if (showRows) {
        Column(
            modifier
                .clip(shape)
                .background(Color(0x1AFFFFFF))
                .border(1.dpx(), Color(0x29FFFFFF), shape)
                .padding(top = 28.dpx(), bottom = 12.dpx()),
            verticalArrangement = Arrangement.spacedBy(8.dpx()),
        ) {
            AgendaHeader("TO DO", source, Modifier.padding(horizontal = 32.dpx()))
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).focusRequester(entry).focusRestorer(),
                state = listState,
                // Room for a focused row's border inside the card's clip.
                contentPadding = PaddingValues(horizontal = 16.dpx(), vertical = 6.dpx()),
                verticalArrangement = Arrangement.spacedBy(4.dpx()),
            ) {
                itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
                    val pending = r.id in pendingIds
                    TaskLine(
                        r,
                        pending,
                        accent,
                        onClick = { if (pending) onUndo(r.id) else onTick(r.id) },
                        modifier = Modifier
                            .focusRequester(rowRequesters.getOrPut(r.id) { FocusRequester() })
                            .onFocusChanged {
                                if (it.isFocused) focusedId = r.id
                                // A loss while the row is still listed is the user moving away; a loss from the row
                                // leaving keeps the id for the follow-through above.
                                else if (focusedId == r.id && r.id in currentIds) focusedId = null
                            }
                            .focusProperties {
                                if (i == 0) this.up = up
                                if (i == rows.lastIndex) this.down = down
                            },
                    )
                }
            }
        }
    } else {
        // No rows: the card itself is the focus stop so the remote path (clock / watch tile → To do) still lands.
        Surface(
            onClick = {},
            modifier = modifier.focusRequester(entry).focusProperties { this.up = up; this.down = down },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x1AFFFFFF),
                focusedContainerColor = Color(0x1AFFFFFF),
                contentColor = Color.White,
                focusedContentColor = Color.White,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dpx(), Color(0x29FFFFFF)), shape = shape),
                focusedBorder = Border(BorderStroke(3.dpx(), Color.White), shape = shape),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = focusGlow(accent)),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dpx(), vertical = 28.dpx())) {
                AgendaHeader("TO DO", source)
                val line = status.copy(unreachable = "Can't reach Todoist", empty = "All done")
                    ?: if (status == BlockStatus.OK) "All done" else null
                if (line != null) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { StatusLine(line) }
                }
            }
        }
    }
}

@Composable
private fun TaskLine(r: TodoRow, pending: Boolean, accent: Color, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dpx())
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0x29FFFFFF),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dpx(), Color.White), shape = shape),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dpx(), vertical = 12.dpx()),
            horizontalArrangement = Arrangement.spacedBy(18.dpx()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dpx()), contentAlignment = Alignment.Center) {
                val ring = Color.White.copy(alpha = 0.72f)
                val strokePx = 2.5f
                Canvas(Modifier.size(28.dpx())) {
                    if (pending) {
                        drawCircle(accent)
                    } else {
                        val w = strokePx * size.minDimension / 28f
                        drawCircle(ring, radius = size.minDimension / 2 - w / 2, style = Stroke(width = w))
                    }
                }
                if (pending) Icon(Icons.Check, null, Modifier.size(20.dpx()), tint = Color(0xFF0B0E1A))
            }
            Text(
                r.text,
                modifier = Modifier.weight(1f),
                color = if (pending) Color.White.copy(alpha = 0.55f) else Color.White,
                textDecoration = if (pending) TextDecoration.LineThrough else null,
                fontFamily = Outfit,
                fontWeight = FontWeight.W400,
                fontSize = 22.spx(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            r.due?.let {
                Text(
                    it,
                    color = if (r.overdue) Color(0xFFF2A878) else Color(0x99FFFFFF),
                    fontFamily = Outfit,
                    fontWeight = FontWeight.W400,
                    fontSize = 18.spx(),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
