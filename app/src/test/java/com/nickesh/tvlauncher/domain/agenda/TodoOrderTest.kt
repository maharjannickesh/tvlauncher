package com.nickesh.tvlauncher.domain.agenda

import com.nickesh.tvlauncher.data.todoist.TodoTask
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoOrderTest {
    private val ny = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 11, 1)
    private fun t(id: String, date: String?, dt: String? = null, order: Int = 0) = TodoTask(id, id, date, dt, order)

    @Test fun sort_overdueFirst_thenToday_thenLater_thenUndated() {
        val tasks = listOf(
            t("undatedB", null, order = 2), t("later2", "2026-11-20"), t("today", "2026-11-01"),
            t("undatedA", null, order = 1), t("late1", "2026-11-05"), t("over2", "2026-10-31"), t("over1", "2026-10-01"),
        )
        assertEquals(
            listOf("over1", "over2", "today", "late1", "later2", "undatedA", "undatedB"),
            TodoOrder.sort(tasks, today, ny).map { it.id },
        )
    }

    @Test fun timedDue_earlierToday_isToday_notOverdue() {
        val task = t("x", "2026-11-01", "2026-11-01T05:00:00Z")
        assertEquals("today" to false, TodoOrder.dueLabel(task, today, ny))
    }

    @Test fun dueLabel_variants() {
        assertEquals("overdue" to true, TodoOrder.dueLabel(t("a", "2026-10-31"), today, ny))
        assertEquals("today" to false, TodoOrder.dueLabel(t("a", "2026-11-01"), today, ny))
        assertEquals("tomorrow" to false, TodoOrder.dueLabel(t("a", "2026-11-02"), today, ny))
        assertEquals("Fri" to false, TodoOrder.dueLabel(t("a", "2026-11-06"), today, ny))
        assertEquals("12 Nov" to false, TodoOrder.dueLabel(t("a", "2026-11-12"), today, ny))
        assertEquals(null to false, TodoOrder.dueLabel(t("a", null), today, ny))
    }

    @Test fun rows_mapSorted() {
        val rows = TodoOrder.rows(listOf(t("b", "2026-11-02"), t("a", "2026-10-30")), today, ny)
        assertEquals(listOf(TodoRow("a", "a", "overdue", true), TodoRow("b", "b", "tomorrow", false)), rows)
    }
}
