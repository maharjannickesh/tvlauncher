package com.nickesh.tvlauncher.data.calendar

import kotlinx.serialization.Serializable

/** One concrete occurrence of a calendar event (recurrences already expanded). */
@Serializable
data class CalendarEvent(
    val uid: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val calendar: String,
)
