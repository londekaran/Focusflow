package com.example.focusflow.domain

import com.example.focusflow.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderRulesTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val zone = ZoneId.systemDefault()

    private fun task(id: Long, date: String = "2026-10-29", due: String? = null, done: Boolean = false, start: Int? = null, est: Int? = null) =
        Task(id = id, title = "Task $id", date = date, dueDate = due, done = done, startMin = start, estimateMin = est)

    @Test fun nextOccurrenceIsTodayWhenStillAhead() {
        val now = LocalDateTime.of(2026, 10, 29, 8, 0)
        assertEquals(LocalDateTime.of(2026, 10, 29, 8, 30), ReminderRules.nextOccurrence(8 * 60 + 30, now))
    }

    @Test fun nextOccurrenceIsTomorrowWhenPassed() {
        val now = LocalDateTime.of(2026, 10, 29, 9, 0)
        assertEquals(LocalDateTime.of(2026, 10, 30, 8, 30), ReminderRules.nextOccurrence(8 * 60 + 30, now))
        val exact = LocalDateTime.of(2026, 10, 29, 8, 30)
        assertEquals(LocalDateTime.of(2026, 10, 30, 8, 30), ReminderRules.nextOccurrence(8 * 60 + 30, exact))
    }

    @Test fun taskAlarmIsLeadMinutesBeforeTheStart() {
        val at = ReminderRules.taskAlarmAt(task(1, start = 600), 10, zone)
        val expected = LocalDateTime.of(2026, 10, 29, 9, 50).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, at)
    }

    @Test fun taskWithoutStartTimeHasNoAlarm() {
        assertNull(ReminderRules.taskAlarmAt(task(1), 10, zone))
    }

    @Test fun morningMessageIsSkippedWhenThereIsNothing() {
        assertNull(ReminderRules.morning(emptyList(), today))
        assertNull(ReminderRules.morning(listOf(task(1, done = true)), today))
    }

    @Test fun morningMessageMentionsTasksOverdueAndDeadlines() {
        val tasks = listOf(task(1, est = 60, due = "2026-10-29"), task(2, date = "2026-10-20"))
        val m = ReminderRules.morning(tasks, today)
        assertNotNull(m)
        assertTrue(m!!.text.contains("1 task planned"))
        assertTrue(m.text.contains("1 overdue"))
        assertTrue(m.text.contains("due today"))
    }

    @Test fun followUpIsSkippedWhenEverythingIsDone() {
        assertNull(ReminderRules.followUp(listOf(task(1, done = true)), today))
    }

    @Test fun followUpListsUnfinishedTasks() {
        val m = ReminderRules.followUp(listOf(task(1), task(2), task(3)), today)
        assertNotNull(m)
        assertTrue(m!!.text.contains("3 tasks unfinished"))
        assertTrue(m.text.contains("and 1 more"))
    }

    @Test fun reviewMessageIsAlwaysProduced() {
        val m = ReminderRules.review(listOf(task(1)), emptyList(), today)
        assertTrue(m.text.contains("0 done, 1 unfinished"))
    }
}
