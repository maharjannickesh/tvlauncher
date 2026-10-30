package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.ui.components.Icons
import com.nickesh.tvlauncher.ui.components.focusGlow
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

@Composable
fun AppsRow(
    apps: List<AppTile>,
    accent: Color,
    onLaunch: (AppTile) -> Unit,
    onOpenAllApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(pkg: String) = requesters.getOrPut(pkg) { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(-1) } // -1 = the row holds no focus
    val latestApps by rememberUpdatedState(apps)
    var previous by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(apps) {
        val pkgs = apps.map { it.packageName }
        val old = previous
        previous = pkgs
        if (old.isEmpty()) {
            runCatching { requesterFor(pkgs.first()).requestFocus() } // first composition only
        } else {
            val idx = focusedIndex
            // The focused tile left composition, so Compose cleared focus: hand it to the neighbour.
            if (idx in old.indices && old[idx] !in pkgs && pkgs.isNotEmpty()) {
                runCatching { requesterFor(pkgs[minOf(idx, pkgs.lastIndex)]).requestFocus() }
            }
        }
        requesters.keys.retainAll(pkgs.toSet())
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dpx())) {
        Text("Your apps", color = Color.White.copy(alpha = 0.9f), fontFamily = Outfit, fontWeight = FontWeight.W500, fontSize = 26.spx())
        Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(22.dpx()), verticalAlignment = Alignment.Top) {
            apps.forEachIndexed { i, app ->
                key(app.packageName) {
                    AppTileButton(
                        app = app,
                        accent = accent,
                        onClick = { if (app.packageName.isEmpty()) onOpenAllApps() else onLaunch(app) },
                        modifier = Modifier
                            .focusRequester(requesterFor(app.packageName))
                            .onFocusChanged {
                                if (it.isFocused) focusedIndex = i
                                // Losing focus because the tile left the list keeps the index (the effect below needs it).
                                else if (focusedIndex == i && latestApps.any { a -> a.packageName == app.packageName }) focusedIndex = -1
                            },
                    )
                }
            }
        }
    }
}

/** One launcher tile: banner (or icon, or letter fallback) above its label. The synthetic "" package shows the Grid icon. */
@Composable
fun AppTileButton(
    app: AppTile,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Tile(app.label, app.tone, accent, onClick, modifier) {
        when {
            app.packageName.isEmpty() -> Icon(Icons.Grid, null, Modifier.size(36.dpx()), tint = Color.White)
            app.banner != null -> Image(app.banner, app.label, Modifier.size(172.dpx(), 104.dpx()), contentScale = ContentScale.Crop)
            app.icon != null -> Image(app.icon, app.label, Modifier.size(64.dpx()))
            else -> Text(
                app.label.take(1),
                style = TextStyle(letterSpacing = (-1).spx()),
                fontFamily = Outfit,
                fontWeight = FontWeight.W600,
                fontSize = 40.spx(),
            )
        }
    }
}

@Composable
private fun Tile(
    label: String,
    tone: Color,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dpx())
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dpx())) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(172.dpx(), 104.dpx()).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = tone,
                focusedContainerColor = tone,
                contentColor = Color.White,
                focusedContentColor = Color.White,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(3.dpx(), Color(0x1AFFFFFF)), shape = shape),
                focusedBorder = Border(BorderStroke(3.dpx(), Color.White), shape = shape),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = focusGlow(accent)),
        ) {
            Box(Modifier.size(172.dpx(), 104.dpx()), contentAlignment = Alignment.Center) { content() }
        }
        Text(
            label,
            color = Color.White.copy(alpha = if (focused) 1f else 0.7f),
            fontFamily = Outfit,
            fontWeight = FontWeight.W400,
            fontSize = 19.spx(),
            maxLines = 1,
        )
    }
}
