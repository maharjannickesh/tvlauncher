package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.domain.clock.ClockSnapshot
import com.nickesh.tvlauncher.ui.components.Icons
import com.nickesh.tvlauncher.ui.components.focusGlow
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

@Composable
fun WorldClockCard(clock: ClockSnapshot, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier, up: FocusRequester = FocusRequester.Default) {
    val shape = RoundedCornerShape(28.dpx())
    Surface(
        onClick = onClick,
        modifier = modifier.height(152.dpx()).focusProperties { this.up = up },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x1AFFFFFF),
            focusedContainerColor = Color(0x1AFFFFFF),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dpx(), Color(0x29FFFFFF)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dpx(), Color.White), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = focusGlow(accent)),
    ) {
        Box(Modifier.fillMaxWidth().height(152.dpx())) {
            Column(
                Modifier.align(Alignment.CenterStart).padding(horizontal = 28.dpx()),
                verticalArrangement = Arrangement.spacedBy(2.dpx()),
            ) {
                Text(clock.city, fontFamily = Outfit, fontWeight = FontWeight.W500, fontSize = 26.spx(), lineHeight = 30.spx(), maxLines = 1)
                Text(
                    buildAnnotatedString {
                        append(clock.text.time)
                        if (clock.text.ampm.isNotEmpty()) {
                            append(" ")
                            withStyle(SpanStyle(fontSize = 22.spx(), color = Color.White.copy(alpha = 0.8f))) {
                                append(clock.text.ampm)
                            }
                        }
                    },
                    fontFamily = Outfit,
                    fontWeight = FontWeight.W300,
                    fontSize = 52.spx(),
                    lineHeight = 56.spx(),
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
                Text(clock.meta, color = Color.White.copy(alpha = 0.75f), fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 20.spx(), lineHeight = 23.spx(), maxLines = 1)
            }
            val weather = clock.weather
            val dim = if (weather == null) 0.5f else 1f
            val icon = when {
                weather == null -> if (clock.isDay) Icons.Sun else Icons.Moon
                weather.kind == WeatherKind.SUN -> if (clock.isDay) Icons.Sun else Icons.Moon
                weather.kind == WeatherKind.PARTLY -> Icons.Partly
                weather.kind == WeatherKind.CLOUD -> Icons.Cloud
                else -> Icons.Rain
            }
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 22.dpx(), end = 24.dpx()),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dpx()),
            ) {
                Icon(icon, null, Modifier.size(40.dpx()), tint = Color.White.copy(alpha = dim))
                Text(
                    weather?.temp ?: "--°",
                    color = Color.White.copy(alpha = dim),
                    fontFamily = Outfit,
                    fontWeight = FontWeight.W400,
                    fontSize = 26.spx(),
                    lineHeight = 30.spx(),
                    maxLines = 1,
                )
            }
        }
    }
}
