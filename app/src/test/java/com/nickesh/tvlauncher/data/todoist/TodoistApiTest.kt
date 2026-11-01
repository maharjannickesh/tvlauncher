package com.nickesh.tvlauncher.data.todoist

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TodoistApiTest {
    private val server = MockWebServer().apply { start() }
    private val api = HttpTodoistApi(OkHttpClient(), Json { ignoreUnknownKeys = true }, server.url("/api/v1").toString().trimEnd('/'))

    @After fun stop() = server.shutdown()

    @Test fun tasks_sendsBearer_andFollowsCursor() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[{"id":"1","content":"a","due":{"date":"2026-11-02","datetime":"2026-11-02T09:00:00Z"},"child_order":2,"extra":1},{"id":"2","content":"b","due":null,"child_order":1}],"next_cursor":"abc"}"""))
        server.enqueue(MockResponse().setBody("""{"results":[{"id":"3","content":"c","order":5}],"next_cursor":null}"""))
        val out = api.tasks("tok")
        assertEquals(listOf("1", "2", "3"), out.map { it.id })
        assertEquals("2026-11-02", out[0].dueDate)
        assertEquals("2026-11-02T09:00:00Z", out[0].dueDatetime)
        assertEquals(2, out[0].order)
        assertNull(out[1].dueDate)
        assertEquals(5, out[2].order)
        val r1 = server.takeRequest()
        val r2 = server.takeRequest()
        assertEquals("Bearer tok", r1.getHeader("Authorization"))
        assertEquals("Bearer tok", r2.getHeader("Authorization"))
        assertNull(r1.requestUrl!!.queryParameter("cursor"))
        assertEquals("abc", r2.requestUrl!!.queryParameter("cursor"))
        assertEquals("/api/v1/tasks", r1.requestUrl!!.encodedPath)
    }

    @Test fun tasks_401_throwsAuth() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try { api.tasks("tok"); fail() } catch (e: TodoistAuthException) { assertTrue(!e.message.orEmpty().contains("tok")) }
    }

    @Test fun tasks_500_throwsIo() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        try { api.tasks("tok"); fail() } catch (e: IOException) { assertEquals("todoist 500", e.message) }
    }

    @Test fun close_posts_toCloseEndpoint() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        api.close("tok", "42")
        api.reopen("tok", "42")
        val c = server.takeRequest()
        assertEquals("POST", c.method); assertEquals("/api/v1/tasks/42/close", c.path)
        assertEquals("Bearer tok", c.getHeader("Authorization"))
        val o = server.takeRequest()
        assertEquals("POST", o.method); assertEquals("/api/v1/tasks/42/reopen", o.path)
    }
}
