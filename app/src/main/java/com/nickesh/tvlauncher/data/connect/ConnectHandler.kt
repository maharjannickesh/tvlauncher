package com.nickesh.tvlauncher.data.connect

import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.domain.connect.ConnectRules

sealed interface ConnectOutcome {
    data class CalendarAdded(val name: String, val events: Int) : ConnectOutcome
    data class TodoistConnected(val openTasks: Int) : ConnectOutcome
    data object Nothing : ConnectOutcome
    data class Failed(val message: String) : ConnectOutcome
}

/** Validates what the phone submitted and saves it. Inputs are secrets: never log them. */
class ConnectHandler(
    private val current: () -> Settings,
    private val validateCalendar: suspend (String) -> Result<Pair<String, Int>>,
    private val validateToken: suspend (String) -> Result<Int>,
    private val save: suspend ((Settings) -> Settings) -> Unit,
) {
    /** Read-only view for the page: generic calendar labels (links are secret) and the masked token. */
    fun view(): Pair<List<String>, String?> {
        val s = current()
        return s.calendarUrls.indices.map { "Calendar ${it + 1}" } to s.todoistToken?.let(ConnectRules::maskToken)
    }

    suspend fun submit(calendarRaw: String?, tokenRaw: String?): List<ConnectOutcome> {
        val out = mutableListOf<ConnectOutcome>()
        if (!calendarRaw.isNullOrBlank()) out += submitCalendar(calendarRaw)
        ConnectRules.normaliseToken(tokenRaw.orEmpty())?.let { out += submitToken(it) }
        return out.ifEmpty { listOf(ConnectOutcome.Nothing) }
    }

    private suspend fun submitCalendar(raw: String): ConnectOutcome {
        val updated = ConnectRules.addCalendar(current().calendarUrls, raw).getOrElse { return failed(it) }
        val url = ConnectRules.normaliseCalendarUrl(raw) ?: return ConnectOutcome.Nothing
        val (name, events) = validateCalendar(url).getOrElse { return failed(it) }
        save { s -> s.copy(calendarUrls = ConnectRules.addCalendar(s.calendarUrls, url).getOrDefault(updated)) }
        return ConnectOutcome.CalendarAdded(name, events)
    }

    private suspend fun submitToken(token: String): ConnectOutcome {
        val open = validateToken(token).getOrElse { return failed(it) }
        save { it.copy(todoistToken = token) }
        return ConnectOutcome.TodoistConnected(open)
    }

    private fun failed(e: Throwable) = ConnectOutcome.Failed(e.message ?: "Something went wrong")
}
