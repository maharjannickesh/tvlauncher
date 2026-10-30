package com.nickesh.tvlauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.nickesh.tvlauncher.data.weather.GeoMatch
import com.nickesh.tvlauncher.ui.components.GlassCard
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.dpx
import com.nickesh.tvlauncher.ui.theme.spx
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Open-Meteo's geocoder needs at least this many characters; shorter queries never hit the network. */
private const val MIN_QUERY = 2
private const val MAX_RESULTS = 8

/**
 * City/zone picker for one place (Home or a clock), shown in the Settings panel slot.
 * The typed text is only a search query: the caller's draft changes only through [onApplyMatch] (a picked result,
 * after which the panel closes via [onClose]) or [onApplyZone] (a picked zone; the picker stays open).
 * Back closes the panel without touching the draft.
 */
@Composable
fun CityPickerPanel(
    title: String,
    city: String,
    zoneId: String,
    search: suspend (String) -> List<GeoMatch>,
    onApplyMatch: (GeoMatch) -> Unit,
    onApplyZone: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf(city) }
    var results by remember { mutableStateOf(emptyList<GeoMatch>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var zones by remember { mutableStateOf(false) }
    // Which row takes focus when the picker (re)appears: the City field on open, the Time zone row after the zone list.
    var focusZoneRow by remember { mutableStateOf(false) }
    // Set when a search returns matches, so the first match takes focus once (not again after the zone list).
    var focusFirstResult by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun runSearch(q: String) {
        job?.cancel()
        results = emptyList()
        if (q.length < MIN_QUERY) { status = "No matches"; return }
        status = "Searching…"
        job = scope.launch {
            try {
                val found = search(q).take(MAX_RESULTS)
                results = found
                status = if (found.isEmpty()) "No matches" else null
                if (found.isNotEmpty()) focusFirstResult = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Transport failure (IOException) — or a malformed reply, which is just as unusable; retrying is the remedy.
                status = "Offline — try again"
            }
        }
    }

    if (zones) {
        ZonePanel(
            current = zoneId,
            onPick = { z -> onApplyZone(z); zones = false; focusZoneRow = true },
            onClose = { zones = false; focusZoneRow = true },
        )
        return
    }

    BackHandler { onClose() }
    val cityRow = remember { FocusRequester() }
    val zoneRow = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    // Per-target focus flags end the per-frame retries once the target really has focus.
    var cityFocused by remember { mutableStateOf(false) }
    var zoneFocused by remember { mutableStateOf(false) }
    var firstFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (focusZoneRow) focusWithRetry(zoneRow) { zoneFocused } else focusWithRetry(cityRow) { cityFocused }
    }
    LaunchedEffect(focusFirstResult) {
        // Clear the key only after the retries: clearing it first would restart (cancel) this effect.
        if (focusFirstResult) { focusWithRetry(firstResult) { firstFocused }; focusFirstResult = false }
    }

    // While the field edits, Left/Right move its cursor; otherwise they would escape to the Settings list behind.
    var editing by remember { mutableStateOf(false) }
    GlassCard(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            !editing && e.type == KeyEventType.KeyDown && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)
        },
    ) {
        Column(Modifier.fillMaxSize().padding(top = 28.dpx(), start = 20.dpx(), end = 20.dpx())) {
            Text(
                title, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 30.spx(),
                modifier = Modifier.padding(start = 12.dpx(), bottom = 16.dpx()),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dpx(), horizontal = 8.dpx()),
                verticalArrangement = Arrangement.spacedBy(10.dpx()),
            ) {
                QueryRow(
                    label = "City",
                    value = query,
                    rowRequester = cityRow,
                    modifier = Modifier.onFocusChanged { cityFocused = it.hasFocus }.onPreviewKeyEvent { e ->
                        !editing && e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp // top edge of the panel
                    },
                    onEditing = { editing = it },
                    onDone = { q -> if (q.isNotEmpty()) query = q; runSearch(q) },
                )
                status?.let { line ->
                    Text(
                        line, color = Color(0x99FFFFFF), fontFamily = Outfit, fontWeight = FontWeight.W300, fontSize = 22.spx(),
                        modifier = Modifier.padding(horizontal = 20.dpx(), vertical = 8.dpx()),
                    )
                }
                results.forEachIndexed { i, m ->
                    val first = Modifier.focusRequester(firstResult).onFocusChanged { firstFocused = it.isFocused }
                    MatchRow(m, if (i == 0) first else Modifier) {
                        onApplyMatch(m)
                        onClose()
                    }
                }
                SettingRow(
                    "Time zone",
                    Modifier.focusRequester(zoneRow).onFocusChanged { zoneFocused = it.isFocused }.onPreviewKeyEvent { e ->
                        e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown // bottom edge of the panel
                    },
                    onClick = { zones = true },
                ) { ValueText("$zoneId  ›") }
            }
        }
    }
}

/** Focus targets in a lazy list attach a frame or more late: retry per frame for a bounded time. */
private suspend fun focusWithRetry(target: FocusRequester, landed: () -> Boolean) {
    repeat(20) {
        withFrameNanos { }
        runCatching { target.requestFocus() }
        withFrameNanos { }
        if (landed()) return
    }
}

/** One geocoder match: "Name, Region, Country" with its time zone on a second line. */
@Composable
private fun MatchRow(m: GeoMatch, modifier: Modifier, onClick: () -> Unit) {
    RowSurface(modifier, onClick = onClick, height = 88.dpx()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dpx(), vertical = 10.dpx()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                m.label, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.W400, fontSize = 26.spx(),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(m.timezone, color = Color(0x99FFFFFF), fontFamily = Outfit, fontWeight = FontWeight.W300, fontSize = 20.spx(), maxLines = 1)
        }
    }
}

/**
 * The `City` search field, after the [TextSettingRow] pattern: OK edits, Done hands the trimmed text to [onDone],
 * Back or moving away abandons the edit. Editing starts with the whole text selected so typing replaces it.
 */
@Composable
private fun QueryRow(
    label: String,
    value: String,
    rowRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onEditing: (Boolean) -> Unit,
    onDone: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue(value)) }
    var hadFocus by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    fun setEditing(on: Boolean) { editing = on; onEditing(on) }
    BackHandler(enabled = editing) { setEditing(false) }
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { requester.requestFocus() }
            // The IME resets the selection when it attaches; select all again after it settles so typing replaces the text.
            repeat(3) { withFrameNanos { } }
            delay(150)
            text = text.copy(selection = TextRange(0, text.text.length))
        } else if (hadFocus) { hadFocus = false; runCatching { rowRequester.requestFocus() } } // Done/Back, not focus moved away
    }
    SettingRow(
        label,
        modifier.focusRequester(rowRequester),
        onClick = { text = TextFieldValue(value, TextRange(0, value.length)); hadFocus = false; setEditing(true) },
    ) {
        if (editing) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontFamily = Outfit, fontSize = 24.spx(), textAlign = TextAlign.End),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val t = text.text.trim()
                    setEditing(false)
                    onDone(t)
                }),
                modifier = Modifier.size(width = 420.dpx(), height = 40.dpx()).focusRequester(requester)
                    .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus && editing) { setEditing(false); hadFocus = false } },
            )
        } else {
            ValueText(value)
        }
    }
}
