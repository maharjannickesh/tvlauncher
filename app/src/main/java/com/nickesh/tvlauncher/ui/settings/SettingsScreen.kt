package com.nickesh.tvlauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.data.settings.TempUnit
import com.nickesh.tvlauncher.data.settings.WorldClock
import com.nickesh.tvlauncher.data.settings.ZONE_OPTIONS
import com.nickesh.tvlauncher.domain.settings.IdleOptions
import com.nickesh.tvlauncher.domain.settings.SettingsDraft
import com.nickesh.tvlauncher.domain.settings.SettingsDraftRules
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

/** Panels that render in the right-hand slot of the Settings screen. */
private enum class SettingsPanel { IDLE }

/**
 * Settings over a draft: rows edit [draft] through [onDraft]; nothing is persisted until [onSave].
 * Back (key or button) leaves via [onLeave] when clean, else asks "Discard changes?" first.
 */
@Composable
fun SettingsScreen(
    saved: Settings,
    draft: SettingsDraft,
    savedIdleMinutes: Int?,
    onDraft: (SettingsDraft) -> Unit,
    onSave: () -> Unit,
    onLeave: () -> Unit,
    onOpenBackground: () -> Unit,
    focusBackgroundRow: Boolean = false,
    homeKeyEnabled: Boolean = false,
    onOpenHomeKey: () -> Unit = {},
    idleWritable: Boolean = true,
    onOpenIdlePermission: () -> Unit = {},
) {
    val settings = draft.settings
    val dirty = SettingsDraftRules.isDirty(saved, settings, savedIdleMinutes, draft.screensaverAfterMinutes)
    fun edit(f: (Settings) -> Settings) = onDraft(draft.copy(settings = f(draft.settings)))

    var confirmDiscard by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<SettingsPanel?>(null) }
    val back = { if (dirty) confirmDiscard = true else onLeave() }
    BackHandler(enabled = panel == null && !confirmDiscard) { back() }

    // One requester per focusable; the last focused one is where focus returns after the discard prompt.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    // Plain holder, not state: focus moves must not recompose the screen.
    val lastFocused = remember { arrayOfNulls<FocusRequester>(1) }
    fun req(key: String) = requesters.getOrPut(key) { FocusRequester() }
    fun tracked(key: String): Modifier {
        val r = req(key)
        return Modifier.onFocusChanged { if (it.isFocused) lastFocused[0] = r }.focusRequester(r)
    }
    val firstRow = req("name")
    val bgRow = req("background")
    val idleRow = req("idle")
    var restoreTo by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(Unit) { runCatching { (if (focusBackgroundRow) bgRow else firstRow).requestFocus() } }
    fun closePanel(returnTo: FocusRequester) {
        panel = null
        restoreTo = returnTo
    }
    LaunchedEffect(panel, confirmDiscard, restoreTo) {
        val r = restoreTo
        if (panel == null && !confirmDiscard && r != null) {
            restoreTo = null
            runCatching { r.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Background(SettingsDraftRules.merge(saved, settings), dim = 0.72f, scrim = false)
        Column(
            Modifier.fillMaxSize().padding(start = 96.dpx(), end = 96.dpx(), top = 60.dpx(), bottom = 24.dpx()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dpx())) {
                RoundButton(Icons.Back, "Back", back, tracked("back"))
                Text("Settings", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 48.spx())
            }
            Row(Modifier.fillMaxSize().padding(top = 24.dpx())) {
                Column(
                    Modifier.width(820.dpx()).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dpx(), horizontal = 8.dpx()),
                    verticalArrangement = Arrangement.spacedBy(10.dpx()),
                ) {
                    TextSettingRow("Name", settings.name, Modifier.onFocusChanged { if (it.isFocused) lastFocused[0] = firstRow }, rowRequester = firstRow) { v -> edit { it.copy(name = v) } }
                    TextSettingRow("Home city", settings.homeCity, Modifier.onFocusChanged { if (it.isFocused) lastFocused[0] = req("homeCity") }, rowRequester = req("homeCity")) { v -> edit { it.copy(homeCity = v) } }
                    ZoneRow("Home time zone", settings.homeZone, tracked("homeZone")) { d -> edit { it.copy(homeZone = cycleZone(it.homeZone, d)) } }
                    settings.clocks.forEachIndexed { i, clock ->
                        fun editClock(f: (WorldClock) -> WorldClock) = edit { s ->
                            s.copy(clocks = s.clocks.mapIndexed { j, old -> if (j == i) f(old) else old })
                        }
                        TextSettingRow("Clock ${i + 1} city", clock.city, Modifier.onFocusChanged { if (it.isFocused) lastFocused[0] = req("clock$i") }, rowRequester = req("clock$i")) { v -> editClock { it.copy(city = v) } }
                        ZoneRow("Clock ${i + 1} time zone", clock.zoneId, tracked("clockZone$i")) { d -> editClock { it.copy(zoneId = cycleZone(it.zoneId, d)) } }
                    }
                    SettingRow("Units", tracked("units"), onClick = {
                        edit { it.copy(unit = if (it.unit == TempUnit.F) TempUnit.C else TempUnit.F) }
                    }) { ValueText(if (settings.unit == TempUnit.F) "Fahrenheit (°F)" else "Celsius (°C)") }
                    SettingRow("24-hour clock", tracked("24h"), onClick = { edit { it.copy(use24h = !it.use24h) } }) {
                        SwitchVisual(settings.use24h)
                    }
                    SettingRow("Accent", tracked("accent"), onClick = {
                        edit {
                            val i = AccentOptions.indexOfFirst { c -> c.argb() == it.accentArgb }
                            it.copy(accentArgb = AccentOptions[(i + 1) % AccentOptions.size].argb())
                        }
                    }) {
                        Box(Modifier.size(36.dpx()).background(Color(settings.accentArgb), CircleShape))
                    }
                    SettingRow(
                        "Screensaver dim",
                        tracked("dim"),
                        onLeft = { edit { it.copy(screensaverDim = nudgeDim(it.screensaverDim, -1)) } },
                        onRight = { edit { it.copy(screensaverDim = nudgeDim(it.screensaverDim, 1)) } },
                    ) { ValueText("‹  ${(settings.screensaverDim * 100).roundToInt()}%  ›") }
                    SettingRow("Show seconds", tracked("seconds"), onClick = { edit { it.copy(screensaverSeconds = !it.screensaverSeconds) } }) {
                        SwitchVisual(settings.screensaverSeconds)
                    }
                    SettingRow(
                        "Screensaver after",
                        tracked("idle"),
                        onClick = { if (idleWritable) panel = SettingsPanel.IDLE else onOpenIdlePermission() },
                    ) {
                        ValueText(
                            when {
                                !idleWritable -> "Needs permission ›"
                                else -> draft.screensaverAfterMinutes?.let { IdleOptions.label(it) } ?: "—"
                            },
                        )
                    }
                    SettingRow("Background…", tracked("background"), onClick = onOpenBackground) { ValueText("›") }
                    SettingRow("Home button…", tracked("homeKey"), onClick = onOpenHomeKey) { ValueText(if (homeKeyEnabled) "On  ›" else "Off  ›") }
                    Text(
                        "Turn on 'TV Launcher Home key' there to make the Home button open this launcher.",
                        color = Color(0x99FFFFFF), fontFamily = Outfit, fontWeight = FontWeight.W300, fontSize = 18.spx(),
                        modifier = Modifier.padding(start = 8.dpx()),
                    )
                    SettingRow("Save", tracked("save"), onClick = onSave) {
                        Icon(
                            Icons.Check, contentDescription = null, modifier = Modifier.size(32.dpx()),
                            tint = if (dirty) Color(settings.accentArgb) else Color(0x66FFFFFF),
                        )
                    }
                }
                Spacer(Modifier.width(84.dpx())) // panel starts at x = 1000 design px
                Box(Modifier.width(760.dpx()).fillMaxHeight().padding(vertical = 8.dpx())) {
                    when (panel) {
                        SettingsPanel.IDLE -> ListPanel(
                            title = "Screensaver after",
                            items = IdleOptions.minutes.map { IdleOptions.label(it) },
                            selectedIndex = IdleOptions.indexFor(draft.screensaverAfterMinutes),
                            onPick = { i ->
                                onDraft(draft.copy(screensaverAfterMinutes = IdleOptions.minutes[i]))
                                closePanel(idleRow)
                            },
                            onClose = { closePanel(idleRow) },
                        )
                        null -> {}
                    }
                }
            }
        }
        if (confirmDiscard) {
            DiscardDialog(
                onKeepEditing = { confirmDiscard = false; restoreTo = lastFocused[0] ?: firstRow },
                onDiscard = { confirmDiscard = false; onLeave() },
            )
        }
    }
}

@Composable
private fun ZoneRow(label: String, zone: String, modifier: Modifier = Modifier, onCycle: (Int) -> Unit) {
    SettingRow(
        label,
        modifier,
        onClick = { onCycle(1) },
        onLeft = { onCycle(-1) },
        onRight = { onCycle(1) },
    ) { ValueText("‹  $zone  ›") }
}
