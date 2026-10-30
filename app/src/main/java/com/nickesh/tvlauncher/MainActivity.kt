package com.nickesh.tvlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.domain.clock.ClockMath
import com.nickesh.tvlauncher.ui.background.Background
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.TvLauncherTheme
import com.nickesh.tvlauncher.ui.theme.spx
import com.nickesh.tvlauncher.ui.welcome.WelcomeScreen
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

sealed interface Screen {
    data object Welcome : Screen
    data object Home : Screen
    data object AllApps : Screen
    data object Settings : Screen
    data object Background : Screen
}

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
            var screen by remember {
                mutableStateOf<Screen>(if (welcomeDone) Screen.Home else Screen.Welcome)
            }
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
                        else -> HomePlaceholder(settings)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePlaceholder(settings: Settings) {
    val zone = remember(settings.homeZone) { ZoneId.of(settings.homeZone) }
    var now by remember { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) {
        while (true) {
            now = ZonedDateTime.now(zone)
            delay(60_000 - (System.currentTimeMillis() % 60_000))
        }
    }
    val t = ClockMath.timeText(now, settings.use24h)
    Box(Modifier.fillMaxSize()) {
        Background(settings)
        Text(
            t.time,
            color = Color.White,
            fontSize = 200.spx(),
            fontFamily = Outfit,
            fontWeight = FontWeight.W200,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
