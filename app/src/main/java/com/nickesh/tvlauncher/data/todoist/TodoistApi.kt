package com.nickesh.tvlauncher.data.todoist

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class TodoTask(val id: String, val content: String, val dueDate: String?, val dueDatetime: String?, val order: Int)

/** Todoist rejected the token (HTTP 401). The message never carries the token. */
class TodoistAuthException : IOException("todoist 401")

interface TodoistApi {
    suspend fun tasks(token: String): List<TodoTask>
    suspend fun close(token: String, id: String)
    suspend fun reopen(token: String, id: String)
}

class HttpTodoistApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = "https://api.todoist.com/api/v1",
) : TodoistApi {
    private companion object { const val MAX_PAGES = 100 }

    @Serializable private data class Due(val date: String? = null, val datetime: String? = null)

    @Serializable
    private data class Raw(
        val id: String,
        val content: String = "",
        val due: Due? = null,
        val order: Int? = null,
        val child_order: Int? = null,
    )

    @Serializable private data class Page(val results: List<Raw> = emptyList(), val next_cursor: String? = null)

    override suspend fun tasks(token: String): List<TodoTask> {
        val out = mutableListOf<TodoTask>()
        var cursor: String? = null
        val seen = mutableSetOf<String>()
        var pages = 0
        do {
            val url = "$baseUrl/tasks".toHttpUrl().newBuilder().apply { cursor?.let { addQueryParameter("cursor", it) } }.build()
            val body = call(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build())
            val page = json.decodeFromString<Page>(body)
            page.results.mapTo(out) { TodoTask(it.id, it.content, it.due?.date, it.due?.datetime, it.child_order ?: it.order ?: 0) }
            cursor = page.next_cursor
            // Null or empty ends the list; a repeated cursor or runaway page count is a server fault, not more data.
            if (!cursor.isNullOrEmpty() && (!seen.add(cursor) || ++pages >= MAX_PAGES)) {
                throw IOException("todoist pagination loop")
            }
        } while (!cursor.isNullOrEmpty())
        return out
    }

    override suspend fun close(token: String, id: String) { post(token, id, "close") }

    override suspend fun reopen(token: String, id: String) { post(token, id, "reopen") }

    private suspend fun post(token: String, id: String, action: String) {
        val url = "$baseUrl/tasks".toHttpUrl().newBuilder().addPathSegment(id).addPathSegment(action).build()
        call(Request.Builder().url(url).header("Authorization", "Bearer $token").post(ByteArray(0).toRequestBody()).build())
    }

    private suspend fun call(req: Request): String = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { res ->
            if (res.code == 401) throw TodoistAuthException()
            if (!res.isSuccessful) throw IOException("todoist ${res.code}")
            res.body?.string().orEmpty()
        }
    }
}
