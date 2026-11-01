package com.nickesh.tvlauncher.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.domain.agenda.BlockStatus
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.spx

/** Header shared by the agenda blocks: an upper-case label and a muted source on the right. */
@Composable
internal fun AgendaHeader(label: String, source: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = Outfit,
            fontWeight = FontWeight.W500,
            fontSize = 22.spx(),
            letterSpacing = 1.spx(),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(source, color = Color(0x99FFFFFF), fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 20.spx(), maxLines = 1)
    }
}

/** Pinned status copy for a block; null for [BlockStatus.LOADING] (empty body) and [BlockStatus.OK]. */
internal fun BlockStatus.copy(unreachable: String, empty: String): String? = when (this) {
    BlockStatus.LOADING, BlockStatus.OK -> null
    BlockStatus.NOT_CONNECTED -> "Connect in Settings"
    BlockStatus.UNREACHABLE -> unreachable
    BlockStatus.EMPTY -> empty
}

@Composable
internal fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = Color(0x99FFFFFF),
        fontFamily = Outfit,
        fontWeight = FontWeight.W400,
        fontSize = 20.spx(),
        textAlign = TextAlign.Center,
    )
}
