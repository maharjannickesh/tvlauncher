package com.nickesh.tvlauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.data.settings.BackgroundSource
import com.nickesh.tvlauncher.data.settings.ChangeInterval
import com.nickesh.tvlauncher.data.settings.SOLID_COLOR_OPTIONS
import com.nickesh.tvlauncher.data.settings.Scene
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.ui.background.Background
import com.nickesh.tvlauncher.ui.components.Icons
import com.nickesh.tvlauncher.ui.components.RoundButton
import com.nickesh.tvlauncher.ui.components.focusGlow
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx

private val Dim80 = Color(0xCCFFFFFF)
private val Ink = Color(0xFF0D1020)

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, color = Dim80, fontFamily = Outfit, fontWeight = FontWeight.W500, fontSize = 22.spx(), letterSpacing = 1.spx())

/** Focusable selectable surface: ring when selected, white focus ring + scale + glow when focused. */
@Composable
private fun Pick(
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedRing: Color = Color.White,
    unselectedRing: Color = Color(0x1AFFFFFF),
    fill: Color = Color.Transparent,
    ringWidth: Int = 3,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fill, focusedContainerColor = fill,
            contentColor = Color.White, focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(ringWidth.dpx(), if (selected) selectedRing else unselectedRing), shape = shape),
            focusedBorder = Border(BorderStroke(3.dpx(), Color.White), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = focusGlow()),
    ) { content() }
}

private val sourceDefs = listOf(
    Triple(BackgroundSource.PHOTOS, "Google Photos", "Coming soon"),
    Triple(BackgroundSource.SCENES, "Built-in scenes", "Dusk, Night and Forest illustrations"),
    Triple(BackgroundSource.COLOR, "Solid color", "A calm, plain backdrop"),
)

private val intervalDefs = listOf(
    ChangeInterval.M5 to "5 min", ChangeInterval.M15 to "15 min", ChangeInterval.H1 to "1 hour",
    ChangeInterval.DAILY to "Daily", ChangeInterval.NEVER to "Never",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundScreen(
    settings: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val selectedRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedRow.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        Background(settings, dim = 0.72f, scrim = false)
        Column(Modifier.fillMaxSize().padding(start = 96.dpx(), end = 96.dpx(), top = 48.dpx(), bottom = 32.dpx())) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dpx())) {
                RoundButton(Icons.Back, "Back", onBack)
                Column(verticalArrangement = Arrangement.spacedBy(4.dpx())) {
                    Text("Background", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 48.spx())
                    Text(
                        "Choose what shows behind your home screen and screensaver",
                        color = Color(0xBFFFFFFF), fontFamily = Outfit, fontSize = 22.spx(),
                    )
                }
            }
            Row(Modifier.fillMaxSize().padding(top = 32.dpx()), horizontalArrangement = Arrangement.spacedBy(48.dpx())) {
                Column(Modifier.width(520.dpx()), verticalArrangement = Arrangement.spacedBy(16.dpx())) {
                    Label("SOURCE")
                    sourceDefs.forEach { (src, label, sub) ->
                        val on = settings.backgroundSource == src
                        val enabled = src != BackgroundSource.PHOTOS
                        Pick(
                            selected = on,
                            shape = RoundedCornerShape(22.dpx()),
                            onClick = { onUpdate { it.copy(backgroundSource = src) } },
                            modifier = Modifier.fillMaxWidth().height(96.dpx()).then(if (on) Modifier.focusRequester(selectedRow) else Modifier).alpha(if (enabled) 1f else 0.5f)
                                .focusProperties { canFocus = enabled },
                            unselectedRing = Color(0x1AFFFFFF),
                            fill = if (on) Color(0x29FFFFFF) else Color(0x0FFFFFFF),
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 28.dpx()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dpx()),
                            ) {
                                Box(
                                    Modifier.size(28.dpx()).border(3.dpx(), Color.White, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.size(12.dpx()).background(if (on) Color.White else Color.Transparent, CircleShape))
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dpx())) {
                                    Text(label, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W500, fontSize = 28.spx())
                                    Text(sub, color = Color(0xB8FFFFFF), fontFamily = Outfit, fontSize = 19.spx())
                                }
                            }
                        }
                    }
                    Label("CHANGE PHOTO", Modifier.padding(top = 8.dpx()))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dpx()),
                        verticalArrangement = Arrangement.spacedBy(12.dpx()),
                    ) {
                        intervalDefs.forEach { (iv, label) ->
                            val on = settings.changeInterval == iv
                            Pick(
                                selected = on, shape = RoundedCornerShape(28.dpx()),
                                onClick = { onUpdate { it.copy(changeInterval = iv) } },
                                modifier = Modifier.height(56.dpx()),
                                unselectedRing = Color(0x33FFFFFF), ringWidth = 2,
                                fill = if (on) Color.White else Color.Transparent,
                            ) {
                                Box(Modifier.fillMaxHeight().padding(horizontal = 24.dpx()), contentAlignment = Alignment.Center) {
                                    Text(
                                        label, color = if (on) Ink else Color.White, fontFamily = Outfit,
                                        fontWeight = FontWeight.W500, fontSize = 22.spx(), maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    Pick(
                        selected = false, shape = RoundedCornerShape(22.dpx()),
                        onClick = { onUpdate { it.copy(shuffle = !it.shuffle) } },
                        modifier = Modifier.fillMaxWidth().height(80.dpx()),
                        unselectedRing = Color(0x24FFFFFF), ringWidth = 1, fill = Color(0x14FFFFFF),
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 28.dpx()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Shuffle photos", color = Color.White, fontFamily = Outfit, fontSize = 26.spx())
                            SwitchVisual(settings.shuffle)
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    when (settings.backgroundSource) {
                        BackgroundSource.COLOR -> Column(verticalArrangement = Arrangement.spacedBy(18.dpx())) {
                            Label("SOLID COLOR")
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dpx())) {
                                SOLID_COLOR_OPTIONS.forEach { c ->
                                    Pick(
                                        selected = settings.solidColorArgb == c, shape = CircleShape,
                                        onClick = { onUpdate { it.copy(solidColorArgb = c) } },
                                        modifier = Modifier.size(120.dpx()),
                                        unselectedRing = Color(0x26FFFFFF),
                                        fill = Color(c),
                                    ) {}
                                }
                            }
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(18.dpx())) {
                            Label("BUILT-IN SCENES")
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dpx())) {
                                Scene.entries.forEach { sc ->
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dpx())) {
                                        Pick(
                                            selected = settings.scene == sc, shape = RoundedCornerShape(20.dpx()),
                                            onClick = { onUpdate { it.copy(backgroundSource = BackgroundSource.SCENES, scene = sc) } },
                                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                            unselectedRing = Color(0x1AFFFFFF),
                                        ) {
                                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dpx()))) {
                                                Background(
                                                    Settings(backgroundSource = BackgroundSource.SCENES, scene = sc),
                                                    scrim = false,
                                                )
                                            }
                                        }
                                        Text(
                                            sc.name.lowercase().replaceFirstChar { it.uppercase() },
                                            color = Color.White, fontFamily = Outfit, fontSize = 24.spx(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "Photos are dimmed slightly behind the clock and weather so text stays readable.",
                        color = Color(0xB8FFFFFF), fontFamily = Outfit, fontSize = 20.spx(),
                    )
                }
            }
        }
    }
}
