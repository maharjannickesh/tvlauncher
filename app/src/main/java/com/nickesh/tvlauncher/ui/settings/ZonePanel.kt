package com.nickesh.tvlauncher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nickesh.tvlauncher.domain.settings.ZoneCatalog

/**
 * Full time-zone list in two steps inside the panel slot: regions, then the zones of the chosen region
 * ([ListPanel] owns Left/Right, so the steps replace each other instead of sitting side by side).
 * The current zone's region and entry are pre-focused. Back: zones → regions → [onClose].
 */
@Composable
fun ZonePanel(current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    // Region being browsed; null while the region step is showing.
    var region by remember { mutableStateOf<String?>(null) }
    // Region focused when the region step shows: the current zone's at first, then the one last browsed.
    var regionFocus by remember { mutableStateOf(ZoneCatalog.regionOf(current)) }
    val r = region
    key(r) {
        if (r == null) {
            ListPanel(
                title = "Time zone",
                items = ZoneCatalog.regions,
                selectedIndex = ZoneCatalog.regions.indexOf(regionFocus),
                onPick = { i -> regionFocus = ZoneCatalog.regions[i]; region = ZoneCatalog.regions[i] },
                onClose = onClose,
            )
        } else {
            val zones = ZoneCatalog.zones(r)
            ListPanel(
                title = r,
                items = zones,
                selectedIndex = zones.indexOf(current),
                onPick = { i -> onPick(zones[i]) },
                onClose = { region = null },
            )
        }
    }
}
