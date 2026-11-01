package com.nickesh.tvlauncher.domain.agenda

import com.nickesh.tvlauncher.data.watchnext.WatchNextRow

data class WatchTile(
    val id: Long,
    val packageName: String,
    val title: String,
    val meta: String,
    val progress: Float?,
    val posterUri: String?,
    val intentUri: String?,
)

object ContinueWatching {
    private const val TYPE_CONTINUE = 0
    private val SHOWN_TYPES = setOf(0, 1, 2)

    fun select(rows: List<WatchNextRow>, limit: Int = 3): List<WatchNextRow> =
        rows.filter { it.browsable && it.type in SHOWN_TYPES }
            .sortedByDescending { it.lastEngagedMs }
            .take(limit)

    fun meta(r: WatchNextRow): String {
        val prefix = listOfNotNull(
            r.season?.let { "S$it" },
            r.episode?.let { "E$it" },
        ).joinToString(" ").ifEmpty { null }
        val pos = r.positionMs
        val dur = r.durationMs
        val trailer = when {
            r.type == TYPE_CONTINUE && pos != null && dur != null && dur > 0 -> timeLeft(dur - pos)
            r.type == TYPE_CONTINUE && (pos == null || dur == null) || r.type != TYPE_CONTINUE -> "Up next"
            else -> null
        }
        return listOfNotNull(prefix, trailer).joinToString(" · ")
    }

    private fun timeLeft(remainingMs: Long): String {
        val secs = maxOf(remainingMs, 0L) / 1000
        if (secs < 60) return "< 1 min left"
        val mins = (secs + 30) / 60 // nearest minute
        if (mins < 60) return "$mins min left"
        val h = mins / 60
        val m = mins % 60
        return if (m == 0L) "${h}h left" else "${h}h ${m}m left"
    }

    fun progress(r: WatchNextRow): Float? {
        val pos = r.positionMs ?: return null
        val dur = r.durationMs ?: return null
        if (dur <= 0) return null
        return (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
    }

    fun tiles(rows: List<WatchNextRow>): List<WatchTile> =
        select(rows).map {
            WatchTile(it.id, it.packageName, it.title, meta(it), progress(it), it.posterUri, it.intentUri)
        }
}
