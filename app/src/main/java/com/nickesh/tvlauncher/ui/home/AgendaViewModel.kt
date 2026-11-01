package com.nickesh.tvlauncher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickesh.tvlauncher.AppContainer
import com.nickesh.tvlauncher.data.calendar.CalendarCache
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.data.todoist.TodoCache
import com.nickesh.tvlauncher.data.watchnext.WatchNextRow
import com.nickesh.tvlauncher.domain.agenda.AgendaFreshness
import com.nickesh.tvlauncher.domain.agenda.AgendaStatus
import com.nickesh.tvlauncher.domain.agenda.BlockStatus
import com.nickesh.tvlauncher.domain.agenda.ContinueWatching
import com.nickesh.tvlauncher.domain.agenda.TickRules
import com.nickesh.tvlauncher.domain.agenda.TickState
import com.nickesh.tvlauncher.domain.agenda.TodoOrder
import com.nickesh.tvlauncher.domain.agenda.TodoRow
import com.nickesh.tvlauncher.domain.agenda.UpNext
import com.nickesh.tvlauncher.domain.agenda.UpNextRow
import com.nickesh.tvlauncher.domain.agenda.WatchTile
import com.nickesh.tvlauncher.domain.agenda.WatchingState
import com.nickesh.tvlauncher.domain.clock.ZoneIds
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class AgendaUi(
    val upNext: List<UpNextRow>,
    val upNextStatus: BlockStatus,
    val todo: List<TodoRow>,
    val todoOpen: Int,
    val todoStatus: BlockStatus,
    val pendingIds: Set<String>,
    val watching: List<WatchTile>,
    val watchingState: WatchingState,
)

private data class WatchSource(val rows: List<WatchNextRow>, val permitted: Boolean)

private data class TodoSource(val cache: TodoCache?, val tick: TickState)

/**
 * Owns the Home agenda blocks: the calendar and Todoist refresh loops (cache first, never blocking on the network,
 * last good data kept on failure), the tick/undo window, and the Watch Next read. Never logs tokens or URLs.
 */
class AgendaViewModel(container: AppContainer) : ViewModel() {
    private val settingsRepo = container.settings
    private val calendar = container.calendar
    private val todoist = container.todoist
    private val watchNext = container.watchNext

    private val _calendar = MutableStateFlow<CalendarCache?>(null)
    private val _todo = MutableStateFlow<TodoCache?>(null)
    private val _tick = MutableStateFlow(TickState())
    private val _watch = MutableStateFlow(WatchSource(emptyList(), watchNext.hasPermission()))

    private val calendarWake = wakeFlow()
    private val todoWake = wakeFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts

    /** In-flight `close` per ticked id, so an undo reopens only after (and only if) the close landed. Main thread only. */
    private val closes = mutableMapOf<String, Deferred<Boolean>>()

    /** Content-observer unregister; main thread only. */
    private var unregisterWatchNext: (() -> Unit)? = null

    /** Minute-aligned clock so "in 46 min" and the due labels stay live. */
    private val minuteTicker = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(MINUTE_MS - now % MINUTE_MS)
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.settings
                .map { it.calendarUrls to it.homeZone }
                .distinctUntilChanged()
                .collectLatest { (urls, zone) ->
                    if (urls.isEmpty()) return@collectLatest
                    if (_calendar.value == null) _calendar.value = quietly { calendar.cached() }
                    while (true) {
                        var ok = false
                        try {
                            val fresh = calendar.refresh(urls, ZoneIds.safe(zone))
                            _calendar.value = fresh
                            ok = fresh.ok
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                        }
                        withTimeoutOrNull(AgendaFreshness.sleepMs(AgendaFreshness.CALENDAR_MS, ok)) { calendarWake.first() }
                    }
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.settings
                .map { it.todoistToken }
                .distinctUntilChanged()
                .collectLatest { token ->
                    if (token == null) return@collectLatest
                    if (_todo.value == null) _todo.value = quietly { todoist.cached() }
                    while (true) {
                        var ok = false
                        try {
                            val fresh = todoist.refresh(token)
                            _todo.value = fresh
                            ok = fresh.ok
                            if (fresh.ok) {
                                // Ids the server no longer lists are done; keep hiding any it still lists (close in flight).
                                val ids = fresh.tasks.mapTo(HashSet()) { it.id }
                                _tick.update { it.copy(hiddenIds = it.hiddenIds intersect ids) }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                        }
                        withTimeoutOrNull(AgendaFreshness.sleepMs(AgendaFreshness.TODO_MS, ok)) { todoWake.first() }
                    }
                }
        }
        reloadWatchNext()
    }

