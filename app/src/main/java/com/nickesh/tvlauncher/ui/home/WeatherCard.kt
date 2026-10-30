package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.ui.components.Icons
import com.nickesh.tvlauncher.ui.components.focusGlow
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

internal fun WeatherKind.icon(): ImageVector = when (this) {
    WeatherKind.SUN -> Icons.SunForecast
    WeatherKind.PARTLY -> Icons.Partly
    WeatherKind.CLOUD -> Icons.Cloud
    WeatherKind.RAIN -> Icons.Rain
}

@Composable
fun WeatherCard(weather: WeatherUi, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dpx())
    Surface(
        onClick = onClick,
        modifier = modifier.width(580.dpx()),
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
        Column(Modifier.padding(horizontal = 32.dpx(), vertical = 28.dpx()), verticalArrangement = Arrangement.spacedBy(22.dpx())) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dpx()), verticalAlignment = Alignment.CenterVertically) {
                if (weather.kind == WeatherKind.PARTLY) {
                    Image(Icons.PartlyCard, null, Modifier.size(84.dpx()))
                } else {
                    Icon(weather.kind.icon(), null, Modifier.size(84.dpx()), tint = Color.White)
                }
                Text(
                    weather.now,
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.W300,
                    fontSize = 96.spx(),
                    lineHeight = 96.spx(),
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dpx()),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(weather.condition, fontFamily = Outfit, fontWeight = FontWeight.W500, fontSize = 28.spx(), textAlign = TextAlign.End)
                    Text(
                        "H ${weather.hi}  ·  L ${weather.lo}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontFamily = Outfit,
                        fontWeight = FontWeight.W400,
                        fontSize = 22.spx(),
                    )
                    Text(
                        weather.city,
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = Outfit,
                        fontWeight = FontWeight.W400,
                        fontSize = 20.spx(),
                    )
                }
            }
            Column(Modifier.fillMaxWidth().height(1.dpx()).background(Color(0x29FFFFFF))) {}
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dpx())) {
                weather.forecast.forEach { f ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dpx()),
                    ) {
                        Text(f.day, color = Color.White.copy(alpha = 0.78f), fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 20.spx())
                        Icon(f.kind.icon(), null, Modifier.size(34.dpx()), tint = Color.White)
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.W500)) { append(f.hi) }
                                append(" ")
                                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.65f))) { append(f.lo) }
                            },
                            fontFamily = Outfit,
                            fontWeight = FontWeight.W400,
                            fontSize = 22.spx(),
                        )
                    }
                }
            }
        }
    }
}
