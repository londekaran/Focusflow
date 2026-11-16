package com.example.focusflow.domain

import com.example.focusflow.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class PlanTimelineTest {
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 10, 29)

    private fun ms(d: LocalDate, h: Int, m: Int) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun task(id: Long, date: String = "2026-10-29", start: Int? = null, est: Int? = null, done: Boolean = false) =
        Task(id = id, title = "t$id", date = date, startMin = start, estimateMin = est, done = done)

    @Test fun timelineIsSortedAndMixesTasksWithEvents() {
        val tasks = listOf(task(1, start = 600, est = 60), task(2, start = 540), task(3), task(4, date = "2026-10-30", start = 600))
        val events = listOf(
            CalEvent("Call", ms(day, 9, 45), ms(day, 10, 15), allDay = false),
            CalEvent("Holiday", ms(day, 0, 0), ms(day.plusDays(1), 0, 0), allDay = true)
        )
        val t = PlanRules.dayTimeline(tasks, events, day, zone)
        assertEquals(listOf("t2", "Call", "t1"), t.items.map { it.title })
        assertEquals(listOf(3L), t.unscheduled.map { it.id })
        assertEquals(listOf("Holiday"), t.allDay)
    }

    @Test fun eventsAreMarkedAsNotTasks() {
        val events = listOf(CalEvent("Call", ms(day, 9, 0), ms(day, 10, 0), allDay = false))
        val t = PlanRules.dayTimeline(emptyList(), events, day, zone)
        assertNull(t.items.single().task)
    }

    @Test fun unscheduledTasksPutOpenOnesFirst() {
        val tasks = listOf(task(1, done = true), task(2))
        val t = PlanRules.dayTimeline(tasks, emptyList(), day, zone)
        assertEquals(listOf(2L, 1L), t.unscheduled.map { it.id })
    }

    @Test fun anEmptyDayHasAnEmptyTimeline() {
        val t = PlanRules.dayTimeline(emptyList(), emptyList(), day, zone)
        assertTrue(t.items.isEmpty() && t.unscheduled.isEmpty() && t.allDay.isEmpty())
    }

    @Test fun monthStartsOnTheRightWeekday() {
        // 1 October 2026 is a Thursday, so three blanks come first.
        val cells = PlanRules.monthCells(YearMonth.of(2026, 10))
        assertEquals(35, cells.size)
        assertEquals(listOf(null, null, null), cells.take(3))
        assertEquals(LocalDate.of(2026, 10, 1), cells[3])
        assertEquals(LocalDate.of(2026, 10, 31), cells[33])
        assertNull(cells[34])
    }

    @Test fun monthWithASundayStartLeavesSixBlanks() {
        // 1 February 2026 is a Sunday.
        val cells = PlanRules.monthCells(YearMonth.of(2026, 2))
        assertEquals(6, cells.takeWhile { it == null }.size)
        assertEquals(35, cells.size)
    }

    @Test fun aMonthCanNeedSixWeeks() {
        // 1 August 2026 is a Saturday and August has 31 days.
        assertEquals(42, PlanRules.monthCells(YearMonth.of(2026, 8)).size)
    }

    @Test fun everyDayOfTheMonthAppearsOnce() {
        val cells = PlanRules.monthCells(YearMonth.of(2026, 10)).filterNotNull()
        assertEquals(31, cells.size)
        assertEquals(31, cells.toSet().size)
    }

    @Test fun eventDatesCoverEveryDayAnEventTouches() {
        val spanning = CalEvent("Trip", ms(day, 22, 0), ms(day.plusDays(1), 2, 0), allDay = false)
        assertEquals(setOf(day, day.plusDays(1)), PlanRules.eventDates(listOf(spanning), zone))
    }

    @Test fun allDayEventsUseTheirOwnUtcDate() {
        val utcStart = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val allDay = CalEvent("Holiday", utcStart, utcStart + 24 * 3_600_000L, allDay = true)
        assertEquals(setOf(day), PlanRules.eventDates(listOf(allDay), ZoneId.of("America/Los_Angeles")))
    }
}
