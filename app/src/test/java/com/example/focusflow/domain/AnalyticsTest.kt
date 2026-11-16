package com.example.focusflow.domain

import com.example.focusflow.data.FocusSession
import com.example.focusflow.data.Interruption
import com.example.focusflow.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AnalyticsTest {
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 10, 29)

    private fun ms(d: LocalDate) = d.atTime(11, 0).atZone(zone).toInstant().toEpochMilli()

    private fun task(id: Long, date: String = "2026-10-29", done: Boolean = false, completed: LocalDate? = null, est: Int? = null) =
        Task(id = id, title = "t$id", date = date, done = done, completedAt = completed?.let { ms(it) }, estimateMin = est)

    private fun session(d: LocalDate, sec: Int, taskId: Long? = null, monitor: String = "on") =
        FocusSession(taskId = taskId, startedAt = ms(d), plannedMin = 25, activeSec = sec, endReason = "completed", monitorState = monitor)

    @Test fun dayStatsCountsEverythingForTheDay() {
        val tasks = listOf(task(1, done = true, completed = day), task(2), task(3, date = "2026-10-30"))
        val sessions = listOf(session(day, 600), session(day.minusDays(1), 900), session(day, 0))
        val ints = listOf(Interruption(sessionId = 1, packageName = "a", label = "A", startedAt = ms(day), durationSec = 40))
        val s = Analytics.dayStats(tasks, sessions, ints, day, zone)
        assertEquals(2, s.planned)
        assertEquals(1, s.completed)
        assertEquals(600, s.focusSec)
        assertEquals(1, s.sessions)
        assertEquals(1, s.interruptions)
        assertEquals(40, s.distractedSec)
    }

    @Test fun insightMentionsPlannedVersusCompleted() {
        val planned = listOf(task(1, done = true), task(2, done = true), task(3))
        val out = Analytics.insights(planned, 0, emptyList(), 0, 0, 0, 0, "today")
        assertTrue(out.any { it.contains("completed 2 of your 3 planned tasks today") })
    }

    @Test fun insightMentionsTasksThatTookLonger() {
        val samples = listOf(EstimateSample("a", 30, 60), EstimateSample("b", 30, 45))
        val out = Analytics.insights(emptyList(), 0, samples, 0, 0, 0, 0, "this week")
        assertTrue(out.any { it.contains("longer than estimated") })
    }

    @Test fun oneSampleIsNotEnoughToComment() {
        val out = Analytics.insights(emptyList(), 0, listOf(EstimateSample("a", 30, 90)), 0, 0, 0, 0, "today")
        assertTrue(out.none { it.contains("estimated") })
    }

    @Test fun frequentInterruptionsAreReported() {
        val out = Analytics.insights(emptyList(), 0, emptyList(), 0, 0, 2, 5, "today")
        assertTrue(out.any { it.contains("frequently interrupted") })
    }

    @Test fun noInterruptionsIsReportedOnlyForMonitoredSessions() {
        assertTrue(Analytics.insights(emptyList(), 0, emptyList(), 0, 0, 2, 0, "today").any { it.contains("No distracting app use") })
        assertTrue(Analytics.insights(emptyList(), 0, emptyList(), 0, 0, 0, 0, "today").none { it.contains("distracting") })
    }

    @Test fun emptyDataGivesAGentleMessage() {
        val out = Analytics.insights(emptyList(), 0, emptyList(), 0, 0, 0, 0, "today")
        assertEquals(1, out.size)
        assertTrue(out[0].startsWith("Not enough data"))
    }

    @Test fun estimateSamplesNeedEstimateAndFocusTime() {
        val tasks = listOf(
            task(1, done = true, completed = day, est = 30),
            task(2, done = true, completed = day, est = null),
            task(3, done = true, completed = day, est = 30)
        )
        val sessions = listOf(session(day, 45 * 60, taskId = 1))
        val s = Analytics.estimateSamples(tasks, sessions, day, day, zone)
        assertEquals(1, s.size)
        assertEquals(45, s[0].actualMin)
    }

    @Test fun monitoredSessionsCountOnlyMonitoredOnes() {
        val sessions = listOf(session(day, 600), session(day, 600, monitor = "off"), session(day, 0))
        assertEquals(1, Analytics.monitoredSessions(sessions, day, day, zone))
    }
}
