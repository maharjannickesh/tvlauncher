package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.domain.clock.TimeText
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

@Composable
fun HeroClock(hero: TimeText, dateLine: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dpx())) {
        // Compose seats the 0.9 line box higher than CSS does; nudge the glyphs onto the mockup baseline.
        Row(Modifier.offset(y = 38.dpx()), horizontalArrangement = Arrangement.spacedBy(18.dpx())) {
            Text(
                hero.time,
                modifier = Modifier.alignBy(androidx.compose.ui.layout.LastBaseline),
                color = Color.White,
                style = TextStyle(
                    fontFamily = Outfit,
                    fontWeight = FontWeight.W200,
                    fontSize = 220.spx(),
                    lineHeight = (220 * 0.9f).toInt().spx(),
                    letterSpacing = (-8).spx(),
                    fontFeatureSettings = "tnum",
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                ),
            )
            if (hero.ampm.isNotEmpty()) {
                Text(
                    hero.ampm,
                    modifier = Modifier.alignBy(androidx.compose.ui.layout.LastBaseline),
                    color = Color.White.copy(alpha = 0.85f),
                    fontFamily = Outfit,
                    fontWeight = FontWeight.W300,
                    fontSize = 52.spx(),
                )
            }
        }
        Text(
            dateLine,
            modifier = Modifier.padding(start = 8.dpx()),
            color = Color.White.copy(alpha = 0.88f),
            fontFamily = Outfit,
            fontWeight = FontWeight.W300,
            fontSize = 36.spx(),
        )
    }
}
