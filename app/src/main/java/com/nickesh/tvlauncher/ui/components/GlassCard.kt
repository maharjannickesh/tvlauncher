package com.nickesh.tvlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nickesh.tvlauncher.ui.theme.Glass
import com.nickesh.tvlauncher.ui.theme.dpx

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radiusPx: Int = 28,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radiusPx.dpx())
    Box(
        modifier
            .clip(shape)
            .background(Glass.fill, shape)
            .border(1.dpx(), Glass.border, shape),
        content = content,
    )
}
