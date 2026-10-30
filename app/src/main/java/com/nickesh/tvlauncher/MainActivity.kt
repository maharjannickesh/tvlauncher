package com.nickesh.tvlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.ui.home.HomeScreen
import com.nickesh.tvlauncher.ui.home.HomeViewModel
import com.nickesh.tvlauncher.ui.theme.TvLauncherTheme
import com.nickesh.tvlauncher.ui.welcome.WelcomeScreen
import kotlinx.coroutines.delay

sealed interface Screen {
    data object Welcome : Screen
    data object Home : Screen
    data object AllApps : Screen
    data object Settings : Screen
    data object Background : Screen
}

private val ScreenSaver = Saver<Screen, String>(
    save = { it.toString() },
    restore = {
        when (it) {
            "Welcome" -> Screen.Welcome
            "AllApps" -> Screen.AllApps
            "Settings" -> Screen.Settings
            "Background" -> Screen.Background
            else -> Screen.Home
        }
    },
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TvLauncherApp).container
        setContent {
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = Settings())
            val accent = Color(settings.accentArgb)
            // Shown on every cold start; survives rotation/recreation only.
            var welcomeDone by rememberSaveable { mutableStateOf(false) }
            var screen by rememberSaveable(stateSaver = ScreenSaver) {
                mutableStateOf<Screen>(if (welcomeDone) Screen.Home else Screen.Welcome)
            }
            val homeViewModel = viewModel { HomeViewModel(container) }
            val homeState by homeViewModel.state.collectAsStateWithLifecycle()
            TvLauncherTheme(accent = accent) {
                Crossfade(targetState = screen, animationSpec = tween(400), label = "screen") { s ->
                    when (s) {
                        Screen.Welcome -> {
                            // Task 6 replaces this fixed delay with the readiness gate + 4 s cap.
                            LaunchedEffect(Unit) {
                                delay(1200)
                                welcomeDone = true
                                screen = Screen.Home
                            }
                            WelcomeScreen(settings.name, accent, settings)
                        }
                        else -> HomeScreen(
                            state = homeState,
                            onOpenAllApps = { screen = Screen.AllApps },
                            onOpenSettings = { screen = Screen.Settings },
                            onLaunch = { /* Task 6 */ },
                            onAction = { /* Task 8 */ },
                        )
                    }
                }
            }
        }
    }
}
