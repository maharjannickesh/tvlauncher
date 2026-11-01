package com.nickesh.tvlauncher.ui.home

import android.os.SystemClock
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
import com.nickesh.tvlauncher.domain.agenda.Pending
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
import java.util.concurrent.ConcurrentHashMap
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

/** [read]: a Watch Next read has completed. */
private data class WatchSource(val rows: List<WatchNextRow>, val permitted: Boolean, val read: Boolean)

/** A block's cache plus whether a refresh attempt has finished since the block connected. */
private data class BlockSource<T>(val cache: T? = null, val attempted: Boolean = false)

private data class TodoSource(val block: BlockSource<TodoCache>, val tick: TickState)

private val LoadingUi = AgendaUi(
    upNext = emptyList(),
    upNextStatus = BlockStatus.LOADING,
    todo = emptyList(),
    todoOpen = 0,
    todoStatus = BlockStatus.LOADING,
    pendingIds = emptySet(),
    watching = emptyList(),
    watchingState = WatchingState.LOADING,
)

/**
 * Owns the Home agenda blocks: the calendar and Todoist refresh loops (cache first, never blocking on the network,
 * last good data kept on failure), the tick/undo window, and the Watch Next read. Never logs tokens or URLs.
 */
class AgendaViewModel(container: AppContainer) : ViewModel() {
    private val settingsRepo = container.settings
    private val calendar = container.calendar
    private val todoist = container.todoist
    private val watchNext = container.watchNext

    private val _calendar = MutableStateFlow(BlockSource<CalendarCache>())
    private val _todo = MutableStateFlow(BlockSource<TodoCache>())
    private val _tick = MutableStateFlow(TickState())
    private val _watch = MutableStateFlow(WatchSource(emptyList(), watchNext.hasPermission(), read = false))

    /** Monotonic time ([SystemClock.elapsedRealtime]) each ticked id's close succeeded; released by a later refresh. */
    private val closedAt = ConcurrentHashMap<String, Long>()

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
                    val held = _calendar.value.cache ?: quietly { calendar.cached() }
                    _calendar.value = BlockSource(held, attempted = false)
                    while (true) {
                        var ok = false
                        try {
                            val fresh = calendar.refresh(urls, ZoneIds.safe(zone))
                            _calendar.value = BlockSource(fresh, attempted = true)
                            ok = fresh.ok
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            _calendar.update { it.copy(attempted = true) }
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
                    val held = _todo.value.cache ?: quietly { todoist.cached() }
                    _todo.value = BlockSource(held, attempted = false)
                    while (true) {
                        var ok = false
                        try {
                            val startedAt = SystemClock.elapsedRealtime()
                            val fresh = todoist.refresh(token)
                            ok = fresh.ok
                            if (fresh.ok) {
                                // A list fetched after an id's close landed is the truth for it: a done task is gone, a
                                // recurring one is back with its next date. Still-pending ids keep their entry for later.
                                _tick.update { s ->
                                    val released = closedAt.filter { (id, at) -> at < startedAt && !TickRules.isPending(s, id) }.keys
                                    released.forEach { closedAt.remove(it) }
                                    TickRules.releaseHidden(s, released)
                                }
                            }
                            _todo.value = BlockSource(fresh, attempted = true)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            _todo.update { it.copy(attempted = true) }
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
        if (AgendaFreshness.dueOnResume(_calendar.value.cache?.fetchedAtMs, now)) calendarWake.tryEmit(Unit)
        if (AgendaFreshness.dueOnResume(_todo.value.cache?.fetchedAtMs, now)) todoWake.tryEmit(Unit)
        reloadWatchNext()
    }

    fun tick(id: String) {
        var entry: Pending? = null
        _tick.update { s ->
            val next = TickRules.tick(s, id, System.currentTimeMillis())
            entry = if (next != s) next.pending.last() else null
            next
        }
        val mine = entry ?: return
        closedAt.remove(id) // a re-tick must not be released by an earlier close's timestamp
        closes[id] = viewModelScope.async(Dispatchers.IO) {
            try {
                val token = settingsRepo.settings.first().todoistToken ?: throw IllegalStateException("no token")
                todoist.close(token, id)
                closedAt[id] = SystemClock.elapsedRealtime()
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
            // Expire exactly this tick's entry: immune to wall-clock jumps, and a later re-tick keeps its own window.
            _tick.update { s -> if (mine in s.pending) TickRules.expireId(s, id) else s }
            if (!TickRules.isPending(_tick.value, id)) closes.remove(id)
            todoWake.tryEmit(Unit)
        }
    }

    fun undo(id: String) {
        if (!TickRules.isPending(_tick.value, id)) return
        _tick.update { TickRules.undo(it, id) }
        closedAt.remove(id)
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
            // A grant (from Home or Settings) brings the row back even after an earlier "Not now".
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    settingsRepo.update { if (it.continueWatchingDismissed) it.copy(continueWatchingDismissed = false) else it }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
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
            _watch.update { it.copy(rows = rows, read = true) }
        }
    }

    override fun onCleared() {
        unregisterWatchNext?.invoke()
        unregisterWatchNext = null
    }

    /** Only ever called with loaded settings; before that [state] holds [LoadingUi]. */
    private fun build(s: Settings, calBlock: BlockSource<CalendarCache>, todo: TodoSource, watch: WatchSource, nowMs: Long): AgendaUi {
        val cal = calBlock.cache
        val zone = ZoneIds.safe(s.homeZone)
        val calConnected = s.calendarUrls.isNotEmpty()
        val upNext = if (calConnected && cal != null) UpNext.rows(cal.events, nowMs, zone, s.use24h) else emptyList()

        val todoConnected = s.todoistToken != null
        val cache = todo.block.cache
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val rows = if (todoConnected && cache != null) TodoOrder.rows(cache.tasks, today, zone) else emptyList()
        val visible = TickRules.visible(rows, todo.tick)

        val tiles = if (watch.permitted) ContinueWatching.tiles(watch.rows) else emptyList()
        return AgendaUi(
            upNext = upNext,
            upNextStatus = AgendaStatus.block(
                settingsLoaded = true, calConnected, cal != null, cal?.ok == true, cal?.events.isNullOrEmpty(), calBlock.attempted, upNext.isEmpty(),
            ),
            todo = visible,
            todoOpen = TickRules.openCount(rows.size, todo.tick),
            todoStatus = AgendaStatus.block(
                settingsLoaded = true, todoConnected, cache != null, cache?.ok == true, cache?.tasks.isNullOrEmpty(), todo.block.attempted, visible.isEmpty(),
            ),
            pendingIds = todo.tick.pending.mapTo(HashSet()) { it.id },
            watching = tiles,
            watchingState = AgendaStatus.watching(settingsLoaded = true, watch.permitted, s.continueWatchingDismissed, watch.read, tiles.isEmpty()),
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
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadingUi)

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
