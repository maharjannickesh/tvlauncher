package com.nickesh.tvlauncher.domain.connect

/** Pure rules for the Connect screen. Calendar links and the token are secrets: never log them. */
object ConnectRules {
    const val MAX_CALENDARS = 4

    fun normaliseCalendarUrl(raw: String): String? {
        val t = raw.trim()
        val url = if (t.startsWith("webcal://", ignoreCase = true)) "https://" + t.substring("webcal://".length) else t
        return url.takeIf { it.startsWith("https://", ignoreCase = true) && it.length > "https://".length }
    }

    fun addCalendar(current: List<String>, raw: String): Result<List<String>> {
        if (raw.isBlank()) return Result.success(current)
        val url = normaliseCalendarUrl(raw)
            ?: return Result.failure(IllegalArgumentException("That link didn't return a calendar"))
        if (url in current) return Result.success(current)
        if (current.size >= MAX_CALENDARS) return Result.failure(IllegalArgumentException("Up to $MAX_CALENDARS calendars"))
        return Result.success(current + url)
    }

    fun removeCalendar(current: List<String>, url: String): List<String> = current.filterNot { it == url }

    fun normaliseToken(raw: String): String? = raw.trim().ifEmpty { null }

    fun maskToken(token: String): String = if (token.length < 4) token else "••••" + token.takeLast(4)
}
