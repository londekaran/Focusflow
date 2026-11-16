package com.example.focusflow.domain

import com.example.focusflow.data.Priority
import com.example.focusflow.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ScheduleTest {
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 10, 29)

    private fun ms(d: LocalDate, h: Int, m: Int) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun task(id: Long, start: Int? = null, est: Int? = null, priority: Int = Priority.MEDIUM, done: Boolean = false) =
        Task(id = id, title = "t$id", startMin = start, estimateMin = est, priority = priority, done = done)

    @Test fun freeSlotsAroundABusyBlock() {
        val free = Schedule.freeSlots(540, 1020, listOf(MinRange(600, 660)))
        assertEquals(listOf(MinRange(540, 600), MinRange(660, 1020)), free)
    }

    @Test fun freeSlotsStartFromNow() {
        val free = Schedule.freeSlots(540, 1020, listOf(MinRange(600, 660)), from = 615)
        assertEquals(listOf(MinRange(660, 1020)), free)
    }

    @Test fun shortGapsAreNotFreeSlots() {
        assertTrue(Schedule.freeSlots(540, 1020, listOf(MinRange(540, 560), MinRange(570, 1020))).isEmpty())
    }

    @Test fun freeSlotStartsAreRoundedUpToFiveMinutes() {
        assertEquals(listOf(MinRange(545, 1020)), Schedule.freeSlots(540, 1020, emptyList(), from = 541))
    }

    @Test fun overlappingRangesAreMerged() {
        assertEquals(listOf(MinRange(0, 90)), Schedule.merge(listOf(MinRange(30, 90), MinRange(0, 45))))
    }

    @Test fun suggestionsGoToHigherPriorityFirst() {
        val s = Schedule.suggest(
            listOf(task(1, est = 30, priority = Priority.LOW), task(2, est = 60, priority = Priority.HIGH)),
            listOf(MinRange(540, 660))
        )
        assertEquals(540, s[2L])
        assertEquals(600, s[1L])
    }

    @Test fun taskThatDoesNotFitIsNotSuggested() {
        assertTrue(Schedule.suggest(listOf(task(1, est = 120)), listOf(MinRange(540, 600))).isEmpty())
    }

    @Test fun alreadyScheduledAndDoneTasksAreNotSuggested() {
        val s = Schedule.suggest(listOf(task(1, start = 600, est = 30), task(2, est = 30, done = true)), listOf(MinRange(540, 660)))
        assertTrue(s.isEmpty())
    }

    @Test fun allDayEventsDoNotBlockTime() {
        val e = CalEvent("Holiday", ms(day, 0, 0), ms(day.plusDays(1), 0, 0), allDay = true)
        assertTrue(Schedule.eventRangesOn(listOf(e), day, zone).isEmpty())
    }

    @Test fun eventsAreClippedToTheDay() {
        val e = CalEvent("Late", ms(day.minusDays(1), 23, 0), ms(day, 1, 0), allDay = false)
        assertEquals(listOf(MinRange(0, 60)), Schedule.eventRangesOn(listOf(e), day, zone))
    }

    @Test fun taskOverlappingAnEventIsAConflict() {
        val e = CalEvent("Standup", ms(day, 10, 0), ms(day, 10, 30), allDay = false)
        val c = Schedule.conflicts(listOf(task(1, start = 600, est = 60)), listOf(e), day, zone)
        assertEquals(1, c.size)
        assertEquals("Standup", c[0].second)
        assertEquals(MinRange(600, 630), c[0].overlap)
    }

    @Test fun twoOverlappingTasksAreOneConflict() {
        val c = Schedule.conflicts(listOf(task(1, start = 600, est = 60), task(2, start = 630, est = 30)), emptyList(), day, zone)
        assertEquals(1, c.size)
    }

    @Test fun backToBackTasksDoNotConflict() {
        val c = Schedule.conflicts(listOf(task(1, start = 600, est = 60), task(2, start = 660, est = 30)), emptyList(), day, zone)
        assertTrue(c.isEmpty())
    }
}
