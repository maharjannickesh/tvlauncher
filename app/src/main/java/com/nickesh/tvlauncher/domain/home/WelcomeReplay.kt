package com.nickesh.tvlauncher.domain.home

/** Decides whether a standby gap is long enough for the Welcome splash to replay. */
object WelcomeReplay {
    const val THRESHOLD_MS = 600_000L

    fun shouldReplay(screenOffAt: Long?, screenOnAt: Long?, thresholdMs: Long = THRESHOLD_MS): Boolean {
        if (screenOffAt == null || screenOnAt == null) return false
        val gap = screenOnAt - screenOffAt
        return gap >= thresholdMs
    }
}
