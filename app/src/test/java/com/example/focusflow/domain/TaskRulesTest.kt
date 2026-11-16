package com.example.focusflow.domain

import com.example.focusflow.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskRulesTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val zone = ZoneId.systemDefault()

    private fun millis(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        date: String = "2026-10-29",
        due: String? = null,
        done: Boolean = false,
        completedAt: Long? = null
    ) = Task(id = id, title = "t$id", date = date, dueDate = due, done = done, completedAt = completedAt)

    @Test fun pendingTaskDueYesterdayIsOverdue() {
        assertTrue(TaskRules.isOverdue(task(1, due = "2026-10-28"), today))
    }

    @Test fun doneTaskIsNeverOverdue() {
        assertFalse(TaskRules.isOverdue(task(2, due = "2026-10-01", done = true), today))
    }

    @Test fun unfinishedTaskPlannedForYesterdayWithoutDueDateIsOverdue() {
        assertTrue(TaskRules.isOverdue(task(3, date = "2026-10-28"), today))
    }

    @Test fun taskDueTodayIsNotOverdue() {
        assertFalse(TaskRules.isOverdue(task(4, due = "2026-10-29"), today))
    }

    @Test fun futureTaskIsUpcomingAndNotInToday() {
        val tasks = listOf(task(5, date = "2026-10-30"))
        assertEquals(1, TaskRules.upcomingList(tasks, today).size)
        assertEquals(0, TaskRules.todayList(tasks, today).size)
    }

    @Test fun overdueTaskIsNotInTodayList() {
        val tasks = listOf(task(6, due = "2026-10-20"))
        assertEquals(0, TaskRules.todayList(tasks, today).size)
        assertEquals(1, TaskRules.overdueList(tasks, today).size)
    }

    @Test fun completedTodayCountsOnlyTodaysCompletions() {
        val tasks = listOf(
            task(7, done = true, completedAt = millis(today)),
            task(8, done = true, completedAt = millis(today.minusDays(1)))
        )
        assertEquals(1, TaskRules.completedToday(tasks, today, zone).size)
    }

    @Test fun statsAddUp() {
        val tasks = listOf(
            task(9, done = true, completedAt = millis(today)),
            task(10),
            task(11, date = "2026-10-31"),
            task(12, due = "2026-10-27")
        )
        val s = TaskRules.stats(tasks, today, zone)
        assertEquals(1, s.completedToday)
        assertEquals(2, s.pending)
        assertEquals(1, s.overdue)
        assertEquals(1, s.today)
        assertEquals(1, s.upcoming)
    }

    @Test fun deadlinesAreSortedAndIgnorePastOnes() {
        val tasks = listOf(
            task(13, due = "2026-11-05"),
            task(14, due = "2026-10-30"),
            task(15, due = "2026-10-01")
        )
        val next = TaskRules.nextDeadlines(tasks, today)
        assertEquals(listOf(14L, 13L), next.map { it.id })
    }
}
