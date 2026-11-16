package com.example.focusflow.domain

import com.example.focusflow.data.Task
import com.example.focusflow.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class VoiceRulesTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 29)
    private val dayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun minute(m: Int) = dayStartMs + m * 60_000L

    private fun task(
        id: Long, start: Int? = null, est: Int? = null, date: String = "2026-10-29",
        done: Boolean = false, status: Int = TaskStatus.PENDING, priority: Int = 1
    ) = Task(id = id, title = "Task $id", date = date, startMin = start, estimateMin = est, done = done, status = status, priority = priority)

    private fun events(tasks: List<Task> = emptyList(), nowMs: Long = dayStartMs) =
        VoicePlan.events(tasks, today, nowMs, zone, 540, 1020, 30, 5, 5, 50)

    // ---------------------------------------------------------------- schedule of spoken events

    @Test fun briefingIsThirtyMinutesBeforeWorkAndWorkStartFollows() {
        val e = events().filter { it.key.endsWith("2026-10-29") }
        assertEquals(minute(510), e.first { it.kind == VoiceKind.MORNING }.atMs)
        assertEquals(minute(540), e.first { it.kind == VoiceKind.WORK_START }.atMs)
    }

    @Test fun statusChecksRunEveryFiftyMinutesInsideWorkHours() {
        val checks = events().filter { it.kind == VoiceKind.CHECKIN && it.key.contains("2026-10-29") }
        assertEquals(9, checks.size)
        assertEquals(minute(590), checks.first().atMs)
        assertEquals(minute(990), checks.last().atMs)
    }

    @Test fun eachScheduledTaskGetsAnIncomingAlertAndADeadlineQuestion() {
        val e = events(listOf(task(1, start = 600, est = 60)))
        assertEquals(minute(595), e.first { it.kind == VoiceKind.INCOMING }.atMs)
        assertEquals(minute(655), e.first { it.kind == VoiceKind.DEADLINE }.atMs)
    }

    @Test fun aTaskWithoutAnEstimateLastsThirtyMinutes() {
        val e = events(listOf(task(1, start = 600)))
        assertEquals(minute(625), e.first { it.kind == VoiceKind.DEADLINE }.atMs)
    }

    @Test fun veryShortTasksAreAskedHalfWayThrough() {
        val e = events(listOf(task(1, start = 600, est = 4)))
        assertEquals(minute(602), e.first { it.kind == VoiceKind.DEADLINE }.atMs)
    }

    @Test fun doneDiscardedAndUnscheduledTasksGetNoTaskEvents() {
        val e = events(listOf(task(1, start = 600, done = true), task(2, start = 600, status = TaskStatus.DISCARDED), task(3)))
        assertTrue(e.none { it.taskId != 0L })
    }

    @Test fun anOngoingTaskIsOnlyAskedAboutItsDeadline() {
        val e = events(listOf(task(1, start = 600, est = 60, status = TaskStatus.ONGOING)))
        assertTrue(e.none { it.kind == VoiceKind.INCOMING })
        assertEquals(1, e.count { it.kind == VoiceKind.DEADLINE })
    }

    @Test fun pastEventsAreLeftOut() {
        val e = events(nowMs = minute(545))
        assertTrue(e.none { it.key == "morning:2026-10-29" || it.key == "start:2026-10-29" })
        assertEquals(minute(590), e.first { it.kind == VoiceKind.CHECKIN }.atMs)
    }

    @Test fun tomorrowsBriefingIsAlreadyPlanned() {
        assertTrue(events().any { it.key == "morning:2026-10-30" })
    }

    @Test fun keysAreUnique() {
        val e = events(listOf(task(1, start = 600, est = 60), task(2, start = 700)))
        assertEquals(e.size, e.map { it.key }.toSet().size)
    }

    @Test fun checkInsCanBeSwitchedOff() {
        val e = VoicePlan.events(emptyList(), today, dayStartMs, zone, 540, 1020, 30, 5, 5, 0)
        assertTrue(e.none { it.kind == VoiceKind.CHECKIN })
    }

    // ---------------------------------------------------------------- extending a task

    @Test fun extendingPushesOverlappingTasksForward() {
        val tasks = listOf(task(1, 540, 60), task(2, 600, 30), task(3, 660, 30))
        val changed = Replan.extend(tasks, 1, 30).associateBy { it.id }
        assertEquals(90, changed.getValue(1).estimateMin)
        assertEquals(630, changed.getValue(2).startMin)
        assertNull(changed[3L])   // task 3 already starts when task 2 now ends, so it stays put
        assertEquals(setOf(1L, 2L), changed.keys)
    }

    @Test fun aChainOfBackToBackTasksAllMoveForward() {
        val tasks = listOf(task(1, 540, 60), task(2, 600, 60), task(3, 660, 60))
        val changed = Replan.extend(tasks, 1, 15).associateBy { it.id }
        assertEquals(615, changed.getValue(2).startMin)
        assertEquals(675, changed.getValue(3).startMin)
    }

    @Test fun aGapAbsorbsTheDelay() {
        val tasks = listOf(task(1, 540, 60), task(2, 660, 30))
        val changed = Replan.extend(tasks, 1, 30)
        assertEquals(listOf(1L), changed.map { it.id })
    }

    @Test fun orderOfTheListDoesNotMatter() {
        val tasks = listOf(task(3, 660, 60), task(1, 540, 60), task(2, 600, 60))
        val changed = Replan.extend(tasks, 1, 15).associateBy { it.id }
        assertEquals(615, changed.getValue(2).startMin)
        assertEquals(675, changed.getValue(3).startMin)
    }

    @Test fun otherDaysAndFinishedTasksStayPut() {
        val tasks = listOf(task(1, 540, 60), task(2, 600, 30, date = "2026-10-30"), task(3, 600, 30, done = true))
        assertEquals(listOf(1L), Replan.extend(tasks, 1, 30).map { it.id })
    }

    @Test fun earlierTasksAreNotMoved() {
        val tasks = listOf(task(1, 480, 30), task(2, 540, 60), task(3, 600, 30))
        assertTrue(Replan.extend(tasks, 2, 30).none { it.id == 1L })
    }

    @Test fun extendingAnUnscheduledTaskOnlyChangesItsEstimate() {
        val changed = Replan.extend(listOf(task(1, est = 20)), 1, 10)
        assertEquals(30, changed.single().estimateMin)
        assertNull(changed.single().startMin)
    }

    @Test fun aMissingTaskChangesNothing() {
        assertTrue(Replan.extend(listOf(task(1, 540, 60)), 99, 30).isEmpty())
    }

    // ---------------------------------------------------------------- what is said

    @Test fun clockIsReadNaturally() {
        assertEquals("12:00 AM", VoiceMessages.clock(0))
        assertEquals("9:30 AM", VoiceMessages.clock(570))
        assertEquals("12:15 PM", VoiceMessages.clock(735))
        assertEquals("1:00 PM", VoiceMessages.clock(780))
    }

    @Test fun morningWithNoTasksAsksForTasks() {
        val c = VoiceMessages.build(VoiceKind.MORNING, 0, emptyList(), today, 510)
        assertEquals(listOf("No tasks added—please add tasks."), c?.speech)
        assertEquals("tasks", c?.route)
    }

    @Test fun morningListsTasksInTimeOrder() {
        val c = VoiceMessages.build(VoiceKind.MORNING, 0, listOf(task(2, 600), task(1, 540)), today, 510)
        assertNotNull(c)
        assertEquals("Good morning, these are your planned tasks for today.", c!!.speech[0])
        assertEquals("First, Task 1 at 9:00 AM.", c.speech[1])
        assertEquals("Then, Task 2 at 10:00 AM.", c.speech[2])
    }

    @Test fun workStartUsesItsOwnGreeting() {
        val c = VoiceMessages.build(VoiceKind.WORK_START, 0, listOf(task(1, 540)), today, 540)
        assertEquals("Good morning, let's start today's work. Here are your tasks.", c?.speech?.first())
    }

    @Test fun workStartWithNoTasksStillAsksForTasks() {
        val c = VoiceMessages.build(VoiceKind.WORK_START, 0, emptyList(), today, 540)
        assertTrue(c!!.speech.contains("No tasks added—please add tasks."))
    }

    @Test fun incomingOffersOngoingPendingAndDiscard() {
        val c = VoiceMessages.build(VoiceKind.INCOMING, 1, listOf(task(1, 600)), today, 595)
        assertEquals(listOf(VoiceOp.ONGOING, VoiceOp.PENDING, VoiceOp.DISCARD), c!!.actions.map { it.op })
        assertTrue(c.speech.single().startsWith("Task incoming: Task 1, starting in 5 minutes."))
    }

    @Test fun incomingIsSkippedWhenTheTaskIsNoLongerRelevant() {
        assertNull(VoiceMessages.build(VoiceKind.INCOMING, 1, listOf(task(1, 600, done = true)), today, 595))
        assertNull(VoiceMessages.build(VoiceKind.INCOMING, 1, listOf(task(1, 600, status = TaskStatus.ONGOING)), today, 595))
        assertNull(VoiceMessages.build(VoiceKind.INCOMING, 1, listOf(task(1, 600, status = TaskStatus.DISCARDED)), today, 595))
    }

    @Test fun deadlineAsksForTheStatusAndOffersMoreTime() {
        val c = VoiceMessages.build(VoiceKind.DEADLINE, 1, listOf(task(1, 600, 60)), today, 655)
        assertEquals("Task 1 ends soon. Is it ongoing or pending? If ongoing, you can add fifteen or thirty minutes.", c!!.speech.single())
        assertEquals(listOf(VoiceOp.EXTEND, VoiceOp.EXTEND, VoiceOp.PENDING), c.actions.map { it.op })
        assertEquals(listOf(15, 30, 0), c.actions.map { it.minutes })
    }

    @Test fun deadlineIsSkippedForFinishedTasks() {
        assertNull(VoiceMessages.build(VoiceKind.DEADLINE, 1, listOf(task(1, 600, 60, done = true)), today, 655))
    }

    @Test fun checkInPrefersTheOngoingTask() {
        val tasks = listOf(task(1, 540, 120), task(2, 580, 30, status = TaskStatus.ONGOING))
        val c = VoiceMessages.build(VoiceKind.CHECKIN, 0, tasks, today, 590)
        assertEquals("Status check. Are you still working on Task 2?", c!!.speech.single())
    }

    @Test fun checkInFallsBackToTheTaskScheduledNow() {
        val c = VoiceMessages.build(VoiceKind.CHECKIN, 0, listOf(task(1, 540, 120)), today, 590)
        assertEquals("Status check. Are you still working on Task 1?", c!!.speech.single())
    }

    @Test fun checkInWithNoActiveTaskNamesTheNextOne() {
        val c = VoiceMessages.build(VoiceKind.CHECKIN, 0, listOf(task(1, 700, 30)), today, 590)
        assertEquals("Status check. No task is active right now.", c!!.speech[0])
        assertEquals("Next up: Task 1 at 11:40 AM.", c.speech[1])
    }

    @Test fun checkInWithNoTasksAsksForTasks() {
        val c = VoiceMessages.build(VoiceKind.CHECKIN, 0, emptyList(), today, 590)
        assertTrue(c!!.speech.single().contains("Please add tasks"))
    }
}
