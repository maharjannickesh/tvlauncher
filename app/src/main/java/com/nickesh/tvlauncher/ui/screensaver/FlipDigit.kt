package com.nickesh.tvlauncher.ui.screensaver

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.ui.theme.RobotoMono
import com.nickesh.tvlauncher.ui.theme.dpx

private val TopColor = Color(0xFF23262E)
private val BottomColor = Color(0xFF1C1F26)
private val TopDigit = Color(0xFFEEF1F6)
private val BottomDigit = Color(0xFFE2E6EE)
private val Hinge = Color(0xFF05070D)

/**
 * One split-flap card. Static halves show the new value on top and the previous one below; the two
 * animated halves fold the old top away (0 to -90, pivot bottom) then unfold the new bottom (90 to 0, pivot top).
 */
@Composable
fun FlipDigit(value: Char, width: Dp, height: Dp, fontSize: TextUnit) {
    var current by remember { mutableStateOf(value) }
    var previous by remember { mutableStateOf(value) }
    val topRotation = remember { Animatable(-90f) } // resting: folded away
    val bottomRotation = remember { Animatable(0f) } // resting: flat over the static bottom

    LaunchedEffect(value) {
        if (value == current) return@LaunchedEffect
        previous = current
        current = value
        topRotation.snapTo(0f)
        bottomRotation.snapTo(90f)
        topRotation.animateTo(-90f, tween(300, easing = EaseIn))
        bottomRotation.animateTo(0f, tween(300, easing = EaseOut))
        previous = current
    }

    val radius = 26.dpx()
    val topShape = RoundedCornerShape(topStart = radius, topEnd = radius)
    val bottomShape = RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    val camera = 12f * LocalDensity.current.density

    Box(Modifier.size(width, height).shadow(24.dp, RoundedCornerShape(radius), ambientColor = Color.Black.copy(alpha = 0.55f), spotColor = Color.Black.copy(alpha = 0.55f))) {
        Half(current, true, width, height, fontSize, TopColor, TopDigit, topShape, Modifier.align(Alignment.TopStart))
        Half(previous, false, width, height, fontSize, BottomColor, BottomDigit, bottomShape, Modifier.align(Alignment.BottomStart))
        Half(
            previous, true, width, height, fontSize, TopColor, TopDigit, topShape,
            Modifier.align(Alignment.TopStart).graphicsLayer {
                rotationX = topRotation.value
                cameraDistance = camera
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
        )
        Half(
            current, false, width, height, fontSize, BottomColor, BottomDigit, bottomShape,
            Modifier.align(Alignment.BottomStart).graphicsLayer {
                rotationX = bottomRotation.value
                cameraDistance = camera
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
        )
        Box(Modifier.align(Alignment.Center).size(width, 4.dpx()).background(Hinge))
        Box(Modifier.align(Alignment.CenterStart).offset(x = (-5).dpx()).size(10.dpx(), 18.dpx()).clip(RoundedCornerShape(4.dpx())).background(Hinge))
        Box(Modifier.align(Alignment.CenterEnd).offset(x = 5.dpx()).size(10.dpx(), 18.dpx()).clip(RoundedCornerShape(4.dpx())).background(Hinge))
    }
}

@Composable
private fun Half(
    char: Char,
    top: Boolean,
    width: Dp,
    height: Dp,
    fontSize: TextUnit,
    fill: Color,
    digit: Color,
    shape: Shape,
    modifier: Modifier,
) {
    Box(modifier.size(width, height / 2).clip(shape).background(fill).clipToBounds()) {
        Box(
            Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(width, height).offset(y = if (top) 0.dp else -(height / 2)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                char.toString(),
                color = digit,
                fontFamily = RobotoMono,
                fontWeight = FontWeight.W600,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
        }
    }
}
