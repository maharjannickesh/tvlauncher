package com.nickesh.tvlauncher.ui.screensaver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.domain.clock.ClockMath
import com.nickesh.tvlauncher.domain.clock.ClockSnapshot
import com.nickesh.tvlauncher.ui.background.Background
import com.nickesh.tvlauncher.ui.components.Icons
import com.nickesh.tvlauncher.ui.home.WeatherKind
import com.nickesh.tvlauncher.ui.home.WeatherUi
import com.nickesh.tvlauncher.ui.home.icon
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx
import java.time.ZoneId
import java.time.ZonedDateTime

private val SHIFTS = listOf(-24 to -24, 24 to -24, 24 to 24, -24 to 24)

/** Burn-in guard: design-px offset that steps to the next corner every 60 s. */
internal fun burnInShift(now: ZonedDateTime): Pair<Int, Int> = SHIFTS[((now.toEpochSecond() / 60) % 4).toInt()]

@Composable
fun ScreensaverContent(settings: Settings, weather: WeatherUi?, clocks: List<ClockSnapshot>, now: ZonedDateTime) {
    val home = now.withZoneSameInstant(ZoneId.of(settings.homeZone))
    val digits = FlipDigitsCalc.from(home, settings.use24h, settings.screensaverSeconds)
    val (dx, dy) = burnInShift(now)
    Box(Modifier.fillMaxSize()) {
        Background(settings, dim = settings.screensaverDim, scrim = false)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 96.dpx(), vertical = 72.dpx()).offset(dx.dpx(), dy.dpx()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(40.dpx(), Alignment.CenterVertically),
        ) {
            FlipClock(digits, settings.screensaverSeconds)
            Text(
                ClockMath.dateLine(home),
                color = Color.White.copy(alpha = 0.9f),
                fontFamily = Outfit,
                fontWeight = FontWeight.W300,
                fontSize = 40.spx(),
            )
            PillBar(settings, weather, clocks, Modifier.padding(top = 24.dpx()))
        }
    }
}

@Composable
private fun PillBar(settings: Settings, weather: WeatherUi?, clocks: List<ClockSnapshot>, modifier: Modifier) {
    val shape = RoundedCornerShape(40.dpx())
    Row(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dpx(), Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 36.dpx(), vertical = 20.dpx()),
        horizontalArrangement = Arrangement.spacedBy(40.dpx()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dpx()), verticalAlignment = Alignment.CenterVertically) {
            Icon((weather?.kind ?: WeatherKind.PARTLY).icon(), null, Modifier.size(40.dpx()), tint = Color.White)
            Text(weather?.now ?: "--°", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 30.spx())
            Text(weather?.city ?: settings.homeCity, color = Color.White.copy(alpha = 0.75f), fontFamily = Outfit, fontSize = 24.spx())
        }
        clocks.forEach { c ->
            Row(horizontalArrangement = Arrangement.spacedBy(40.dpx()), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(1.dpx()).height(36.dpx()).background(Color.White.copy(alpha = 0.2f)))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dpx())) {
                    Text(c.city, Modifier.alignByBaseline(), color = Color.White.copy(alpha = 0.75f), fontFamily = Outfit, fontSize = 24.spx())
                    Text(
                        c.text.time, Modifier.alignByBaseline(), color = Color.White, fontFamily = Outfit,
                        fontWeight = FontWeight.W400, fontSize = 30.spx(), style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                    if (c.text.ampm.isNotEmpty()) {
                        Text(c.text.ampm, Modifier.alignByBaseline(), color = Color.White.copy(alpha = 0.7f), fontFamily = Outfit, fontSize = 20.spx())
                    }
                }
            }
        }
    }
}
