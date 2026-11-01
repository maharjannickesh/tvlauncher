package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nickesh.tvlauncher.domain.agenda.WatchTile
import com.nickesh.tvlauncher.domain.agenda.WatchingState
import com.nickesh.tvlauncher.ui.components.LocalAccent
import com.nickesh.tvlauncher.ui.components.focusGlow
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

private const val TILE_W = 354
private const val TILE_H = 199
private const val TILE_GAP = 20
private val PosterPlaceholder = Color(0xFF1A2024)

/**
 * The Continue watching row: a label, then up to three resume tiles, the permission tile, or a status line. The row
 * always keeps the tile height so Home never jumps. [right] is where Right leaves the last tile; [down] where Down leaves
 * any tile (the apps row).
 */
@Composable
fun ContinueWatchingRow(
    tiles: List<WatchTile>,
    state: WatchingState,
    onResume: (WatchTile) -> Unit,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    right: FocusRequester = FocusRequester.Default,
    down: FocusRequester = FocusRequester.Default,
) {
    // The row's one current focus stop: the first tile (OK), the allow tile, the off line (DISMISSED) or the anchor
    // (LOADING / EMPTY). While the row holds focus, every content change hands focus to the new stop, so focus never
    // falls out of the row (to the weather card, whose Select opens Settings) when the focused element leaves.
    val stop = remember { FocusRequester() }
    val content = state to tiles.map { it.id }
    val currentContent by rememberUpdatedState(content)
    var rowHadFocus by remember { mutableStateOf(false) }
    var focusedContent by remember { mutableStateOf<Pair<WatchingState, List<Long>>?>(null) }
    LaunchedEffect(content) {
        if (rowHadFocus) runCatching { stop.requestFocus() }
    }
    val stopProps = Modifier.focusProperties { this.down = down; this.right = right }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dpx())) {
        Text(
            "CONTINUE WATCHING",
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = Outfit,
            fontWeight = FontWeight.W500,
            fontSize = 22.spx(),
            letterSpacing = 1.spx(),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(TILE_H.dpx())
                .onFocusChanged {
                    if (it.hasFocus) {
                        rowHadFocus = true
                        focusedContent = currentContent
                    } else if (focusedContent == currentContent) {
                        // Lost focus with the content unchanged: the user moved away. A loss caused by the focused
                        // element leaving (content changed in the same frame) keeps the flag for the effect above.
                        rowHadFocus = false
                    }
                },
        ) {
            when (state) {
                WatchingState.OK -> Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP.dpx())) {
                    tiles.forEachIndexed { i, t ->
                        WatchTileCard(
                            t,
                            onClick = { onResume(t) },
                            modifier = (if (i == 0) Modifier.focusRequester(stop) else Modifier).focusProperties {
                                this.down = down
                                if (i == tiles.lastIndex) this.right = right
                            },
                        )
                    }
                }
                WatchingState.NEEDS_PERMISSION -> AllowTile(onAllow, Modifier.focusRequester(stop).then(stopProps))
                WatchingState.DISMISSED -> OffLine(onOpenSettings, Modifier.align(Alignment.CenterStart).focusRequester(stop).then(stopProps))
                WatchingState.EMPTY -> {
                    PlaceholderTiles()
                    Text(
                        "Nothing in progress",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0x99FFFFFF),
                        fontFamily = Outfit,
                        fontWeight = FontWeight.W400,
                        fontSize = 20.spx(),
                    )
                    Anchor(Modifier.focusRequester(stop).then(stopProps))
                }
                WatchingState.LOADING -> {
                    PlaceholderTiles() // empty frames, no copy
                    Anchor(Modifier.focusRequester(stop).then(stopProps))
                }
            }
        }
    }
}

@Composable
private fun WatchTileCard(t: WatchTile, onClick: () -> Unit, modifier: Modifier) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(20.dpx())
    val context = LocalContext.current
    val appLabel = remember(t.packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(t.packageName, 0)).toString()
        }.getOrNull()
    }
    Surface(
        onClick = onClick,
        modifier = modifier.size(TILE_W.dpx(), TILE_H.dpx()),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PosterPlaceholder,
            focusedContainerColor = PosterPlaceholder,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dpx(), Color.White), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = focusGlow(accent)),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (t.posterUri != null) {
                AsyncImage(
                    model = t.posterUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = ColorPainter(PosterPlaceholder),
                    error = ColorPainter(PosterPlaceholder),
                    contentScale = ContentScale.Crop,
                )
            }
            if (appLabel != null) {
                Text(
                    appLabel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 14.dpx(), start = 14.dpx())
                        .background(Color(0x8C000000), RoundedCornerShape(10.dpx()))
                        .padding(horizontal = 12.dpx(), vertical = 4.dpx()),
                    color = Color.White.copy(alpha = 0.9f),
                    fontFamily = Outfit,
                    fontWeight = FontWeight.W400,
                    fontSize = 16.spx(),
                    maxLines = 1,
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0xD9000000))))
                    .padding(start = 18.dpx(), end = 18.dpx(), top = 36.dpx(), bottom = 18.dpx()),
            ) {
                Text(t.title, fontFamily = Outfit, fontWeight = FontWeight.W500, fontSize = 24.spx(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.meta.isNotEmpty()) {
                    Text(t.meta, color = Color(0xCCFFFFFF), fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 18.spx(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            t.progress?.let { p ->
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(p.coerceIn(0f, 1f))
                        .height(6.dpx())
                        .background(accent),
                )
            }
        }
    }
}

@Composable
private fun AllowTile(onClick: () -> Unit, modifier: Modifier) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(20.dpx())
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxSize(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x1AFFFFFF),
            focusedContainerColor = Color(0x1AFFFFFF),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dpx(), Color(0x29FFFFFF)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dpx(), Color.White), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = focusGlow(accent)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Show what you're watching · Press Select to allow", fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 24.spx())
        }
    }
}

@Composable
private fun OffLine(onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dpx())
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0x29FFFFFF),
            contentColor = Color(0x99FFFFFF),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dpx(), Color.White), shape = shape),
        ),
    ) {
        Text(
            "Continue watching is off · Settings",
            modifier = Modifier.padding(horizontal = 16.dpx(), vertical = 10.dpx()),
            fontFamily = Outfit,
            fontWeight = FontWeight.W400,
            fontSize = 20.spx(),
        )
    }
}

/** A focusable, non-clickable stop over the empty row, so focus has somewhere to stay while the row has no tiles. */
@Composable
private fun Anchor(modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dpx())
    Box(
        modifier
            .fillMaxSize()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .then(if (focused) Modifier.border(3.dpx(), Color.White, shape) else Modifier),
    )
}

/** Three dimmed empty frames, the row's shape while it loads or has nothing in progress. */
@Composable
private fun PlaceholderTiles() {
    val shape = RoundedCornerShape(20.dpx())
    Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(TILE_GAP.dpx())) {
        repeat(3) {
            Box(
                Modifier
                    .size(TILE_W.dpx(), TILE_H.dpx())
                    .background(Color(0x0DFFFFFF), shape)
                    .border(1.dpx(), Color(0x1FFFFFFF), shape),
            )
        }
    }
}
