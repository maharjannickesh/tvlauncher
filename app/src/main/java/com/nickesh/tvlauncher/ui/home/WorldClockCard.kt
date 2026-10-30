package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
fun WorldClockCard(clock: ClockSnapshot, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dpx())
    Surface(
        onClick = onClick,
        modifier = modifier.height(152.dpx()),
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
            Row(
                Modifier.fillMaxWidth().height(152.dpx()).padding(horizontal = 28.dpx(), vertical = 22.dpx()),
                horizontalArrangement = Arrangement.spacedBy(26.dpx()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dial(clock, accent, Modifier.size(104.dpx()))
                Column(Modifier.wrapContentHeight(Alignment.CenterVertically, unbounded = true), verticalArrangement = Arrangement.spacedBy(2.dpx())) {
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
                        fontSize = 46.spx(),
                        lineHeight = (46 * 1.05f).toInt().spx(),
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                    Text(clock.meta, color = Color.White.copy(alpha = 0.75f), fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 20.spx(), lineHeight = 23.spx(), maxLines = 1)
                }
            }
            Icon(
                if (clock.isDay) Icons.Sun else Icons.Moon,
                null,
                Modifier.align(Alignment.TopEnd).padding(top = 22.dpx(), end = 24.dpx()).size(26.dpx()),
                tint = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

/** 100-unit SVG dial from the mockup, scaled to the canvas. */
@Composable
private fun Dial(clock: ClockSnapshot, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val c = p(50f, 50f)
        drawCircle(if (clock.isDay) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.28f), 47f * u, c)
        drawCircle(Color.White.copy(alpha = 0.28f), 47f * u, c, style = Stroke(1.5f * u))
        val tick = Color.White.copy(alpha = 0.6f)
        listOf(p(50f, 8f) to p(50f, 14f), p(92f, 50f) to p(86f, 50f), p(50f, 92f) to p(50f, 86f), p(8f, 50f) to p(14f, 50f))
            .forEach { (a, b) -> drawLine(tick, a, b, 2.5f * u, StrokeCap.Round) }
        hand(clock.hourDeg, c, Color.White, 4.5f * u, p(50f, 50f), p(50f, 27f))
        hand(clock.minuteDeg, c, Color.White, 3f * u, p(50f, 50f), p(50f, 16f))
        hand(clock.secondDeg, c, accent, 1.5f * u, p(50f, 58f), p(50f, 14f))
        drawCircle(accent, 3.5f * u, c)
    }
}

private fun DrawScope.hand(deg: Float, pivot: Offset, color: Color, width: Float, from: Offset, to: Offset) {
    rotate(deg, pivot) { drawLine(color, from, to, width, StrokeCap.Round) }
}