    /** Home resume: each block refetches only when its last fetch is stale; Watch Next is always re-read. */
    fun refreshNow() {
        val now = System.currentTimeMillis()
        if (AgendaFreshness.dueOnResume(_calendar.value?.fetchedAtMs, now)) calendarWake.tryEmit(Unit)
        if (AgendaFreshness.dueOnResume(_todo.value?.fetchedAtMs, now)) todoWake.tryEmit(Unit)
        reloadWatchNext()
    }

    fun tick(id: String) {
        var changed = false
        _tick.update { s -> TickRules.tick(s, id, System.currentTimeMillis()).also { changed = it != s } }
        if (!changed) return
        closes[id] = viewModelScope.async(Dispatchers.IO) {
            try {
                val token = settingsRepo.settings.first().todoistToken ?: throw IllegalStateException("no token")
                todoist.close(token, id)
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Also un-hide: a close that fails after the window would otherwise stay hidden while the server lists it.
                _tick.update { s -> TickRules.fail(s, id).let { it.copy(hiddenIds = it.hiddenIds - id) } }
                _toasts.tryEmit(TOAST)
                false
            }
        }
        viewModelScope.launch {
            delay(TickRules.UNDO_MS)
            _tick.update { TickRules.expire(it, System.currentTimeMillis()).first }
            if (!TickRules.isPending(_tick.value, id)) closes.remove(id)
            todoWake.tryEmit(Unit)
        }
    }

    fun undo(id: String) {
        if (!TickRules.isPending(_tick.value, id)) return
        _tick.update { TickRules.undo(it, id) }
        val close = closes.remove(id)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // A failed close already toasted and left the task open; nothing to reopen.
                if (close != null && !close.await()) return@launch
                val token = settingsRepo.settings.first().todoistToken ?: throw IllegalStateException("no token")
                todoist.reopen(token, id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _toasts.tryEmit(TOAST)
            }
        }
    }

    fun onWatchNextPermission(granted: Boolean) {
        if (granted) {
            reloadWatchNext()
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    settingsRepo.update { it.copy(continueWatchingDismissed = true) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    /** Re-checks the permission, starts observing once it is granted, and re-reads Watch Next off the main thread. */
    fun reloadWatchNext() {
        val permitted = watchNext.hasPermission()
        if (permitted && unregisterWatchNext == null) {
            unregisterWatchNext = watchNext.observe { reloadWatchNext() }
        }
        _watch.update { it.copy(permitted = permitted) }
        viewModelScope.launch(Dispatchers.IO) {
            val rows = watchNext.read()
            _watch.update { it.copy(rows = rows) }
        }
    }

    override fun onCleared() {
        unregisterWatchNext?.invoke()
        unregisterWatchNext = null
    }

    private fun build(s: Settings, cal: CalendarCache?, todo: TodoSource, watch: WatchSource, nowMs: Long): AgendaUi {
        val zone = ZoneIds.safe(s.homeZone)
        val calConnected = s.calendarUrls.isNotEmpty()
        val upNext = if (calConnected && cal != null) UpNext.rows(cal.events, nowMs, zone, s.use24h) else emptyList()

        val todoConnected = s.todoistToken != null
        val cache = todo.cache
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val rows = if (todoConnected && cache != null) TodoOrder.rows(cache.tasks, today, zone) else emptyList()
        val visible = TickRules.visible(rows, todo.tick)

        val tiles = if (watch.permitted) ContinueWatching.tiles(watch.rows) else emptyList()
        return AgendaUi(
            upNext = upNext,
            upNextStatus = AgendaStatus.block(calConnected, cal != null, cal?.ok == true, cal?.events.isNullOrEmpty(), upNext.isEmpty()),
            todo = visible,
            todoOpen = TickRules.openCount(rows.size, todo.tick),
            todoStatus = AgendaStatus.block(todoConnected, cache != null, cache?.ok == true, cache?.tasks.isNullOrEmpty(), visible.isEmpty()),
            pendingIds = todo.tick.pending.mapTo(HashSet()) { it.id },
            watching = tiles,
            watchingState = AgendaStatus.watching(watch.permitted, s.continueWatchingDismissed, tiles.isEmpty()),
        )
    }

    val state: StateFlow<AgendaUi> =
        combine(
            settingsRepo.settings,
            _calendar,
            combine(_todo, _tick, ::TodoSource),
            _watch,
            minuteTicker,
            ::build,
        ).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            build(Settings(), null, TodoSource(null, TickState()), _watch.value, System.currentTimeMillis()),
        )

    private companion object {
        const val MINUTE_MS = 60_000L
        const val TOAST = "Couldn't update Todoist"

        fun wakeFlow() = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /** Cache reads never take a block down; a failed read just means "no cache yet". */
        suspend fun <T> quietly(block: suspend () -> T?): T? = try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }
}
