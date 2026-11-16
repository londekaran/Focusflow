package com.example.focusflow.domain

import com.example.focusflow.data.Priority
import com.example.focusflow.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HomeRulesTest {
    private val today = LocalDate.of(2026, 10, 29)

    private fun task(
        id: Long, date: String = "2026-10-29", priority: Int = Priority.MEDIUM,
        start: Int? = null, done: Boolean = false
    ) = Task(id = id, title = "t$id", date = date, priority = priority, startMin = start, done = done)

    @Test fun progressCountsTodaysTasksOnly() {
        val tasks = listOf(task(1, done = true), task(2), task(3, done = true), task(4, date = "2026-10-30"))
        assertEquals(2 to 3, TaskRules.todayProgress(tasks, today))
    }

    @Test fun progressIsZeroWhenNothingIsPlanned() {
        assertEquals(0 to 0, TaskRules.todayProgress(emptyList(), today))
    }

    @Test fun nextFocusTaskIsTheHighestPriority() {
        val tasks = listOf(task(1, priority = Priority.LOW), task(2, priority = Priority.HIGH), task(3))
        assertEquals(2L, TaskRules.nextFocusTask(tasks, today)?.id)
    }

    @Test fun nextFocusTaskBreaksTiesWithTheEarliestStart() {
        val tasks = listOf(
            task(1, priority = Priority.HIGH, start = 600),
            task(2, priority = Priority.HIGH, start = 540),
            task(3, priority = Priority.HIGH)
        )
        assertEquals(2L, TaskRules.nextFocusTask(tasks, today)?.id)
    }

    @Test fun nextFocusTaskIgnoresDoneAndOverdueTasks() {
        val tasks = listOf(task(1, done = true), task(2, date = "2026-10-20"))
        assertNull(TaskRules.nextFocusTask(tasks, today))
    }

    @Test fun upcomingTasksAreInDateAndTimeOrder() {
        val tasks = listOf(
            task(1, date = "2026-10-30", start = 600),
            task(2, start = 700),
            task(3, start = 540),
            task(4, date = "2026-10-20"),
            task(5, done = true)
        )
        assertEquals(listOf(3L, 2L, 1L), TaskRules.upcomingScheduled(tasks, today, null, 10).map { it.id })
    }

    @Test fun upcomingTasksCanExcludeOneAndAreLimited() {
        val tasks = listOf(task(1, start = 540), task(2, start = 600), task(3, start = 660))
        assertEquals(listOf(2L), TaskRules.upcomingScheduled(tasks, today, 1L, 1).map { it.id })
    }
}
