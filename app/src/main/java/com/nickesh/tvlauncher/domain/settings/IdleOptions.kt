package com.nickesh.tvlauncher.domain.settings

object IdleOptions {
    val minutes = listOf(1, 2, 5, 10, 15, 30, 60)

    /** Anything over a day is the system's "never" sentinel (e.g. `Int.MAX_VALUE` ms), not a real timer. */
    fun label(minutes: Int) = if (minutes > 24 * 60) "Never" else "$minutes min"

    /** Index of the exact value, else the nearest larger value, else the last index. */
    fun indexFor(minutes: Int?): Int {
        if (minutes == null) return this.minutes.indexOf(10)
        val i = this.minutes.indexOfFirst { it >= minutes }
        return if (i >= 0) i else this.minutes.lastIndex
    }
}
