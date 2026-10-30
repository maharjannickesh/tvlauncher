package com.nickesh.tvlauncher

import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nickesh.tvlauncher.domain.clock.ClockMath
import com.nickesh.tvlauncher.ui.home.WeatherUi
import com.nickesh.tvlauncher.ui.screensaver.ScreensaverContent
import com.nickesh.tvlauncher.ui.theme.TvLauncherTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

/** Flip-clock screensaver. Reads settings and the cached weather only; never touches the network. */
class ScreensaverService : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private var registry = LifecycleRegistry(this)
    private var savedState = SavedStateRegistryController.create(this)
    private var store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        // A dream can be attached more than once; owners are single-use after DESTROYED.
        registry = LifecycleRegistry(this)
        savedState = SavedStateRegistryController.create(this)
        store = ViewModelStore()
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED

        val container = (application as TvLauncherApp).container
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ScreensaverService)
            setViewTreeSavedStateRegistryOwner(this@ScreensaverService)
            setViewTreeViewModelStoreOwner(this@ScreensaverService)
            setContent {
                val settings by container.settings.settings.collectAsState(null)
                val weather by produceState<WeatherUi?>(null) {
                    value = container.weatherCache.read()?.let { container.weather.toUi(it, LocalDate.now()) }
                }
                var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        nowMs = System.currentTimeMillis()
                        delay(1000 - nowMs % 1000)
                    }
                }
                settings?.let { s ->
                    val instant = Instant.ofEpochMilli(nowMs)
                    val home = instant.atZone(ZoneId.of(s.homeZone))
                    val clocks = s.clocks.map { ClockMath.snapshot(it, home, instant, s.use24h) }
                    TvLauncherTheme(accent = Color(s.accentArgb)) {
                        ScreensaverContent(s, weather, clocks, home)
                    }
                }
            }
        }
        // Compose resolves owners from the window root, so mirror them on the decor view too.
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        window.decorView.setViewTreeViewModelStoreOwner(this)
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        registry.currentState = Lifecycle.State.STARTED
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDetachedFromWindow()
    }
}
