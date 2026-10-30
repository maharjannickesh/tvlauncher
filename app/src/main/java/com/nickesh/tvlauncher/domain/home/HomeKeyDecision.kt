package com.nickesh.tvlauncher.domain.home

/** Outcome of one Home key press: short, long, or no pending press to resolve. */
enum class Press { SHORT, LONG, NONE }

/** Classifies a Home key DOWN→UP pair by its hold duration; the first DOWN of a hold wins. */
class HomeKeyDecision(private val longPressMs: Long = LONG_PRESS_MS) {
    private var pendingDownMs: Long? = null

    /** Records the start of a hold and always consumes the key; repeat DOWNs keep the first time. */
    fun onDown(eventTimeMs: Long): Boolean {
        if (pendingDownMs == null) pendingDownMs = eventTimeMs
        return true
    }

    /** Resolves the pending DOWN as short or long, or NONE when no DOWN is pending. */
    fun onUp(eventTimeMs: Long): Press {
        val downMs = pendingDownMs ?: return Press.NONE
        pendingDownMs = null
        return if (eventTimeMs - downMs < longPressMs) Press.SHORT else Press.LONG
    }

    companion object {
        const val LONG_PRESS_MS = 1000L
    }
}
