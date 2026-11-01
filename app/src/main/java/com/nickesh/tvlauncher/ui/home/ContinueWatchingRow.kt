package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val firstTile = remember { FocusRequester() }
    // The allow tile held focus when the permission dialog opened; once tiles replace it, focus goes to the first one
    // instead of wherever Compose falls back to.
    var allowHadFocus by remember { mutableStateOf(false) }
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(state) {
        // Granting passes through LOADING/EMPTY before OK, so the flag survives those and clears on OK or DISMISSED.
        if (state == WatchingState.OK && allowHadFocus) runCatching { firstTile.requestFocus() }
        if (state == WatchingState.OK || state == WatchingState.DISMISSED) allowHadFocus = false
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dpx())) {
        Text(
            "CONTINUE WATCHING",
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = Outfit,
            fontWeight = FontWeight.W500,
            fontSize = 22.spx(),
            letterSpacing = 1.spx(),
        )
        Box(Modifier.fillMaxWidth().height(TILE_H.dpx())) {
            when (state) {
                WatchingState.OK -> Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP.dpx())) {
                    tiles.forEachIndexed { i, t ->
                        WatchTileCard(
                            t,
                            onClick = { onResume(t) },
                            modifier = (if (i == 0) Modifier.focusRequester(firstTile) else Modifier).focusProperties {
                                this.down = down
                                if (i == tiles.lastIndex) this.right = right
                            },
                        )
                    }
                }
                WatchingState.NEEDS_PERMISSION -> AllowTile(
                    onAllow,
                    Modifier
                        .onFocusChanged {
                            // A focus loss caused by the tile leaving (permission granted) must not clear the flag.
                            if (it.isFocused) allowHadFocus = true
                            else if (currentState == WatchingState.NEEDS_PERMISSION) allowHadFocus = false
                        }
                        .focusProperties { this.down = down; this.right = right },
                )
                WatchingState.DISMISSED -> OffLine(onOpenSettings, Modifier.align(Alignment.CenterStart).focusProperties { this.down = down; this.right = right })
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
                }
                WatchingState.LOADING -> PlaceholderTiles() // empty frames, no copy
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
