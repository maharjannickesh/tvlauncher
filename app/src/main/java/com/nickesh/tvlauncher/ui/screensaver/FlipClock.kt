package com.nickesh.tvlauncher.ui.screensaver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx
import java.time.ZonedDateTime

data class FlipDigits(val groups: List<String>, val ampm: String)

object FlipDigitsCalc {
    fun from(t: ZonedDateTime, use24h: Boolean, showSeconds: Boolean): FlipDigits {
        val hour = if (use24h) t.hour else if (t.hour % 12 == 0) 12 else t.hour % 12
        val groups = buildList {
            add("%02d".format(hour))
            add("%02d".format(t.minute))
            if (showSeconds) add("%02d".format(t.second))
        }
        return FlipDigits(groups, if (use24h) "" else if (t.hour < 12) "AM" else "PM")
    }
}

private val DotColor = Color.White.copy(alpha = 0.75f)

@Composable
private fun Colon() {
    Column(Modifier.padding(horizontal = 8.dpx()), verticalArrangement = Arrangement.spacedBy(56.dpx())) {
        repeat(2) { Box(Modifier.size(26.dpx()).clip(CircleShape).background(DotColor)) }
    }
}

@Composable
fun FlipClock(digits: FlipDigits, showSeconds: Boolean) {
    val w = if (showSeconds) 200 else 250
    val h = if (showSeconds) 290 else 360
    val fs = if (showSeconds) 220 else 280
    Row(horizontalArrangement = Arrangement.spacedBy(28.dpx()), verticalAlignment = Alignment.CenterVertically) {
        digits.groups.forEachIndexed { i, group ->
            Row(horizontalArrangement = Arrangement.spacedBy(28.dpx()), verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) Colon()
                Row(horizontalArrangement = Arrangement.spacedBy(16.dpx())) {
                    group.forEach { c -> FlipDigit(c, w.dpx(), h.dpx(), fs.spx()) }
                }
            }
        }
        if (digits.ampm.isNotEmpty()) {
            Text(
                digits.ampm,
                Modifier.align(Alignment.Bottom).padding(bottom = 18.dpx()),
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = Outfit,
                fontWeight = FontWeight.W400,
                fontSize = 44.spx(),
                letterSpacing = 2.spx(),
            )
        }
    }
}
