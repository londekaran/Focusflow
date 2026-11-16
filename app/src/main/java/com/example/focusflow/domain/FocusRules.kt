package com.example.focusflow.domain

import com.example.focusflow.data.FocusSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FocusStats(val sec: Int, val sessions: Int)

object FocusRules {
    fun sessionsOn(sessions: List<FocusSession>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<FocusSession> =
        sessions.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == day }

    /** Total focused time and number of sessions that started on the given day. Sessions with no focused time are ignored. */
    fun statsFor(sessions: List<FocusSession>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): FocusStats {
        val s = sessionsOn(sessions, day, zone).filter { it.activeSec > 0 }
        return FocusStats(sec = s.sumOf { it.activeSec }, sessions = s.size)
    }
}
