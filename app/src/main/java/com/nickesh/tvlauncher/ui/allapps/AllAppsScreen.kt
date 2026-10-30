package com.nickesh.tvlauncher.ui.allapps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.ui.background.Background
import com.nickesh.tvlauncher.ui.components.Icons
import com.nickesh.tvlauncher.ui.components.RoundButton
import com.nickesh.tvlauncher.ui.home.AppTile
import com.nickesh.tvlauncher.ui.home.AppTileButton
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

@Composable
fun AllAppsScreen(
    apps: List<AppTile>,
    settings: Settings,
    accent: Color,
    onLaunch: (AppTile) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        Background(settings, dim = 0.5f, scrim = false)
        Column(Modifier.fillMaxSize().padding(start = 96.dpx(), end = 96.dpx(), top = 60.dpx(), bottom = 56.dpx())) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dpx())) {
                RoundButton(Icons.Back, "Back", onBack)
                Text("All apps", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 48.spx())
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxSize().padding(top = 32.dpx()),
                contentPadding = PaddingValues(vertical = 16.dpx()),
                verticalArrangement = Arrangement.spacedBy(28.dpx()),
                horizontalArrangement = Arrangement.spacedBy(22.dpx(), Alignment.CenterHorizontally),
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { i, app ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AppTileButton(
                            app = app,
                            accent = accent,
                            onClick = { onLaunch(app) },
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
        }
    }
}
