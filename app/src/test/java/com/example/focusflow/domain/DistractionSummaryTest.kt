package com.example.focusflow.domain

import com.example.focusflow.data.Interruption
import org.junit.Assert.assertEquals
import org.junit.Test

class DistractionSummaryTest {
    private fun item(session: Long, pkg: String, label: String, sec: Int) =
        Interruption(sessionId = session, packageName = pkg, label = label, startedAt = 0, durationSec = sec)

    @Test fun emptyListGivesZeros() {
        val s = Analytics.distractionSummary(emptyList())
        assertEquals(0, s.interruptions)
        assertEquals(0, s.sessionsAffected)
        assertEquals(0, s.totalSec)
        assertEquals(0, s.apps.size)
    }

    @Test fun totalsAndSessionsAreCounted() {
        val s = Analytics.distractionSummary(
            listOf(item(1, "a", "A", 60), item(1, "b", "B", 30), item(2, "a", "A", 90))
        )
        assertEquals(3, s.interruptions)
        assertEquals(2, s.sessionsAffected)
        assertEquals(180, s.totalSec)
    }

    @Test fun appsAreSortedByTimeWithCounts() {
        val s = Analytics.distractionSummary(
            listOf(item(1, "a", "A", 60), item(1, "b", "B", 300), item(2, "a", "A", 90))
        )
        assertEquals(listOf("b", "a"), s.apps.map { it.pkg })
        assertEquals(2, s.apps[1].count)
        assertEquals(150, s.apps[1].totalSec)
    }
}
