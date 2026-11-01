package com.nickesh.tvlauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.data.connect.ConnectOutcome
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.domain.connect.ConnectRules
import com.nickesh.tvlauncher.ui.background.Background
import com.nickesh.tvlauncher.ui.components.Icons
import com.nickesh.tvlauncher.ui.components.RoundButton
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx
import java.net.URI

private val Dim80 = Color(0xCCFFFFFF)
private val Ok = Color(0xFF7FE0C2)
private val Bad = Color(0xFFFFB4A8)

/** The on-screen line for one submission outcome; mirrors the phone page. Null for "nothing submitted". */
fun outcomeLine(o: ConnectOutcome): String? = when (o) {
    is ConnectOutcome.CalendarAdded -> "Calendar connected · ${o.name} · ${o.events} events"
    is ConnectOutcome.TodoistConnected -> "Todoist connected · ${o.openTasks} open tasks"
    is ConnectOutcome.Failed -> o.message
    ConnectOutcome.Nothing -> null
}

/** A calendar row's name: the cached feed name, else the link's host. Never the link itself (it is secret). */
private fun calendarLabel(url: String, names: Map<String, String>): String =
    names[url] ?: runCatching { URI(url).host }.getOrNull() ?: "Calendar"

/**
 * Connect: a QR for the phone page on the left; on the right what is connected (with Disconnect) and
 * remote-typed entry rows. [url] null means no LAN address. Links and the token are never shown in full.
 */
@Composable
fun ConnectScreen(
    settings: Settings,
    url: String?,
    results: List<ConnectOutcome>,
    calendarNames: Map<String, String>,
    checking: Boolean,
    onDisconnectCalendar: (String) -> Unit,
    onDisconnectTodoist: () -> Unit,
    onTypeCalendar: (String) -> Unit,
    onTypeToken: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val calendarEntry = remember { FocusRequester() }
    val tokenEntry = remember { FocusRequester() }
    val anyConnected = settings.calendarUrls.isNotEmpty() || settings.todoistToken != null
    // Never a Disconnect row: one stray OK on entry must not remove a connection.
    LaunchedEffect(Unit) { runCatching { calendarEntry.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        Background(settings, dim = 0.72f, scrim = false)
        Column(Modifier.fillMaxSize().padding(start = 96.dpx(), end = 96.dpx(), top = 48.dpx(), bottom = 32.dpx())) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dpx())) {
                RoundButton(Icons.Back, "Back", onBack)
                Text("Connections", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 48.spx())
            }
            Row(Modifier.fillMaxSize().padding(top = 32.dpx()), horizontalArrangement = Arrangement.spacedBy(72.dpx())) {
                Column(Modifier.width(640.dpx()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dpx())) {
                    if (url != null) {
                        Box(Modifier.background(Color.White, RoundedCornerShape(24.dpx())).padding(16.dpx())) {
                            QrImage(url, 560)
                        }
                        Text(url, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W500, fontSize = 24.spx(), textAlign = TextAlign.Center)
                        Text(
                            "Scan with your phone, paste your calendar link and Todoist token",
                            color = Dim80, fontFamily = Outfit, fontSize = 24.spx(), textAlign = TextAlign.Center,
                        )
                    } else {
                        Box(
                            Modifier.size(592.dpx()).background(Color(0x14FFFFFF), RoundedCornerShape(24.dpx())),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Connect this TV to Wi-Fi first",
                                color = Color.White, fontFamily = Outfit, fontSize = 30.spx(), textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dpx()),
                            )
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dpx()),
                    verticalArrangement = Arrangement.spacedBy(10.dpx()),
                ) {
                    Text("CONNECTED", color = Dim80, fontFamily = Outfit, fontWeight = FontWeight.W500, fontSize = 22.spx(), letterSpacing = 1.spx())
                    settings.calendarUrls.forEach { u ->
                        SettingRow(calendarLabel(u, calendarNames), onClick = {
                            runCatching { calendarEntry.requestFocus() }
                            onDisconnectCalendar(u)
                        }) { ValueText("Disconnect") }
                    }
                    val token = settings.todoistToken
                    if (token != null) {
                        SettingRow(
                            "Todoist  ${ConnectRules.maskToken(token)}",
                            onClick = {
                            runCatching { tokenEntry.requestFocus() }
                            onDisconnectTodoist()
                        }) { ValueText("Disconnect") }
                    }
                    if (!anyConnected) {
                        Text("Not connected", color = Color.White, fontFamily = Outfit, fontSize = 26.spx(), modifier = Modifier.padding(start = 8.dpx(), top = 4.dpx(), bottom = 4.dpx()))
                    }
                    TextSettingRow(
                        "Type a calendar link here…", "", Modifier.padding(top = 14.dpx()),
                        rowRequester = calendarEntry, startEmpty = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    ) { onTypeCalendar(it) }
                    TextSettingRow(
                        "Type a Todoist token here…", "", rowRequester = tokenEntry, startEmpty = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    ) { onTypeToken(it) }
                    Column(Modifier.fillMaxWidth().padding(top = 12.dpx(), start = 8.dpx()), verticalArrangement = Arrangement.spacedBy(6.dpx())) {
                        if (checking) Text("Checking…", color = Dim80, fontFamily = Outfit, fontSize = 24.spx())
                        results.forEach { o ->
                            outcomeLine(o)?.let { line ->
                                Text(
                                    line, color = if (o is ConnectOutcome.Failed) Bad else Ok, fontFamily = Outfit,
                                    fontSize = 24.spx(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
