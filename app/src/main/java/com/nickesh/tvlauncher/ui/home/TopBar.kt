package com.nickesh.tvlauncher.ui.home

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.ui.components.Icons
import com.nickesh.tvlauncher.ui.components.RoundButton
import com.nickesh.tvlauncher.ui.components.focusGlow
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

@Composable
fun TopBar(
    greeting: String,
    homeCity: String,
    accent: Color,
    onOpenSettings: () -> Unit,
    onAction: (SystemAction) -> Unit,
    onOpenAllAppsSearch: () -> Unit,
    showInputs: Boolean = true,
    modifier: Modifier = Modifier,
    assistRequester: FocusRequester = remember { FocusRequester() },
) {
    Row(
        modifier.fillMaxWidth().height(56.dpx()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dpx()), verticalAlignment = Alignment.CenterVertically) {
            Text(greeting, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 30.spx())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dpx()), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Pin, null, Modifier.size(20.dpx()), tint = accent)
                Text(
                    homeCity,
                    color = Color.White.copy(alpha = 0.78f),
                    fontFamily = Outfit,
                    fontWeight = FontWeight.W400,
                    fontSize = 22.spx(),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dpx()), verticalAlignment = Alignment.CenterVertically) {
            AssistPill(accent, assistRequester) { onAction(SystemAction.ASSIST) }
            RoundButton(Icons.Search, "Search", onOpenAllAppsSearch)
            if (showInputs) RoundButton(Icons.Inputs, "Input source", { onAction(SystemAction.INPUTS) })
            RoundButton(Icons.Settings, "Settings", onOpenSettings)
            RoundButton(Icons.Profile, "Profile", { onAction(SystemAction.PROFILE) })
        }
    }
}

@Composable
private fun AssistPill(accent: Color, requester: FocusRequester, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dpx())
    Surface(
        onClick = onClick,
        modifier = Modifier.height(56.dpx()).focusRequester(requester)
            .focusProperties { left = FocusRequester.Cancel }, // nothing to the left in the top bar: stay put, don't jump to the clock cards
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x2EFFFFFF),
            focusedContainerColor = Color(0x2EFFFFFF),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dpx(), Color(0x47FFFFFF)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dpx(), Color.White), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = focusGlow(accent)),
    ) {
        Row(
            Modifier.height(56.dpx()).padding(start = 18.dpx(), end = 26.dpx()),
            horizontalArrangement = Arrangement.spacedBy(12.dpx()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dpx()).background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Mic, null, Modifier.size(20.dpx()), tint = Color(0xFF0B0E1A))
            }
            Text("Ask Gemini", fontFamily = Outfit, fontWeight = FontWeight.W500, fontSize = 22.spx())
        }
    }
}
