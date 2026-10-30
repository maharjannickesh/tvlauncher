package com.nickesh.tvlauncher.ui.allapps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.domain.apps.AppFilter
import com.nickesh.tvlauncher.ui.background.Background
import com.nickesh.tvlauncher.ui.components.Icons
import com.nickesh.tvlauncher.ui.components.RoundButton
import com.nickesh.tvlauncher.ui.home.AppTile
import com.nickesh.tvlauncher.ui.home.AppTileButton
import com.nickesh.tvlauncher.ui.theme.Glass
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

@Composable
fun AllAppsScreen(
    apps: List<AppTile>,
    focusSearch: Boolean,
    settings: Settings,
    accent: Color,
    onLaunch: (AppTile) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    val search = remember { FocusRequester() }
    val shown = remember(apps, query) { AppFilter.filter(apps, query) { it.label } }
    BackHandler {
        if (query.isNotEmpty()) { query = ""; runCatching { search.requestFocus() } } else onBack()
    }
    // Focus follows the grid: when the focused tile leaves `shown` (app list change), hand focus to its neighbour.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusedPkg by remember { mutableStateOf<String?>(null) }
    var previous by remember { mutableStateOf(emptyList<String>()) }
    val latestShown by rememberUpdatedState(shown)
    LaunchedEffect(shown) {
        val pkgs = shown.map { it.packageName }
        val old = previous
        previous = pkgs
        val gone = focusedPkg
        if (gone != null && gone !in pkgs && pkgs.isNotEmpty()) {
            val idx = old.indexOf(gone).coerceAtLeast(0)
            // Forget the gone tile first: the neighbour's onFocusChanged (which may fire inside requestFocus) sets it
            // again if the hand-off lands; if it does not, no stale package lingers.
            focusedPkg = null
            runCatching { requesters.getOrPut(pkgs[minOf(idx, pkgs.lastIndex)]) { FocusRequester() }.requestFocus() }
        }
        requesters.keys.retainAll(pkgs.toSet())
    }
    LaunchedEffect(focusSearch, apps.isNotEmpty()) {
        if (focusSearch) runCatching { search.requestFocus() }
        else if (apps.isNotEmpty()) runCatching { first.requestFocus() }
    }
    Box(Modifier.fillMaxSize()) {
        Background(settings, dim = 0.5f, scrim = false)
        Column(Modifier.fillMaxSize().padding(start = 96.dpx(), end = 96.dpx(), top = 60.dpx(), bottom = 56.dpx())) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dpx())) {
                RoundButton(Icons.Back, "Back", onBack)
                Text("All apps", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 48.spx())
            }
            val pillShape = RoundedCornerShape(28.dpx())
            Row(
                Modifier.padding(top = 24.dpx()).width(520.dpx()).height(56.dpx())
                    .clip(pillShape).background(Glass.fill, pillShape).border(1.dpx(), Glass.border, pillShape)
                    .padding(horizontal = 20.dpx()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dpx()),
            ) {
                Icon(Icons.Search, null, Modifier.size(24.dpx()), tint = Color.White)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontFamily = Outfit, fontSize = 24.spx()),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f).focusRequester(search).onPreviewKeyEvent {
                        if (it.key == Key.DirectionDown && it.type == KeyEventType.KeyDown && shown.isNotEmpty()) {
                            runCatching { first.requestFocus() }.isSuccess
                        } else false
                    },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("Search apps", color = Color.White.copy(alpha = 0.5f), fontFamily = Outfit, fontSize = 24.spx())
                            }
                            inner()
                        }
                    },
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxSize().padding(top = 32.dpx()).focusRestorer(),
                contentPadding = PaddingValues(vertical = 16.dpx()),
                verticalArrangement = Arrangement.spacedBy(28.dpx()),
                horizontalArrangement = Arrangement.spacedBy(22.dpx(), Alignment.CenterHorizontally),
            ) {
                itemsIndexed(shown, key = { _, app -> app.packageName }) { i, app ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AppTileButton(
                            app = app,
                            accent = accent,
                            onClick = { onLaunch(app) },
                            modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier)
                                .focusRequester(requesters.getOrPut(app.packageName) { FocusRequester() })
                                .onFocusChanged {
                                    if (it.isFocused) focusedPkg = app.packageName
                                    else if (focusedPkg == app.packageName && latestShown.any { a -> a.packageName == app.packageName }) focusedPkg = null
                                },
                        )
                    }
                }
            }
        }
    }
}
