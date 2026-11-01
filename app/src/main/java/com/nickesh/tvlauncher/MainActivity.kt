package com.nickesh.tvlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.data.system.IdleTimer
import com.nickesh.tvlauncher.data.system.read
import com.nickesh.tvlauncher.domain.settings.SettingsDraft
import com.nickesh.tvlauncher.domain.settings.SettingsDraftRules
import com.nickesh.tvlauncher.domain.home.HomeKey
import com.nickesh.tvlauncher.domain.home.HomeKeyState
import com.nickesh.tvlauncher.data.watchnext.ResumeIntents
import com.nickesh.tvlauncher.domain.agenda.WatchTile
import com.nickesh.tvlauncher.ui.home.AgendaViewModel
import com.nickesh.tvlauncher.ui.allapps.AllAppsScreen
import com.nickesh.tvlauncher.ui.home.AppTile
import com.nickesh.tvlauncher.ui.home.HomeScreen
import com.nickesh.tvlauncher.ui.home.HomeViewModel
import com.nickesh.tvlauncher.ui.home.SystemAction
import com.nickesh.tvlauncher.ui.settings.BackgroundScreen
import com.nickesh.tvlauncher.ui.settings.SettingsScreen
import com.nickesh.tvlauncher.ui.theme.TvLauncherTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.nickesh.tvlauncher.ui.welcome.WelcomeScreen
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URISyntaxException

class MainActivity : ComponentActivity() {
    // Bumped on every new intent (singleTask re-delivery); any of them is treated as a Home press.
    private val homePresses = mutableStateOf(0)

