package com.nickesh.tvlauncher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import com.nickesh.tvlauncher.ui.theme.dpx

/** Accent colour for focus glows; provided by the screen that owns the settings. */
val LocalAccent = staticCompositionLocalOf { Color(0xFF8FB8FF) }

@Composable
fun focusGlow(accent: Color = LocalAccent.current): Glow =
    Glow(elevationColor = accent.copy(alpha = 0.33f), elevation = 18.dpx())

@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(56.dpx()),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x1AFFFFFF),
            focusedContainerColor = Color(0x1AFFFFFF),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dpx(), Color(0x2EFFFFFF)), shape = CircleShape),
            focusedBorder = Border(BorderStroke(3.dpx(), Color.White), shape = CircleShape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = focusGlow()),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size(56.dpx()), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, Modifier.size(24.dpx()), tint = Color.White)
        }
    }
}
