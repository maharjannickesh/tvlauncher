package com.nickesh.tvlauncher.domain.clock

import com.nickesh.tvlauncher.data.settings.WorldClock
import com.nickesh.tvlauncher.ui.home.WeatherKind
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class TimeText(val time: String, val ampm: String) // ampm "" in 24h

/** Current conditions for a clock card: icon kind and rounded temperature ("62°"). */
data class ClockWeather(val kind: WeatherKind, val temp: String)

data class ClockSnapshot(
    val city: String,
    val text: TimeText,
    val meta: String,
    val isDay: Boolean,
    val hourDeg: Float,
    val minuteDeg: Float,
    val secondDeg: Float,
    val weather: ClockWeather? = null,
)

object ClockMath {
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

    fun timeText(t: ZonedDateTime, use24h: Boolean): TimeText {
        val minutes = "%02d".format(t.minute)
        if (use24h) return TimeText("%02d:%s".format(t.hour, minutes), "")
        val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
        return TimeText("$h12:$minutes", if (t.hour < 12) "AM" else "PM")
    }

    fun dateLine(t: ZonedDateTime): String = DATE_FORMAT.format(t)

    fun greeting(hour: Int, name: String): String {
        val base = when {
            hour < 5 -> "Good Night"
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
        val n = name.trim()
        return if (n.isEmpty()) base else "$base, $n"
    }

    fun isDay(hour: Int): Boolean = hour in 6..17

    fun offsetMeta(home: ZonedDateTime, other: ZonedDateTime): String {
        val diff = wallMinutes(other.toLocalDateTime()) - wallMinutes(home.toLocalDateTime())
        val rounded = (diff / 15.0).roundToInt() * 15
        val dayDiff = other.toLocalDate().toEpochDay() - home.toLocalDate().toEpochDay()
        val day = when {
            dayDiff > 0 -> "Tomorrow"
            dayDiff < 0 -> "Yesterday"
            else -> "Today"
        }
        val offset = if (rounded == 0) {
            "Same time"
        } else {
            val sign = if (rounded > 0) "+" else "−"
            val hours = abs(rounded) / 60
            val mins = abs(rounded) % 60
            sign + hours + "h" + if (mins != 0) " ${mins}m" else ""
        }
        return "$day  ·  $offset"
    }

    fun dialAngles(t: ZonedDateTime): Triple<Float, Float, Float> {
        val h = t.hour.toFloat()
        val m = t.minute.toFloat()
        val s = t.second.toFloat()
        return Triple(((h % 12f) + m / 60f) * 30f, (m + s / 60f) * 6f, s * 6f)
    }

    fun snapshot(clock: WorldClock, home: ZonedDateTime, now: Instant, use24h: Boolean, weather: ClockWeather? = null): ClockSnapshot {
        val other = now.atZone(ZoneIds.safe(clock.zoneId))
        val (hourDeg, minuteDeg, secondDeg) = dialAngles(other)
        return ClockSnapshot(
            city = clock.city,
            text = timeText(other, use24h),
            meta = offsetMeta(home, other),
            isDay = isDay(other.hour),
            hourDeg = hourDeg,
            minuteDeg = minuteDeg,
            secondDeg = secondDeg,
            weather = weather,
        )
    }

    private fun wallMinutes(t: LocalDateTime): Long = t.toEpochSecond(ZoneOffset.UTC) / 60
}
