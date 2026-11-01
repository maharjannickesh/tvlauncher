package com.nickesh.tvlauncher.ui.home

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickesh.tvlauncher.AppContainer
import com.nickesh.tvlauncher.data.calendar.CalendarCache
import com.nickesh.tvlauncher.data.calendar.CalendarRepository
import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.data.settings.SettingsRepository
import com.nickesh.tvlauncher.data.todoist.TodoCache
import com.nickesh.tvlauncher.data.todoist.TodoistRepository
import com.nickesh.tvlauncher.data.watchnext.WatchNextRow
import com.nickesh.tvlauncher.data.watchnext.WatchNextSource
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
class AgendaViewModel(
    private val settingsRepo: SettingsRepository,
    private val calendar: CalendarRepository,
    private val todoist: TodoistRepository,
    private val watchNext: WatchNextSource,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Monotonic clock for the close/refresh ordering. */
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    constructor(container: AppContainer) : this(container.settings, container.calendar, container.todoist, container.watchNext)

    private val _calendar = MutableStateFlow(BlockSource<CalendarCache>())
    private val _todo = MutableStateFlow(BlockSource<TodoCache>())
    private val _tick = MutableStateFlow(TickState())
    private val _watch = MutableStateFlow(WatchSource(emptyList(), watchNext.hasPermission(), read = false))

    /** Monotonic time ([elapsed]) each ticked id's close landed; released by a refresh that started after it. */
    private val closedAt = ConcurrentHashMap<String, Long>()

    private val calendarWake = wakeFlow()
    private val todoWake = wakeFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts

    /** Content-observer unregister; main thread only. */
    private var unregisterWatchNext: (() -> Unit)? = null

    /** Minute-aligned clock so "in 46 min" and the due labels stay live. */
    private val minuteTicker = flow {
        while (true) {
            val now = wallClock()
            emit(now)
            delay(MINUTE_MS - now % MINUTE_MS)
        }
    }

    init {
        viewModelScope.launch(io) {
            settingsRepo.settings
                .map { it.calendarUrls to it.homeZone }
                .distinctUntilChanged()
                .collectLatest { (urls, zone) ->
                    if (urls.isEmpty()) {
                        // Disconnected: forget the old feeds' events, in memory and on disk.
                        _calendar.value = BlockSource()
                        quietly { calendar.clear() }
                        return@collectLatest
                    }
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
        viewModelScope.launch(io) {
            settingsRepo.settings
                .map { it.todoistToken }
                .distinctUntilChanged()
                .collectLatest { token ->
                    if (token == null) {
                        // Disconnected: forget the old account's tasks and ticks, in memory and on disk.
                        _todo.value = BlockSource()
                        _tick.value = TickState()
                        closedAt.clear()
                        quietly { todoist.clear() }
                        return@collectLatest
                    }
                    val held = _todo.value.cache ?: quietly { todoist.cached() }
                    _todo.value = BlockSource(held, attempted = false)
                    while (true) {
                        var ok = false
                        try {
                            val startedAt = elapsed()
                            val fresh = todoist.refresh(token)
                            ok = fresh.ok
                            // The fresh list goes out first, so a released id is never shown from the old list.
                            _todo.value = BlockSource(fresh, attempted = true)
                            if (fresh.ok) {
                                // A list fetched after an id's close landed is the truth for it: a done task is gone, a
                                // recurring one is back with its next date. Still-pending ids keep their entry for later.
                                var released: Map<String, Long> = emptyMap()
                                _tick.update { s ->
                                    // Pure: the CAS may re-run this lambda, so closedAt is only read here.
                                    released = closedAt.filter { (id, at) -> at < startedAt && !TickRules.isPending(s, id) }
                                    TickRules.releaseHidden(s, released.keys)
                                }
                                released.forEach { (id, at) -> closedAt.remove(id, at) }
                            }
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
        val now = wallClock()
        if (AgendaFreshness.dueOnResume(_calendar.value.cache?.fetchedAtMs, now)) calendarWake.tryEmit(Unit)
        if (AgendaFreshness.dueOnResume(_todo.value.cache?.fetchedAtMs, now)) todoWake.tryEmit(Unit)
        reloadWatchNext()
    }

    /**
     * Ticks [id] locally for the undo window (check + strike, count drops). When the window ends the row hides and
     * `close` is sent once; a failed close un-hides the row and toasts. Nothing reaches Todoist inside the window.
     */
    fun tick(id: String) {
        var entry: Pending? = null
        _tick.update { s ->
            val next = TickRules.tick(s, id, wallClock())
            entry = if (next != s) next.pending.last() else null
            next
        }
        val mine = entry ?: return
        closedAt.remove(id) // a re-tick must not be released by an earlier close's timestamp
        viewModelScope.launch(io) {
            delay(TickRules.UNDO_MS)
            // Expire exactly this tick's entry: immune to wall-clock jumps, and a later re-tick keeps its own window.
            var expired = false
            _tick.update { s ->
                expired = mine in s.pending
                if (expired) TickRules.expireId(s, id) else s
            }
            if (!expired) return@launch // undone inside the window: purely local, nothing to send
            try {
                val token = settingsRepo.settings.first().todoistToken ?: throw IllegalStateException("no token")
                todoist.close(token, id)
                closedAt[id] = elapsed()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // The task is still open on the server: show it again.
                _tick.update { s -> TickRules.releaseHidden(s, setOf(id)) }
                _toasts.tryEmit(TOAST)
            }
            todoWake.tryEmit(Unit)
        }
    }

    /** Inside the window only; local, sends nothing. */
    fun undo(id: String) {
        _tick.update { s -> if (TickRules.isPending(s, id)) TickRules.undo(s, id) else s }
    }

    fun onWatchNextPermission(granted: Boolean) {
        if (granted) {
            reloadWatchNext()
            // A grant (from Home or Settings) brings the row back even after an earlier "Not now".
            viewModelScope.launch(io) {
                try {
                    settingsRepo.update { if (it.continueWatchingDismissed) it.copy(continueWatchingDismissed = false) else it }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        } else {
            viewModelScope.launch(io) {
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
        viewModelScope.launch(io) {
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
            todoOpen = TickRules.openCount(rows, todo.tick),
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
