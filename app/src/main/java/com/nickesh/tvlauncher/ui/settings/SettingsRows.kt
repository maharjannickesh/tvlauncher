package com.nickesh.tvlauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.ui.components.focusGlow
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

/** One focusable settings row: label left, value/trailing right. Left/right keys are consumed when handlers are given. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dpx())
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.DirectionLeft && onLeft != null -> { onLeft(); true }
                    e.key == Key.DirectionRight && onRight != null -> { onRight(); true }
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dpx())),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x14FFFFFF),
            focusedContainerColor = Color(0x29FFFFFF),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dpx(), Color(0x1FFFFFFF)), shape = RoundedCornerShape(18.dpx())),
            focusedBorder = Border(BorderStroke(3.dpx(), Color.White), shape = RoundedCornerShape(18.dpx())),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = focusGlow()),
    ) {
        Row(
            Modifier.fillMaxWidth().height(72.dpx()).padding(horizontal = 28.dpx()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 26.spx())
            trailing()
        }
    }
}

@Composable
fun ValueText(value: String) =
    Text(value, color = Color(0xCCFFFFFF), fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 24.spx())

@Composable
fun SwitchVisual(on: Boolean) {
    Box(
        Modifier.size(72.dpx(), 40.dpx())
            .background(if (on) Color(0xFF7FE0C2) else Color(0x40FFFFFF), RoundedCornerShape(20.dpx()))
            .padding(4.dpx()),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(32.dpx()).background(Color.White, CircleShape))
    }
}

@Composable
fun TextSettingRow(label: String, value: String, onCommit: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }
    val requester = remember { FocusRequester() }
    BackHandler(enabled = editing) { editing = false }
    LaunchedEffect(editing) { if (editing) runCatching { requester.requestFocus() } }
    SettingRow(label, onClick = { draft = value; editing = true }) {
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontFamily = Outfit, fontSize = 24.spx()),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val t = draft.trim()
                    if (t.isNotEmpty()) onCommit(t)
                    editing = false
                }),
                modifier = Modifier.size(width = 360.dpx(), height = 40.dpx()).focusRequester(requester),
            )
        } else {
            ValueText(value)
        }
    }
}