    // Activity-scoped so the permission result reaches the same instance Compose renders (default key, so a Compose
    // `viewModel<AgendaViewModel>()` would resolve to this one too).
    private val agendaVm: AgendaViewModel by viewModels {
        viewModelFactory { initializer { AgendaViewModel((application as TvLauncherApp).container) } }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WATCH_NEXT_REQUEST) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            agendaVm.onWatchNextPermission(granted)
        }
    }

    /** Resumes a Continue-watching title in its own app; falls back to opening the app. Never logs the intent URI. */
    private fun resume(container: AppContainer, tile: WatchTile) {
        val uri = tile.intentUri
        val started = uri != null && try {
            startActivity(ResumeIntents.sanitize(Intent.parseUri(uri, 0), tile.packageName))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: URISyntaxException) {
            false
        } catch (_: RuntimeException) {
            // The URI comes from any app's Watch Next row: a malformed extra (NumberFormatException,
            // IllegalArgumentException) or a SecurityException must fall back, never crash Home.
            false
        }
        if (!started && !container.apps.launch(tile.packageName)) {
            Toast.makeText(this, "Can't open ${tile.title}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i("Home", "onNewIntent ${intent.action} ${intent.categories}")
        homePresses.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TvLauncherApp).container
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                agendaVm.toasts.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            }
        }
        setContent {
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = Settings())
            val accent = Color(settings.accentArgb)
            // Shown on every cold start; survives rotation/recreation only.
            // Bumped on every replay so the Welcome gate re-runs even if Compose never saw the state change.
            var welcomeRun by rememberSaveable { mutableStateOf(0) }
            var welcomeDone by rememberSaveable { mutableStateOf(false) }
            var screen by rememberSaveable(stateSaver = ScreenSaver) {
                mutableStateOf<Screen>(if (welcomeDone) Screen.Home else Screen.Welcome)
            }
            LaunchedEffect(homePresses.value) {
                if (homePresses.value > 0) HomeKey.next(screen)?.let { screen = it }
            }
            var cameFromBackground by rememberSaveable { mutableStateOf(false) }
            // The Settings draft lives above the screen switch so Background → back keeps pending edits.
            // Replaced on every open from Home; a config change loses it (Settings branch re-creates it).
            var draft by remember { mutableStateOf<SettingsDraft?>(null) }
            var savedIdleMinutes by remember { mutableStateOf<Int?>(null) }
            fun openDraft(from: Settings) {
                val minutes = IdleTimer.readIdleMinutes(this@MainActivity)
                savedIdleMinutes = minutes
                draft = SettingsDraft(from, minutes)
            }
            val homeViewModel = viewModel { HomeViewModel(container) }
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner, homeViewModel) {
                val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                    if (e == androidx.lifecycle.Lifecycle.Event.ON_START) {
                        homeViewModel.refreshNow()
                        agendaVm.refreshNow()
                        val pm = getSystemService(PowerManager::class.java)
                        // A start while still asleep must not consume the pending standby.
                        if (pm.isInteractive) {
                            container.standby.onInteractiveStart()
                            if (container.standby.takeReplay(BuildConfig.WELCOME_REPLAY_MS)) {
                                Log.i("Home", "welcome replay after standby")
                                welcomeRun++
                                welcomeDone = false
                                screen = Screen.Welcome
                            }
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            val showInputs = remember { container.systemIntents.isAvailable(SystemAction.INPUTS) }
            val launch = { tile: AppTile ->
                if (!container.apps.launch(tile.packageName)) {
                    Toast.makeText(this@MainActivity, "Can't open ${tile.label}", Toast.LENGTH_SHORT).show()
                }
            }
            TvLauncherTheme(accent = accent) {
                Crossfade(targetState = screen, animationSpec = tween(400), label = "screen") { s ->
                    when (s) {
                        Screen.Welcome -> {
                            // Readiness gate: apps loaded and the saved settings emitted (so the saved name
                            // shows), capped at 4 s, never shorter than 1.2 s.
                            LaunchedEffect(welcomeRun) {
                                val start = SystemClock.elapsedRealtime()
                                withTimeoutOrNull(4000) {
                                    container.apps.ready.first { it }
                                    container.settings.settings.first()
                                }
                                delay(max(0L, 1200L - (SystemClock.elapsedRealtime() - start)))
                                welcomeDone = true
                                screen = Screen.Home
                            }
                            WelcomeScreen(settings.name, accent, settings)
                        }
                        is Screen.AllApps -> {
                            val homeState by homeViewModel.state.collectAsStateWithLifecycle()
                            val allApps by container.apps.apps.collectAsStateWithLifecycle()
                            AllAppsScreen(
                                apps = allApps,
                                focusSearch = s.focusSearch,
                                settings = homeState.settings,
                                accent = homeState.accent,
                                onLaunch = launch,
                                onBack = { screen = Screen.Home },
                            )
                        }
                        Screen.Settings -> {
                            val cur by container.settings.settings.collectAsStateWithLifecycle(initialValue = settings)
                            var homeKeyOn by remember { mutableStateOf(HomeKeyState.read(this@MainActivity)) }
                            var idleWritable by remember { mutableStateOf(IdleTimer.canWriteIdle(this@MainActivity)) }
                            // Back from the Accessibility page only resumes this activity; re-read there.
                            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                                val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                                    if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                        homeKeyOn = HomeKeyState.read(this@MainActivity)
                                        idleWritable = IdleTimer.canWriteIdle(this@MainActivity)
                                    }
                                }
                                lifecycleOwner.lifecycle.addObserver(obs)
                                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
                            }
                            // Recreation (process death / config change) lands here with no draft: seed from the store, never from
                            // the Settings() placeholder, so Save cannot write defaults. The screen stays blank until then.
                            LaunchedEffect(Unit) { if (draft == null) openDraft(container.settings.settings.first()) }
                            val d = draft
                            if (d != null) SettingsScreen(
                                saved = cur,
                                draft = d,
                                savedIdleMinutes = savedIdleMinutes,
                                onDraft = { draft = it },
                                onSave = {
                                    val toSave = d.settings
                                    val minutes = d.screensaverAfterMinutes
                                    val idleBefore = savedIdleMinutes
                                    lifecycleScope.launch {
                                        container.settings.update { SettingsDraftRules.merge(it, toSave) }
                                        if (minutes != null && minutes != idleBefore &&
                                            !IdleTimer.writeIdleMinutes(this@MainActivity, minutes)
                                        ) {
                                            Toast.makeText(this@MainActivity, "Screensaver timer not saved — grant 'Modify system settings' over adb: tools/tv.sh idle-perm", Toast.LENGTH_LONG).show()
                                        }
                                        screen = Screen.Home
                                    }
                                },
                                onLeave = { screen = Screen.Home },
                                onOpenBackground = { cameFromBackground = true; screen = Screen.Background },
                                search = { q -> container.weatherApi.search(q) },
                                focusBackgroundRow = cameFromBackground,
                                homeKeyEnabled = homeKeyOn,
                                onOpenHomeKey = {
                                    if (!container.systemIntents.openAccessibilitySettings()) {
                                        Toast.makeText(this@MainActivity, "Open TV Settings → System → Accessibility → TV Launcher Home key", Toast.LENGTH_LONG).show()
                                    }
                                },
                                idleWritable = idleWritable,
                                onOpenIdlePermission = {
                                    if (!IdleTimer.openWriteSettingsPage(this@MainActivity)) {
                                        Toast.makeText(this@MainActivity, "Grant 'Modify system settings' over adb: tools/tv.sh idle-perm", Toast.LENGTH_LONG).show()
                                    }
                                },
                            )
                        }
                        Screen.Background -> {
                            val cur by container.settings.settings.collectAsStateWithLifecycle(initialValue = settings)
                            BackgroundScreen(
                                settings = cur,
                                onUpdate = { f -> lifecycleScope.launch { container.settings.update(f) } },
                                onBack = { screen = Screen.Settings },
                            )
                        }
                        else -> {
                            // Collected here, not at the root, so the per-second ticker only recomposes Home.
                            val homeState by homeViewModel.state.collectAsStateWithLifecycle()
                            val agenda by agendaVm.state.collectAsStateWithLifecycle()
                            BackHandler { }
                            HomeScreen(
                                state = homeState,
                                agenda = agenda,
                                onTick = agendaVm::tick,
                                onUndo = agendaVm::undo,
                                onResume = { tile -> resume(container, tile) },
                                onAllowWatching = { requestPermissions(arrayOf(READ_TV_LISTINGS), WATCH_NEXT_REQUEST) },
                                onOpenCalendar = {
                                    if (!container.apps.launch(CALENDAR_PACKAGE)) {
                                        Toast.makeText(this@MainActivity, "Google Calendar isn't installed", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpenAllApps = { screen = Screen.AllApps() },
                                onOpenAllAppsSearch = { screen = Screen.AllApps(focusSearch = true) },
                                onOpenSettings = { cameFromBackground = false; openDraft(settings); screen = Screen.Settings },
                                onLaunch = launch,
                                showInputs = showInputs,
                                onAction = { action ->
                                    if (!container.systemIntents.perform(action)) {
                                        Toast.makeText(this@MainActivity, "Not available on this TV", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val WATCH_NEXT_REQUEST = 7
        const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
        const val CALENDAR_PACKAGE = "com.google.android.calendar"
    }
}
