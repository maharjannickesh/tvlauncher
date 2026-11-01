package com.nickesh.tvlauncher.domain.agenda

data class Pending(val id: String, val expiresAtMs: Long)

data class TickState(
    val pending: List<Pending> = emptyList(),
    val hiddenIds: Set<String> = emptySet(),
)

object TickRules {
    const val UNDO_MS = 5_000L

    fun tick(s: TickState, id: String, nowMs: Long): TickState =
        if (isPending(s, id) || id in s.hiddenIds) s
        else s.copy(pending = s.pending + Pending(id, nowMs + UNDO_MS))

    fun undo(s: TickState, id: String): TickState =
        s.copy(pending = s.pending.filterNot { it.id == id })

    fun fail(s: TickState, id: String): TickState = undo(s, id)

    fun expire(s: TickState, nowMs: Long): Pair<TickState, List<String>> {
        val (done, still) = s.pending.partition { it.expiresAtMs <= nowMs }
        val ids = done.map { it.id }
        return s.copy(pending = still, hiddenIds = s.hiddenIds + ids) to ids
    }

    fun isPending(s: TickState, id: String): Boolean = s.pending.any { it.id == id }

    fun visible(tasks: List<TodoRow>, s: TickState): List<TodoRow> =
        tasks.filterNot { it.id in s.hiddenIds }

    fun openCount(total: Int, s: TickState): Int =
        (total - s.pending.size - s.hiddenIds.size).coerceAtLeast(0)
}
