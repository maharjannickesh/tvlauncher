package com.nickesh.tvlauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.data.settings.TempUnit
import com.nickesh.tvlauncher.data.settings.WorldClock
import com.nickesh.tvlauncher.data.settings.ZONE_OPTIONS
import com.nickesh.tvlauncher.ui.background.Background
import com.nickesh.tvlauncher.ui.components.Icons
import com.nickesh.tvlauncher.ui.components.RoundButton
import com.nickesh.tvlauncher.ui.theme.AccentOptions
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx
import kotlin.math.roundToInt

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun cycleZone(current: String, step: Int): String {
    val i = ZONE_OPTIONS.indexOf(current).let { if (it < 0) 0 else it }
    return ZONE_OPTIONS[(i + step + ZONE_OPTIONS.size) % ZONE_OPTIONS.size]
}

private fun nudgeDim(v: Float, step: Int): Float =
    ((v * 20).roundToInt() + step).coerceIn(0, 16) / 20f

@Composable
fun SettingsScreen(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onOpenBackground: () -> Unit,
    onBack: () -> Unit,
    focusBackgroundRow: Boolean = false,
) {
    BackHandler { onBack() }
    val firstRow = remember { FocusRequester() }
    val bgRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { (if (focusBackgroundRow) bgRow else firstRow).requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        Background(settings, dim = 0.72f, scrim = false)
        Column(Modifier.fillMaxSize().padding(start = 96.dpx(), end = 96.dpx(), top = 60.dpx(), bottom = 24.dpx())) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dpx())) {
                RoundButton(Icons.Back, "Back", onBack)
                Text("Settings", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 48.spx())
            }
            Column(
                Modifier.width(820.dpx()).fillMaxSize().padding(top = 24.dpx()).verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dpx(), horizontal = 8.dpx()),
                verticalArrangement = Arrangement.spacedBy(10.dpx()),
            ) {
                TextSettingRow("Name", settings.name, rowRequester = firstRow) { v -> onUpdate { it.copy(name = v) } }
                TextSettingRow("Home city", settings.homeCity) { v -> onUpdate { it.copy(homeCity = v) } }
                ZoneRow("Home time zone", settings.homeZone) { d -> onUpdate { it.copy(homeZone = cycleZone(it.homeZone, d)) } }
                settings.clocks.forEachIndexed { i, clock ->
                    fun editClock(f: (WorldClock) -> WorldClock) = onUpdate { s ->
                        s.copy(clocks = s.clocks.mapIndexed { j, old -> if (j == i) f(old) else old })
                    }
                    TextSettingRow("Clock ${i + 1} city", clock.city) { v -> editClock { it.copy(city = v) } }
                    ZoneRow("Clock ${i + 1} time zone", clock.zoneId) { d -> editClock { it.copy(zoneId = cycleZone(it.zoneId, d)) } }
                }
                TextSettingRow("Weather city", settings.weatherCity) { v -> onUpdate { it.copy(weatherCity = v) } }
                SettingRow("Units", onClick = {
                    onUpdate { it.copy(unit = if (it.unit == TempUnit.F) TempUnit.C else TempUnit.F) }
                }) { ValueText(if (settings.unit == TempUnit.F) "Fahrenheit (°F)" else "Celsius (°C)") }
                SettingRow("24-hour clock", onClick = { onUpdate { it.copy(use24h = !it.use24h) } }) {
                    SwitchVisual(settings.use24h)
                }
                SettingRow("Accent", onClick = {
                    onUpdate {
                        val i = AccentOptions.indexOfFirst { c -> c.argb() == it.accentArgb }
                        it.copy(accentArgb = AccentOptions[(i + 1) % AccentOptions.size].argb())
                    }
                }) {
                    Box(Modifier.size(36.dpx()).background(Color(settings.accentArgb), CircleShape))
                }
                SettingRow(
                    "Screensaver dim",
                    onLeft = { onUpdate { it.copy(screensaverDim = nudgeDim(it.screensaverDim, -1)) } },
                    onRight = { onUpdate { it.copy(screensaverDim = nudgeDim(it.screensaverDim, 1)) } },
                ) { ValueText("‹  ${(settings.screensaverDim * 100).roundToInt()}%  ›") }
                SettingRow("Show seconds", onClick = { onUpdate { it.copy(screensaverSeconds = !it.screensaverSeconds) } }) {
                    SwitchVisual(settings.screensaverSeconds)
                }
                SettingRow("Background…", Modifier.focusRequester(bgRow), onClick = onOpenBackground) { ValueText("›") }
            }
        }
    }
}

@Composable
private fun ZoneRow(label: String, zone: String, onCycle: (Int) -> Unit) {
    SettingRow(
        label,
        onClick = { onCycle(1) },
        onLeft = { onCycle(-1) },
        onRight = { onCycle(1) },
    ) { ValueText("‹  $zone  ›") }
}
