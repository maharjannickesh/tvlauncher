package com.nickesh.tvlauncher.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import com.nickesh.tvlauncher.data.settings.BackgroundSource
import com.nickesh.tvlauncher.data.settings.Scene
import com.nickesh.tvlauncher.data.settings.Settings

data class Star(val cx: Float, val cy: Float, val r: Float, val opacity: Float)

data class ScenePalette(
    val skyTop: Color,
    val skyMid: Color,
    val horizon: Color,
    val orb: Color,
    val orbR: Float,
    val m1: Color,
    val m2: Color,
    val m3: Color,
    val m4: Color,
    val stars: List<Star>,
)

private val NightStars = listOf(
    Star(140f, 90f, 1.6f, 0.8f),
    Star(320f, 180f, 1.2f, 0.6f),
    Star(610f, 70f, 1.8f, 0.9f),
    Star(840f, 210f, 1.1f, 0.5f),
    Star(1010f, 120f, 1.5f, 0.7f),
    Star(1330f, 60f, 1.2f, 0.6f),
    Star(1520f, 170f, 1.7f, 0.8f),
    Star(1710f, 95f, 1.3f, 0.7f),
    Star(1860f, 230f, 1.1f, 0.5f),
    Star(460f, 300f, 1.0f, 0.4f),
    Star(1180f, 280f, 1.2f, 0.5f),
    Star(760f, 340f, 0.9f, 0.4f),
)

private val Dusk = ScenePalette(
    Color(0xFF16213F), Color(0xFF4F4372), Color(0xFFE39470), Color(0xFFFFD2A0), 70f,
    Color(0xFF64568A), Color(0xFF3B3864), Color(0xFF222546), Color(0xFF11142A), emptyList(),
)
private val Night = ScenePalette(
    Color(0xFF04060F), Color(0xFF0C1734), Color(0xFF22406A), Color(0xFFE9EEFC), 34f,
    Color(0xFF1C2A4C), Color(0xFF13203E), Color(0xFF0C162E), Color(0xFF060B1A), NightStars,
)
private val Forest = ScenePalette(
    Color(0xFF0E2629), Color(0xFF3A7470), Color(0xFFC8D8B2), Color(0xFFFFF3CF), 56f,
    Color(0xFF2E5D5A), Color(0xFF1E4542), Color(0xFF133230), Color(0xFF0A1D1C), emptyList(),
)

fun scenePalette(scene: Scene): ScenePalette = when (scene) {
    Scene.DUSK -> Dusk
    Scene.NIGHT -> Night
    Scene.FOREST -> Forest
}

private const val M1 = "M0 620 L180 540 L320 590 L520 470 L700 560 L860 500 L1040 580 L1240 450 L1420 540 L1600 480 L1780 560 L1920 510 L1920 1080 L0 1080 Z"
private const val M2 = "M0 720 L160 650 L340 700 L540 610 L760 690 L960 630 L1160 710 L1380 620 L1580 690 L1760 640 L1920 680 L1920 1080 L0 1080 Z"
private const val M3 = "M0 820 L220 760 L460 800 L700 740 L940 800 L1200 750 L1460 810 L1700 770 L1920 800 L1920 1080 L0 1080 Z"
private const val M4 = "M0 930 C300 890 600 950 960 910 C1320 870 1600 940 1920 900 L1920 1080 L0 1080 Z"

@Composable
fun Background(
    settings: Settings,
    dim: Float = 0f,
    scrim: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (settings.backgroundSource == BackgroundSource.COLOR) {
            Box(Modifier.fillMaxSize().background(Color(settings.solidColorArgb)))
        } else {
            // PHOTOS is not implemented in v1: treated like SCENES.
            SceneCanvas(scenePalette(settings.scene))
        }
        if (scrim) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.38f),
                        0.32f to Color.Transparent,
                        0.52f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
            )
        }
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

@Composable
private fun SceneCanvas(p: ScenePalette) {
    val paths = remember {
        listOf(M1, M2, M3, M4).map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1920f
        drawRect(
            Brush.verticalGradient(0f to p.skyTop, 0.55f to p.skyMid, 1f to p.horizon),
        )
        scale(k, k, pivot = Offset.Zero) {
            p.stars.forEach { s ->
                drawCircle(Color.White.copy(alpha = s.opacity), s.r, Offset(s.cx, s.cy))
            }
            drawCircle(p.orb.copy(alpha = 0.9f), p.orbR, Offset(1240f, 470f))
            val fills = listOf(p.m1, p.m2, p.m3, p.m4)
            paths.forEachIndexed { i, path: Path -> drawPath(path, fills[i]) }
        }
    }
}
