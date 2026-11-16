package com.example.focusflow.domain

import com.example.focusflow.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PlanRulesTest {
    private val today = LocalDate.of(2026, 10, 29)   // a Thursday
    private val start = 9 * 60
    private val end = 17 * 60

    private fun task(id: Long, date: String, est: Int? = null, done: Boolean = false, due: String? = null) =
        Task(id = id, title = "t$id", date = date, estimateMin = est, done = done, dueDate = due)

    @Test fun weekStartIsMonday() {
        assertEquals(LocalDate.of(2026, 10, 26), PlanRules.weekStart(today))
        assertEquals(LocalDate.of(2026, 10, 26), PlanRules.weekStart(LocalDate.of(2026, 10, 26)))
        assertEquals(LocalDate.of(2026, 10, 26), PlanRules.weekStart(LocalDate.of(2026, 11, 1)))
    }

    @Test fun capacityForTodayIsWhatIsLeftOfWorkHours() {
        assertEquals(180, PlanRules.capacity(today, today, 14 * 60, start, end))
        assertEquals(480, PlanRules.capacity(today, today, 6 * 60, start, end))
        assertEquals(0, PlanRules.capacity(today, today, 18 * 60, start, end))
    }

    @Test fun capacityForFutureIsAFullWorkDayAndPastIsZero() {
        assertEquals(480, PlanRules.capacity(today.plusDays(1), today, 14 * 60, start, end))
        assertEquals(0, PlanRules.capacity(today.minusDays(1), today, 14 * 60, start, end))
    }

    @Test fun loadCountsOnlyThatDaysTasks() {
        val tasks = listOf(
            task(1, "2026-10-30", est = 60),
            task(2, "2026-10-30", est = 30, done = true),
            task(3, "2026-10-30"),
            task(4, "2026-10-31", est = 120)
        )
        val l = PlanRules.load(tasks, LocalDate.of(2026, 10, 30), today, 10 * 60, start, end)
        assertEquals(3, l.taskCount)
        assertEquals(1, l.doneCount)
        assertEquals(90, l.plannedMin)
        assertEquals(60, l.remainingMin)
        assertEquals(1, l.unestimated)
        assertEquals(0, l.overByMin)
    }

    @Test fun overCapacityIsReportedForFutureDays() {
        val tasks = listOf(task(1, "2026-10-30", est = 300), task(2, "2026-10-30", est = 240))
        val l = PlanRules.load(tasks, LocalDate.of(2026, 10, 30), today, 10 * 60, start, end)
        assertEquals(540, l.remainingMin)
        assertEquals(60, l.overByMin)
    }

    @Test fun todayIsComparedWithTheTimeLeft() {
        val tasks = listOf(task(1, "2026-10-29", est = 120))
        val l = PlanRules.load(tasks, today, today, 16 * 60, start, end)
        assertEquals(60, l.capacityMin)
        assertEquals(60, l.overByMin)
    }

    @Test fun pastDaysNeverWarn() {
        val tasks = listOf(task(1, "2026-10-28", est = 900))
        val l = PlanRules.load(tasks, LocalDate.of(2026, 10, 28), today, 10 * 60, start, end)
        assertEquals(0, l.overByMin)
    }

    @Test fun rescheduleMovesThePlannedDay() {
        val moved = TaskRules.reschedule(task(1, "2026-10-28"), LocalDate.of(2026, 10, 30))
        assertEquals("2026-10-30", moved.date)
        assertNull(moved.dueDate)
    }

    @Test fun rescheduleMovesAnEarlierDeadlineAlong() {
        val moved = TaskRules.reschedule(task(1, "2026-10-20", due = "2026-10-25"), LocalDate.of(2026, 10, 30))
        assertEquals("2026-10-30", moved.dueDate)
    }

    @Test fun rescheduleKeepsALaterDeadline() {
        val moved = TaskRules.reschedule(task(1, "2026-10-20", due = "2026-11-10"), LocalDate.of(2026, 10, 30))
        assertEquals("2026-11-10", moved.dueDate)
    }

    @Test fun rescheduledOverdueTaskIsNoLongerOverdue() {
        val t = task(1, "2026-10-20", due = "2026-10-25")
        val moved = TaskRules.reschedule(t, today.plusDays(1))
        assertEquals(false, TaskRules.isOverdue(moved, today))
    }
}
