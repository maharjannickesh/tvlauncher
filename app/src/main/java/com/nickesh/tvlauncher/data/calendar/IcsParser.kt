package com.nickesh.tvlauncher.data.calendar

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

/** Pure ICS text -> flat occurrence list. No I/O, no logging of feed content. */
object IcsParser {
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val DECLINED = "DECLINED"

    fun calendarName(text: String): String? = runCatching {
        parseCalendar(text)?.getExperimentalProperty("X-WR-CALNAME")?.value?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Every occurrence whose `[startMs, endMs)` overlaps `[windowStartMs, windowEndMs)`,
     * ordered by start. Zero-length events count when their start lies inside the window.
     */
    fun parse(
        text: String,
        windowStartMs: Long,
        windowEndMs: Long,
        zone: ZoneId,
        ownerEmail: String?,
        calendarName: String,
    ): List<CalendarEvent> {
        val ical = runCatching { parseCalendar(text) }.getOrNull() ?: return emptyList()
        val events = runCatching { ical.events }.getOrNull() ?: return emptyList()

        // Overridden instances: (uid, original start ms). Registered even when the override is
        // cancelled or declined, so the master's occurrence never comes back.
        val overridden = HashSet<Pair<String, Long>>()
        for (e in events) {
            val rid = runCatching { e.recurrenceId?.value?.time }.getOrNull() ?: continue
            val uid = e.uid?.value ?: continue
            overridden += uid to rid
        }

        val out = ArrayList<CalendarEvent>()
        for (e in events) {
            runCatching {
                if (e.status?.isCancelled == true) return@runCatching
                if (declinedByOwner(e, ownerEmail)) return@runCatching
                expand(ical, e, windowStartMs, windowEndMs, zone, calendarName, overridden, out)
            }
        }
        return out.sortedBy { it.startMs }
    }

    private fun parseCalendar(text: String): ICalendar? = Biweekly.parse(text).first()

    private fun declinedByOwner(e: VEvent, ownerEmail: String?): Boolean {
        if (ownerEmail.isNullOrBlank()) return false
        return e.attendees.any { a ->
            val addr = (a.email ?: a.uri?.removePrefix("mailto:")?.removePrefix("MAILTO:"))
            addr.equals(ownerEmail, ignoreCase = true) &&
                a.participationStatus?.value.equals(DECLINED, ignoreCase = true)
        }
    }

    private fun expand(
        ical: ICalendar,
        e: VEvent,
        windowStartMs: Long,
        windowEndMs: Long,
        zone: ZoneId,
        calendarName: String,
        overridden: Set<Pair<String, Long>>,
        out: MutableList<CalendarEvent>,
    ) {
        val dtStart = e.dateStart ?: return
        val startDate = dtStart.value ?: return
        val allDay = !dtStart.value.hasTime()
        val uid = e.uid?.value ?: return
        val title = e.summary?.value.orEmpty()
        val isOverride = e.recurrenceId != null

        // Date-only values are midnight in the JVM default zone when biweekly parses them.
        val sysZone = ZoneId.systemDefault()
        fun allDayStart(ms: Long): Long =
            Instant.ofEpochMilli(ms).atZone(sysZone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

        val durationMs: Long = run {
            val end = e.dateEnd?.value
            val dur = e.duration?.value
            when {
                end != null -> end.time - startDate.time
                dur != null -> dur.toMillis()
                allDay -> DAY_MS
                else -> 0L
            }
        }
        // For all-day events the span is a whole number of calendar days, applied to the date.
        val days = if (allDay) maxOf(1L, Math.round(durationMs.toDouble() / DAY_MS)) else 0L

        fun emit(rawStart: Long) {
            val s: Long
            val en: Long
            if (allDay) {
                val d = Instant.ofEpochMilli(rawStart).atZone(sysZone).toLocalDate()
                s = d.atStartOfDay(zone).toInstant().toEpochMilli()
                en = d.plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                s = rawStart
                en = rawStart + durationMs
            }
            val overlaps = if (en > s) s < windowEndMs && en > windowStartMs
            else s >= windowStartMs && s < windowEndMs
            if (overlaps) out += CalendarEvent(uid, title, s, en, allDay, calendarName)
        }

        if (isOverride) {
            emit(startDate.time)
            return
        }

        val iterTz: TimeZone = when {
            allDay -> TimeZone.getDefault()
            else -> {
                val assigned = ical.timezoneInfo.getTimezone(dtStart)?.timeZone
                when {
                    assigned != null -> assigned // TZID
                    // No TZID and not floating: a UTC ("...Z") anchor, fixed at the UTC instant.
                    !ical.timezoneInfo.isFloating(dtStart) -> TimeZone.getTimeZone("UTC")
                    else -> TimeZone.getTimeZone(zone) // floating
                }
            }
        }
        val it = e.getDateIterator(iterTz)
        // Skip ahead to (roughly) the window start, leaving room for the event's own length.
        val lead = maxOf(durationMs, if (allDay) days * DAY_MS else 0L) + DAY_MS
        it.advanceTo(Date(maxOf(windowStartMs - lead, 0L)))
        while (it.hasNext()) {
            val occ = it.next().time
            val startMs = if (allDay) allDayStart(occ) else occ
            if (startMs >= windowEndMs) break
            if ((uid to occ) in overridden) continue
            emit(occ)
        }
    }
}
