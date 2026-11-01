package com.nickesh.tvlauncher.domain.agenda

/** Refresh cadence for the agenda blocks on Home. */
object AgendaFreshness {
    const val CALENDAR_MS = 15 * 60_000L
    const val TODO_MS = 5 * 60_000L
    /** Home resume refetches a block only when its last fetch is older than this. */
    const val STALE_MS = 5 * 60_000L
    const val RETRY_MS = 90_000L

    /** True when the block was never fetched or its last fetch is older than [STALE_MS]. */
    fun dueOnResume(fetchedAtMs: Long?, nowMs: Long): Boolean =
        fetchedAtMs == null || nowMs - fetchedAtMs > STALE_MS

    /** The block's period after a good fetch, a 90 s retry after a failed one. */
    fun sleepMs(periodMs: Long, ok: Boolean): Long = if (ok) periodMs else RETRY_MS
}
