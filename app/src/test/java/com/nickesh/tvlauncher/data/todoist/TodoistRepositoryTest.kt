package com.nickesh.tvlauncher.data.todoist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

private class FakeApi : TodoistApi {
    var result: () -> List<TodoTask> = { emptyList() }
    var calls = 0
    override suspend fun tasks(token: String): List<TodoTask> { calls++; return result() }
    override suspend fun close(token: String, id: String) {}
    override suspend fun reopen(token: String, id: String) {}
}

class TodoistRepositoryTest {
    private val api = FakeApi()
    private val logs = mutableListOf<String>()
    private val repo = TodoistRepository(api, MemStore(), { 1000L }, { logs += it })
    private val task = TodoTask("1", "milk", null, null, 0)

    @Test fun cached_isNullBeforeRefresh_andNeverCallsApi() = runBlocking {
        assertNull(repo.cached()); assertEquals(0, api.calls)
    }

    @Test fun refresh_success_caches() = runBlocking {
        api.result = { listOf(task) }
        val out = repo.refresh("secret-tok")
        assertTrue(out.ok); assertEquals(1000L, out.fetchedAtMs)
        assertEquals(out, repo.cached())
    }

    @Test fun refresh_failure_keepsOldTasks_notOk() = runBlocking {
        api.result = { listOf(task) }
        repo.refresh("secret-tok")
        api.result = { throw IOException("todoist 500") }
        val out = repo.refresh("secret-tok")
        assertFalse(out.ok); assertEquals(listOf(task), out.tasks)
        assertEquals(out, repo.cached())
    }

    @Test fun validate_401_hasCopy() = runBlocking {
        api.result = { throw TodoistAuthException() }
        assertEquals("Todoist didn't accept that token", repo.validate("secret-tok").exceptionOrNull()?.message)
        api.result = { throw IOException("x") }
        assertEquals("Can't reach Todoist", repo.validate("secret-tok").exceptionOrNull()?.message)
        api.result = { listOf(task, task) }
        assertEquals(2, repo.validate("secret-tok").getOrNull())
    }

    @Test fun log_neverContainsToken() = runBlocking {
        api.result = { throw IOException("boom secret-tok") }
        repo.refresh("secret-tok"); repo.validate("secret-tok")
        api.result = { throw TodoistAuthException() }
        repo.refresh("secret-tok"); repo.validate("secret-tok")
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.none { it.contains("secret-tok") })
    }
}
