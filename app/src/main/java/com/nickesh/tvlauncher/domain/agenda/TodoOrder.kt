package com.nickesh.tvlauncher.domain.agenda

import com.nickesh.tvlauncher.data.todoist.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TodoRow(val id: String, val text: String, val due: String?, val overdue: Boolean)

object TodoOrder {
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.US)

    /** Todoist's `due.date` is the account's own local date, so it wins; the datetime (converted to [zone]) is only a fallback. */
    private fun dueDay(t: TodoTask, zone: ZoneId): LocalDate? {
        t.dueDate?.let { d -> runCatching { return LocalDate.parse(d.take(10)) } }
        val dt = t.dueDatetime ?: return null
        runCatching { return Instant.parse(dt).atZone(zone).toLocalDate() }
        return runCatching { LocalDate.parse(dt.take(10)) }.getOrNull()
    }

    fun sort(tasks: List<TodoTask>, today: LocalDate, zone: ZoneId): List<TodoTask> {
        fun bucket(d: LocalDate?) = when {
            d == null -> 3
            d < today -> 0
            d == today -> 1
            else -> 2
        }
        return tasks.sortedWith(
            compareBy<TodoTask>({ bucket(dueDay(it, zone)) }, { dueDay(it, zone) ?: LocalDate.MAX }, { it.order }),
        )
    }

    fun dueLabel(t: TodoTask, today: LocalDate, zone: ZoneId): Pair<String?, Boolean> {
        val d = dueDay(t, zone) ?: return null to false
        val days = d.toEpochDay() - today.toEpochDay()
        return when {
            days < 0 -> "overdue" to true
            days == 0L -> "today" to false
            days == 1L -> "tomorrow" to false
            days < 7 -> weekday.format(d) to false
            else -> dayMonth.format(d) to false
        }
    }

    fun rows(tasks: List<TodoTask>, today: LocalDate, zone: ZoneId): List<TodoRow> =
        sort(tasks, today, zone).map { t ->
            val (label, overdue) = dueLabel(t, today, zone)
            TodoRow(t.id, t.content, label, overdue)
        }
}
