package com.nickesh.tvlauncher.ui.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nickesh.tvlauncher.data.calendar.CalendarRepository
import com.nickesh.tvlauncher.data.net.TextFetcher
import com.nickesh.tvlauncher.data.settings.SettingsRepository
import com.nickesh.tvlauncher.data.todoist.TodoTask
import com.nickesh.tvlauncher.data.todoist.TodoistApi
import com.nickesh.tvlauncher.data.todoist.TodoistRepository
import com.nickesh.tvlauncher.data.watchnext.WatchNextRow
import com.nickesh.tvlauncher.data.watchnext.WatchNextSource
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class MemStore : DataStore<Preferences> {
    val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

/** A tiny Todoist: `close` removes a task, or for a recurring one moves it to the next day. */
private class FakeTodoist(var tasks: List<TodoTask>) : TodoistApi {
    val closed = mutableListOf<String>()
    var failClose = false
    val recurring = mutableSetOf<String>()
    override suspend fun tasks(token: String) = tasks
    override suspend fun close(token: String, id: String) {
        closed += id
        if (failClose) throw IOException("todoist 500")
        tasks = if (id in recurring) tasks.map { if (it.id == id) it.copy(dueDate = TOMORROW) else it } else tasks.filterNot { it.id == id }
    }
    override suspend fun reopen(token: String, id: String) = error("reopen must never be sent")
}

private class NoWatchNext : WatchNextSource {
    override fun hasPermission() = false
    override fun read() = emptyList<WatchNextRow>()
    override fun observe(onChange: () -> Unit): () -> Unit = {}
}

private class NoFetch : TextFetcher {
    override suspend fun get(url: String, bearer: String?): String = throw IOException("unused")
}

private val TODAY = LocalDate.of(2026, 11, 1)
private val TOMORROW = TODAY.plusDays(1).toString()
private val NOON_MS = TODAY.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

/** Holds the ViewModel in a real [ViewModelStore] so the test can clear it like an activity finishing would. */
private class Owner {
    private val store = ViewModelStore()
    fun put(vm: AgendaViewModel) {
        ViewModelProvider.create(store, viewModelFactory { initializer { vm } })[AgendaViewModel::class]
    }
    fun clear() = store.clear()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeTodoist(listOf(TodoTask("1", "water plants", TODAY.toString(), null, 0), TodoTask("2", "milk", null, null, 1)))
    private val store = MemStore()
    private val toasts = mutableListOf<String>()
    private val states = mutableListOf<AgendaUi>()

    /** A real monotonic clock never reads the same value for "close landed" and "the next refresh started". */
    private var monotonic = 0L

    /**
     * Main is unconfined (like `Main.immediate` on the device): the state pipeline reacts to each store write as it
     * happens, so an intermediate (wrongly ordered) state is published and caught. The "IO" work stays on [dispatcher].
     */
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(dispatcher.scheduler))
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.start(): AgendaViewModel {
        val settings = SettingsRepository(store)
        kotlinx.coroutines.runBlocking { settings.update { it.copy(todoistToken = "secret-tok", homeZone = "UTC") } }
        val vm = AgendaViewModel(
            settings,
            CalendarRepository(NoFetch(), store),
            TodoistRepository(api, store, clock = { NOON_MS + testScheduler.currentTime }),
            NoWatchNext(),
            io = dispatcher,
            elapsed = { testScheduler.currentTime * 1_000 + ++monotonic },
            wallClock = { NOON_MS + testScheduler.currentTime },
        )
        owner.put(vm)
        // Unconfined collectors see every state the ViewModel publishes.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { states += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.toasts.collect { toasts += it } }
        runCurrent()
        assertEquals(listOf("1", "2"), vm.state.value.todo.map { it.id })
        return vm
    }

    /** The ViewModel's loops never go idle, so each test clears it (cancelling them) before runTest drains the scheduler. */
    private val owner = Owner()

    private fun agendaTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            owner.clear()
        }
    }

    private fun AgendaViewModel.ids() = state.value.todo.map { it.id }

    @Test fun tick_windowEnds_closesOnce_rowGone() = agendaTest {
        val vm = start()
        vm.tick("1")
        runCurrent()
        assertEquals(setOf("1"), vm.state.value.pendingIds)
        assertEquals(listOf("1", "2"), vm.ids()) // still shown, struck through
        assertEquals(1, vm.state.value.todoOpen)
        advanceTimeBy(4_999); runCurrent()
        assertTrue("nothing sent inside the window", api.closed.isEmpty())
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf("1"), api.closed)
        assertEquals(listOf("2"), vm.ids())
        assertEquals(1, vm.state.value.todoOpen)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(listOf("1"), api.closed)
        assertTrue(toasts.isEmpty())
    }

    @Test fun undo_insideWindow_sendsNothing_rowBack() = agendaTest {
        val vm = start()
        vm.tick("1")
        advanceTimeBy(3_000); runCurrent()
        vm.undo("1")
        runCurrent()
        assertEquals(emptySet<String>(), vm.state.value.pendingIds)
        assertEquals(2, vm.state.value.todoOpen)
        advanceTimeBy(10_000); runCurrent()
        assertTrue(api.closed.isEmpty())
        assertEquals(listOf("1", "2"), vm.ids())
        assertTrue(toasts.isEmpty())
    }

    @Test fun closeFails_rowBack_andToast() = agendaTest {
        api.failClose = true
        val vm = start()
        vm.tick("1")
        advanceTimeBy(5_001); runCurrent()
        assertEquals(listOf("1"), api.closed)
        assertEquals(listOf("1", "2"), vm.ids())
        assertEquals(2, vm.state.value.todoOpen)
        assertEquals(listOf("Couldn't update Todoist"), toasts)
    }

    @Test fun recurring_refreshAfterClose_showsItAgain_neverFromTheOldList() = agendaTest {
        api.recurring += "1"
        val vm = start()
        vm.tick("1")
        advanceTimeBy(5_001); runCurrent()
        assertEquals(listOf("1"), api.closed)
        // The close's wake refresh started after the close landed: the id is back, with its next date.
        val row = vm.state.value.todo.first { it.id == "1" }
        assertEquals("tomorrow", row.due)
        assertEquals(2, vm.state.value.todoOpen)
        // Once hidden, no state ever showed the row again from the pre-close list ("today").
        val tickedAt = states.indexOfFirst { s -> "1" in s.pendingIds }
        assertTrue(tickedAt >= 0)
        val hiddenAt = tickedAt + states.drop(tickedAt).indexOfFirst { s -> s.pendingIds.isEmpty() && s.todo.none { it.id == "1" } }
        assertTrue(hiddenAt > tickedAt)
        val stale = states.drop(hiddenAt).filter { s -> s.todo.any { it.id == "1" && it.due == "today" } }
        assertEquals(emptyList<AgendaUi>(), stale)
    }

    @Test fun disconnect_clearsTodoistCache() = agendaTest {
        val vm = start()
        val key = stringPreferencesKey("todoist_json")
        assertTrue(store.state.value[key] != null)
        SettingsRepository(store).update { it.copy(todoistToken = null) }
        runCurrent()
        assertNull(store.state.value[key])
        assertTrue(vm.ids().isEmpty())
        assertNull(SettingsRepository(store).settings.first().todoistToken)
    }
}
