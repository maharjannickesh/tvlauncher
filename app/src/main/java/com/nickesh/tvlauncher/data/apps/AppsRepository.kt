package com.nickesh.tvlauncher.data.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.nickesh.tvlauncher.ui.home.AppTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

val TONES = listOf(
    0xFF3B2F52, 0xFF4A2B36, 0xFF233A4D, 0xFF24315A,
    0xFF1E3F36, 0xFF423823, 0xFF2C3D4A, 0xFF343844,
).map(::Color)

/** Installed leanback apps with decoded banners. Loading happens off the main thread. */
class AppsRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadJob: Job? = null

    private val _apps = MutableStateFlow<List<AppTile>>(emptyList())
    val apps: StateFlow<List<AppTile>> = _apps.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init { refresh() }

    /** Re-query installed apps; a newer refresh supersedes one still in flight. */
    @Synchronized
    fun refresh() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val tiles = runCatching { load() }.getOrNull()
            if (tiles != null) _apps.value = tiles
            _ready.value = true
        }
    }

    fun launch(packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun load(): List<AppTile> {
        val pm = context.packageManager
        val scale = context.resources.displayMetrics.widthPixels / 1920f
        val bannerW = (172 * scale).toInt().coerceAtLeast(1)
        val bannerH = (104 * scale).toInt().coerceAtLeast(1)
        val iconSize = (64 * scale).toInt().coerceAtLeast(1)
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(query, 0)
        return resolved
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { it to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
            .mapIndexed { index, (info, label) ->
                val banner = runCatching {
                    (info.activityInfo.loadBanner(pm) ?: info.activityInfo.applicationInfo.loadBanner(pm))
                        ?.toBitmap(bannerW, bannerH)?.asImageBitmap()
                }.getOrNull()
                val icon = runCatching { info.loadIcon(pm).toBitmap(iconSize, iconSize).asImageBitmap() }.getOrNull()
                AppTile(info.activityInfo.packageName, label, banner, icon, TONES[index % TONES.size])
            }
    }
}
