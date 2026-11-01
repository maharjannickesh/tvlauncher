package com.nickesh.tvlauncher.domain.agenda

import com.nickesh.tvlauncher.data.calendar.CalendarEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class UpNextRow(val time: String, val title: String, val suffix: String?)

object UpNext {
    private val TIME_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val TIME_24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.US)

    fun select(events: List<CalendarEvent>, nowMs: Long, limit: Int = 3): List<CalendarEvent> =
        events.filter { it.endMs > nowMs }
            .sortedWith(compareBy<CalendarEvent> { it.startMs }.thenBy { it.title })
            .take(limit)

    fun timeColumn(e: CalendarEvent, nowMs: Long, zone: ZoneId, use24h: Boolean): String {
        val start = Instant.ofEpochMilli(e.startMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        // A running event that began before today reads as today's.
        val days = maxOf(0L, start.toLocalDate().toEpochDay() - today.toEpochDay())
        val day: String? = when {
            days == 0L -> null
            days == 1L -> "Tomorrow"
            days <= 6L -> WEEKDAY.format(start)
            else -> DAY_MONTH.format(start)
        }
        if (e.allDay) return if (day == null) "All day" else "$day · All day"
        val time = (if (use24h) TIME_24 else TIME_12).format(start)
        return when {
            day == null -> time
            days >= 7L -> day
            else -> "$day $time"
        }
    }

    /** All-day events carry no suffix: "now" beside "All day" reads oddly. */
    fun suffix(e: CalendarEvent, nowMs: Long): String? {
        if (e.allDay) return null
        if (e.startMs <= nowMs) return if (nowMs < e.endMs) "now" else null
        val minutes = (e.startMs - nowMs) / 60_000
        return when {
            minutes < 60 -> "in $minutes min"
            minutes < 24 * 60 -> "in ${minutes / 60} h"
            else -> null
        }
    }

    fun rows(events: List<CalendarEvent>, nowMs: Long, zone: ZoneId, use24h: Boolean): List<UpNextRow> =
        select(events, nowMs).mapIndexed { i, e ->
            UpNextRow(
                time = timeColumn(e, nowMs, zone, use24h),
                title = e.title,
                suffix = if (i == 0) suffix(e, nowMs) else null,
            )
        }
}
