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

    /** The due date in [zone]: a timed due is converted from its instant; floating/date-only values are taken as written. */
    private fun dueDay(t: TodoTask, zone: ZoneId): LocalDate? {
        t.dueDatetime?.let { dt ->
            runCatching { return Instant.parse(dt).atZone(zone).toLocalDate() }
            runCatching { return LocalDate.parse(dt.take(10)) }
        }
        return t.dueDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
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
