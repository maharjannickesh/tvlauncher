package com.nickesh.tvlauncher.domain.settings

object IdleOptions {
    val minutes = listOf(1, 2, 5, 10, 15, 30, 60)

    fun label(minutes: Int) = "$minutes min"

    /** Index of the exact value, else the nearest larger value, else the last index. */
    fun indexFor(minutes: Int?): Int {
        if (minutes == null) return this.minutes.indexOf(10)
        val i = this.minutes.indexOfFirst { it >= minutes }
        return if (i >= 0) i else this.minutes.lastIndex
    }
}
