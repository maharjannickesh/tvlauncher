package com.nickesh.tvlauncher.data.todoist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TodoCache(val tasks: List<TodoTask>, val fetchedAtMs: Long, val ok: Boolean)

class TodoistRepository(
    private val api: TodoistApi,
    private val store: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val key = stringPreferencesKey("todoist_json")

    /** Drops the cached blob (on disconnect), so nothing from the old link or token stays on disk. */
    suspend fun clear() {
        store.edit { it.remove(key) }
    }

    /** Pure read; a corrupt blob reads as null. Never touches the network. */
    suspend fun cached(): TodoCache? =
        store.data.first()[key]?.let { runCatching { json.decodeFromString<TodoCache>(it) }.getOrNull() }

    suspend fun refresh(token: String): TodoCache {
        val now = clock()
        val previous = cached()
        val out = try {
            val tasks = api.tasks(token)
            log("todoist fetched ${tasks.size}")
            TodoCache(tasks, now, true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("todoist refresh failed: ${e.javaClass.simpleName}")
            TodoCache(previous?.tasks.orEmpty(), now, false)
        }
        store.edit { it[key] = json.encodeToString(TodoCache.serializer(), out) }
        return out
    }

    suspend fun close(token: String, id: String) = api.close(token, id)

    /** Open task count, or a failure carrying the user-facing copy. */
    suspend fun validate(token: String): Result<Int> = try {
        Result.success(api.tasks(token).size)
    } catch (e: CancellationException) {
        throw e
    } catch (e: TodoistAuthException) {
        log("todoist validate: token rejected")
        Result.failure(IOException("Todoist didn't accept that token"))
    } catch (e: Exception) {
        log("todoist validate failed: ${e.javaClass.simpleName}")
        Result.failure(IOException("Can't reach Todoist"))
    }
}
