package com.nickesh.tvlauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.ui.components.GlassCard
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

/** Modal "Discard changes?" card over the Settings screen. Back = Keep editing; focus cannot leave the card. */
@Composable
fun DiscardDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    BackHandler { onKeepEditing() }
    val keep = remember { FocusRequester() }
    val discard = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keep.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        GlassCard(
            // Opaque base under the glass so the Settings rows behind do not read through the card.
            Modifier.size(640.dpx(), 240.dpx()).background(Color(0xF0181C22), RoundedCornerShape(28.dpx()))
                .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { runCatching { keep.requestFocus() }; true }
                    Key.DirectionRight -> { runCatching { discard.requestFocus() }; true }
                    Key.DirectionUp, Key.DirectionDown -> true
                    else -> false
                }
            },
        ) {
            Column(Modifier.fillMaxSize().padding(36.dpx()), verticalArrangement = Arrangement.SpaceBetween) {
                Text("Discard changes?", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 36.spx())
                Row(horizontalArrangement = Arrangement.spacedBy(20.dpx())) {
                    Box(Modifier.width(274.dpx())) {
                        SettingRow("Keep editing", Modifier.focusRequester(keep), onClick = onKeepEditing) {}
                    }
                    Box(Modifier.width(274.dpx())) {
                        SettingRow("Discard", Modifier.focusRequester(discard), onClick = onDiscard) {}
                    }
                }
            }
        }
    }
}
