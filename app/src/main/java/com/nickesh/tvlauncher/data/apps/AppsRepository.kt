package com.nickesh.tvlauncher.data.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

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

    private val generation = AtomicInteger(0)
    private val loadLock = Mutex()

    init { refresh(debounceMs = 0) }

    /**
     * Re-query installed apps. Debounced so the REMOVED/ADDED/REPLACED burst of a package update becomes one
     * reload; loads run one at a time and only the newest generation publishes.
     */
    fun refresh(debounceMs: Long = REFRESH_DEBOUNCE_MS) {
        val gen = generation.incrementAndGet()
        synchronized(this) {
            loadJob?.cancel()
            loadJob = scope.launch {
                if (debounceMs > 0) delay(debounceMs)
                loadLock.withLock {
                    if (gen != generation.get()) return@withLock
                    val tiles = runCatching { load() }.getOrNull()
                    ensureActive()
                    if (gen != generation.get()) return@withLock
                    if (tiles != null) _apps.value = tiles
                    _ready.value = true
                }
            }
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
                        ?.toCroppedBitmap(bannerW, bannerH)?.asImageBitmap()
                }.getOrNull()
                val icon = if (banner != null) null else
                    runCatching { info.loadIcon(pm).toBitmap(iconSize, iconSize).asImageBitmap() }.getOrNull()
                AppTile(info.activityInfo.packageName, label, banner, icon, TONES[index % TONES.size])
            }
    }

    /** Scales to cover [w]x[h] preserving aspect ratio and centre-crops, instead of stretching. */
    private fun Drawable.toCroppedBitmap(w: Int, h: Int): Bitmap {
        val iw = if (intrinsicWidth > 0) intrinsicWidth else w
        val ih = if (intrinsicHeight > 0) intrinsicHeight else h
        val scale = maxOf(w / iw.toFloat(), h / ih.toFloat())
        val dw = (iw * scale).toInt().coerceAtLeast(w)
        val dh = (ih * scale).toInt().coerceAtLeast(h)
        val left = (w - dw) / 2
        val top = (h - dh) / 2
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        setBounds(left, top, left + dw, top + dh)
        draw(Canvas(bitmap))
        return bitmap
    }
}

private const val REFRESH_DEBOUNCE_MS = 300L
