package com.nickesh.tvlauncher.data.connect

import com.nickesh.tvlauncher.data.settings.Settings
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConnectServerTest {
    private var settings = Settings()
    private val calendarSeen = mutableListOf<String>()
    private val tokenSeen = mutableListOf<String>()
    private var saves = 0
    private var calendarResult: Result<Pair<String, Int>> = Result.success("Home" to 3)
    private var tokenResult: Result<Int> = Result.success(5)
    private val handler = ConnectHandler(
        current = { settings },
        validateCalendar = { calendarSeen += it; calendarResult },
        validateToken = { tokenSeen += it; tokenResult },
        save = { t -> saves++; settings = t(settings) },
    )
    private val server = ConnectServer(handler, "abc234", 0)
    private val port = server.start()
    private val client = OkHttpClient()
    private fun base() = "http://127.0.0.1:$port"

    @After fun tearDown() = server.stop()

    private fun get(path: String) = client.newCall(Request.Builder().url(base() + path).build()).execute()
    private fun post(path: String, calendar: String?, token: String?) = client.newCall(
        Request.Builder().url(base() + path).post(FormBody.Builder().apply {
            calendar?.let { add("calendar", it) }; token?.let { add("token", it) }
        }.build()).build(),
    ).execute()

    @Test fun get_withCode_servesPage() {
        val r = get("/c/abc234")
        assertEquals(200, r.code)
        assertTrue(r.body!!.string().contains("TV Launcher · Connect"))
    }

    @Test fun get_wrongCode_is404() {
        val r = get("/c/zzzzzz")
        assertEquals(404, r.code)
        assertEquals("", r.body!!.string())
        assertEquals(404, get("/").code)
    }

    @Test fun post_wrongCode_is404_andDoesNothing() {
        assertEquals(404, post("/c/zzzzzz", "https://x/basic.ics", "t").code)
        assertEquals(0, saves)
        assertTrue(calendarSeen.isEmpty())
    }

    @Test fun post_calendar_isTrimmed_validated_saved() {
        val r = post("/c/abc234", " https://x/basic.ics\n", null)
        val body = r.body!!.string()
        assertEquals(listOf("https://x/basic.ics"), calendarSeen)
        assertEquals(listOf("https://x/basic.ics"), settings.calendarUrls)
        assertTrue(body.contains("Calendar connected · Home · 3 events"))
        assertFalse(body.contains("https://x/basic.ics"))
    }

    @Test fun post_badCalendar_showsFailure_doesNotSave() {
        calendarResult = Result.failure(IllegalStateException("That link didn't return a calendar"))
        val body = post("/c/abc234", "https://x/basic.ics", null).body!!.string()
        assertTrue(body.contains("That link didn&#39;t return a calendar"))
        assertEquals(0, saves)
        assertTrue(settings.calendarUrls.isEmpty())
    }

    @Test fun post_token_replaces_andShowsOpenCount() {
        settings = settings.copy(todoistToken = "old-token-0000")
        val body = post("/c/abc234", null, "\n new-token-1234 ").body!!.string()
        assertEquals(listOf("new-token-1234"), tokenSeen)
        assertEquals("new-token-1234", settings.todoistToken)
        assertTrue(body.contains("Todoist connected · 5 open tasks"))
        assertFalse(body.contains("new-token-1234"))
        assertTrue(body.contains("••••1234"))
    }

    @Test fun post_emptyFields_changesNothing() {
        post("/c/abc234", "  ", "")
        assertEquals(0, saves)
        assertTrue(calendarSeen.isEmpty() && tokenSeen.isEmpty())
    }

    @Test fun post_bothFields_giveTwoResultLines() {
        val body = post("/c/abc234", "https://x/basic.ics", "tok-12345").body!!.string()
        assertTrue(body.contains("Calendar connected") && body.contains("Todoist connected"))
    }

    @Test fun post_tooLarge_is413() {
        assertEquals(413, post("/c/abc234", "https://x/" + "a".repeat(9000), null).code)
        assertEquals(0, saves)
    }

    @Test fun pageEscapesDynamicStrings() {
        val html = com.nickesh.tvlauncher.data.connect.ConnectPage.html(
            listOf(ConnectOutcome.Failed("<script>x</script>")), listOf("<b>"), null,
        )
        assertFalse(html.contains("<script>x"))
        assertFalse(html.contains("<b>"))
    }

    @Test fun stop_closesPort() {
        server.stop()
        try {
            get("/c/abc234"); fail("expected connection failure")
        } catch (_: java.io.IOException) {
        }
    }
}
