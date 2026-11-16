package com.example.focusflow.domain

import com.example.focusflow.data.FocusSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FocusRulesTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val zone = ZoneId.systemDefault()

    private fun at(d: LocalDate) = d.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun session(day: LocalDate, sec: Int) =
        FocusSession(startedAt = at(day), plannedMin = 25, activeSec = sec, endReason = "completed")

    @Test fun sumsOnlyTodaysSessions() {
        val list = listOf(session(today, 600), session(today, 300), session(today.minusDays(1), 900))
        val s = FocusRules.statsFor(list, today, zone)
        assertEquals(900, s.sec)
        assertEquals(2, s.sessions)
    }

    @Test fun ignoresSessionsWithNoFocusedTime() {
        val s = FocusRules.statsFor(listOf(session(today, 0), session(today, 120)), today, zone)
        assertEquals(120, s.sec)
        assertEquals(1, s.sessions)
    }

    @Test fun emptyListGivesZero() {
        val s = FocusRules.statsFor(emptyList(), today, zone)
        assertEquals(0, s.sec)
        assertEquals(0, s.sessions)
    }
}
