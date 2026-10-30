package com.nickesh.tvlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import com.nickesh.tvlauncher.R

object Design {
    const val WIDTH = 1920f
    const val HEIGHT = 1080f
}

val LocalDesignScale: ProvidableCompositionLocal<Float> = staticCompositionLocalOf { 0.5f }

/** Design pixels (1920x1080 mockup space) to Dp. */
@Composable
fun Int.dpx(): Dp = (this * LocalDesignScale.current).dp

/** Design pixels (1920x1080 mockup space) to sp. */
@Composable
fun Int.spx(): TextUnit = (this * LocalDesignScale.current).sp

val Outfit = FontFamily(
    Font(R.font.outfit_200, FontWeight.W200),
    Font(R.font.outfit_300, FontWeight.W300),
    Font(R.font.outfit_400, FontWeight.W400),
    Font(R.font.outfit_500, FontWeight.W500),
    Font(R.font.outfit_600, FontWeight.W600),
)

val RobotoMono = FontFamily(
    Font(R.font.roboto_mono_500, FontWeight.W500),
    Font(R.font.roboto_mono_600, FontWeight.W600),
)

object Glass {
    val fill = Color(0x1AFFFFFF)
    val border = Color(0x29FFFFFF)
    val radiusPx = 28
}

val AccentOptions: List<Color> = listOf(
    Color(0xFF8FB8FF),
    Color(0xFFFFB27A),
    Color(0xFF7FE0C2),
    Color(0xFFF2A7D8),
)

private fun outfitTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = Outfit),
        displayMedium = base.displayMedium.copy(fontFamily = Outfit),
        displaySmall = base.displaySmall.copy(fontFamily = Outfit),
        headlineLarge = base.headlineLarge.copy(fontFamily = Outfit),
        headlineMedium = base.headlineMedium.copy(fontFamily = Outfit),
        headlineSmall = base.headlineSmall.copy(fontFamily = Outfit),
        titleLarge = base.titleLarge.copy(fontFamily = Outfit),
        titleMedium = base.titleMedium.copy(fontFamily = Outfit),
        titleSmall = base.titleSmall.copy(fontFamily = Outfit),
        bodyLarge = base.bodyLarge.copy(fontFamily = Outfit),
        bodyMedium = base.bodyMedium.copy(fontFamily = Outfit),
        bodySmall = base.bodySmall.copy(fontFamily = Outfit),
        labelLarge = base.labelLarge.copy(fontFamily = Outfit),
        labelMedium = base.labelMedium.copy(fontFamily = Outfit),
        labelSmall = base.labelSmall.copy(fontFamily = Outfit),
    )
}

@Composable
fun TvLauncherTheme(accent: Color, content: @Composable () -> Unit) {
    // accent is consumed by focus glow in later tasks; carried here so every screen shares one entry point.
    val scale = LocalConfiguration.current.screenWidthDp / Design.WIDTH
    CompositionLocalProvider(LocalDesignScale provides scale) {
        MaterialTheme(typography = outfitTypography(), content = content)
    }
}
