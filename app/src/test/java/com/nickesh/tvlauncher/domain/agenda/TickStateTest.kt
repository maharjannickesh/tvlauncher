package com.nickesh.tvlauncher.domain.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TickStateTest {
    private fun row(id: String) = TodoRow(id, "t$id", null, false)
    private val empty = TickState()

    @Test fun tick_addsPending_withFiveSecondDeadline() {
        val s = TickRules.tick(empty, "a", 1_000L)
        assertEquals(listOf(Pending("a", 6_000L)), s.pending)
        assertTrue(TickRules.isPending(s, "a"))
    }

    @Test fun undo_removesPending() {
        val s = TickRules.undo(TickRules.tick(empty, "a", 0L), "a")
        assertEquals(empty, s)
        assertEquals(empty, TickRules.undo(empty, "zzz"))
    }

    @Test fun fail_removesPending() {
        val s = TickRules.fail(TickRules.tick(empty, "a", 0L), "a")
        assertEquals(empty, s)
        assertEquals(empty, TickRules.fail(empty, "zzz"))
    }

    @Test fun expire_movesToHidden_andReportsIds() {
        var s = TickRules.tick(empty, "a", 0L)
        s = TickRules.tick(s, "b", 1_000L)
        s = TickRules.tick(s, "c", 3_000L)
        val (next, gone) = TickRules.expire(s, 6_000L)
        assertEquals(listOf("a", "b"), gone)
        assertEquals(setOf("a", "b"), next.hiddenIds)
        assertEquals(listOf(Pending("c", 8_000L)), next.pending)
        assertFalse(TickRules.isPending(next, "a"))
    }

    @Test fun visible_keepsPending_dropsHidden() {
        val s = TickState(listOf(Pending("b", 9L)), setOf("c"))
        val out = TickRules.visible(listOf(row("a"), row("b"), row("c"), row("d")), s)
        assertEquals(listOf("a", "b", "d"), out.map { it.id })
    }

    @Test fun openCount_countsRowsNeitherPendingNorHidden() {
        val s = TickState(listOf(Pending("b", 9L)), setOf("c"))
        assertEquals(2, TickRules.openCount(listOf(row("a"), row("b"), row("c"), row("d")), s))
        assertEquals(1, TickRules.openCount(listOf(row("a")), s))
        assertEquals(0, TickRules.openCount(listOf(row("b"), row("c")), s))
    }

    @Test fun openCount_ignoresTickedIdsMissingFromRows() {
        // A hidden id the server already dropped must not shrink the count of the rows still listed.
        val s = TickState(listOf(Pending("gone1", 9L)), setOf("gone2"))
        assertEquals(2, TickRules.openCount(listOf(row("a"), row("d")), s))
    }

    @Test fun tick_twice_isIdempotent() {
        val once = TickRules.tick(empty, "a", 0L)
        assertEquals(once, TickRules.tick(once, "a", 2_000L))
        val hidden = TickState(hiddenIds = setOf("h"))
        assertEquals(hidden, TickRules.tick(hidden, "h", 0L))
    }

    @Test fun expireId_hidesOnlyThatId_regardlessOfClock() {
        val s = TickRules.tick(TickRules.tick(empty, "a", 10_000L), "b", 10_000L)
        val out = TickRules.expireId(s, "a")
        assertEquals(listOf(Pending("b", 15_000L)), out.pending)
        assertEquals(setOf("a"), out.hiddenIds)
    }

    @Test fun expireId_notPending_isNoOp() {
        val undone = TickRules.undo(TickRules.tick(empty, "a", 0L), "a")
        assertEquals(undone, TickRules.expireId(undone, "a"))
    }

    @Test fun releaseHidden_removesIds_keepsPendingAndOthers() {
        val s = TickState(pending = listOf(Pending("p", 5L)), hiddenIds = setOf("a", "b"))
        assertEquals(TickState(pending = listOf(Pending("p", 5L)), hiddenIds = setOf("b")), TickRules.releaseHidden(s, setOf("a", "zzz")))
    }
}
