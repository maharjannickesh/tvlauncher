package com.nickesh.tvlauncher.ui.welcome

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.ui.background.Background
import androidx.compose.ui.platform.LocalDensity
import com.nickesh.tvlauncher.ui.theme.LocalDesignScale
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.spx

@Composable
fun WelcomeScreen(name: String, accent: Color, settings: Settings) {
    val bandPx = 240f * LocalDesignScale.current * LocalDensity.current.density
    val t = rememberInfiniteTransition(label = "welcome")
    val glowAlpha by t.animateFloat(
        0.35f, 0.75f,
        infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glowAlpha",
    )
    val glowScale by t.animateFloat(
        0.95f, 1.05f,
        infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glowScale",
    )
    val shimmer by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "shimmer",
    )

    Box(Modifier.fillMaxSize()) {
        Background(settings)
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = glowAlpha
                scaleX = glowScale
                scaleY = glowScale
            },
        ) {
            val k = size.width / 1920f
            drawCircle(
                Brush.radialGradient(
                    listOf(accent, Color.Transparent),
                    center = center,
                    radius = 520f * k,
                ),
                radius = 520f * k,
                center = center,
            )
        }
        Text(
            "Welcome $name!!!",
            color = Color.White,
            fontSize = 120.spx(),
            fontFamily = Outfit,
            fontWeight = FontWeight.W300,
            modifier = Modifier
                .align(Alignment.Center)
                // Offscreen so SrcAtop only paints over the glyphs, not the scene behind.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val band = bandPx
                    // sweep the band from fully left of the text to fully right of it
                    val x = -band + (size.width + band) * shimmer
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0f),
                            0.5f to Color.White.copy(alpha = 0.35f),
                            1f to Color.White.copy(alpha = 0f),
                            start = Offset(x, 0f),
                            end = Offset(x + band, 0f),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                },
        )
    }
}
