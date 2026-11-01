package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.domain.agenda.BlockStatus
import com.nickesh.tvlauncher.domain.agenda.UpNextRow
import com.nickesh.tvlauncher.ui.components.LocalAccent
import com.nickesh.tvlauncher.ui.components.focusGlow
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

/** The next calendar events. The whole card is one focus stop; Select opens [onClick] (the calendar app). */
@Composable
fun UpNextCard(rows: List<UpNextRow>, status: BlockStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(28.dpx())
    Surface(
        onClick = onClick,
        modifier = modifier,
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
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dpx(), vertical = 28.dpx()),
            verticalArrangement = Arrangement.spacedBy(18.dpx()),
        ) {
            AgendaHeader("UP NEXT", "Google Calendar")
            val line = status.copy(unreachable = "Can't reach calendar", empty = "Nothing scheduled")
            when {
                status == BlockStatus.OK -> {
                    // One shared time column sized to the widest visible time, so every title starts at the same x.
                    val timeStyle = timeStyle()
                    val measurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val widest = remember(rows, timeStyle, measurer) {
                        rows.maxOfOrNull { measurer.measure(it.time, timeStyle, maxLines = 1, softWrap = false).size.width } ?: 0
                    }
                    val timeWidth = with(density) { widest.toDp() } + 12.dpx()
                    rows.forEach { r -> EventLine(r, accent, timeStyle, timeWidth) }
                }
                line != null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { StatusLine(line) }
                else -> Unit // LOADING: empty body, the frame and header keep the layout still
            }
        }
    }
}

@Composable
private fun timeStyle() = TextStyle(
    fontFeatureSettings = "tnum",
    fontFamily = Outfit,
    fontWeight = FontWeight.W500,
    fontSize = 22.spx(),
)

@Composable
private fun EventLine(r: UpNextRow, accent: Color, timeStyle: TextStyle, timeWidth: Dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            r.time,
            modifier = Modifier.widthIn(min = timeWidth).padding(end = 12.dpx()),
            color = accent,
            style = timeStyle,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            r.title,
            modifier = Modifier.weight(1f),
            fontFamily = Outfit,
            fontWeight = FontWeight.W400,
            fontSize = 22.spx(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        r.suffix?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 12.dpx()),
                color = Color(0x99FFFFFF),
                fontFamily = Outfit,
                fontWeight = FontWeight.W400,
                fontSize = 18.spx(),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
