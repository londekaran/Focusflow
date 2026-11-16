package com.example.focusflow.domain

import com.example.focusflow.data.Interruption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAnalyzerTest {
    private val watched = setOf("insta")

    private fun e(pkg: String, resumed: Boolean, t: Long) = UsageEvt(pkg, resumed, t)

    @Test fun singleForegroundSpan() {
        val s = UsageAnalyzer.foregroundSpans(listOf(e("insta", true, 10_000), e("insta", false, 70_000)), 0, 100_000, watched)
        assertEquals(1, s.size)
        assertEquals(60_000L, s[0].ms)
    }

    @Test fun openSpanIsClosedAtSessionEnd() {
        val s = UsageAnalyzer.foregroundSpans(listOf(e("insta", true, 90_000)), 0, 100_000, watched)
        assertEquals(10_000L, s.single().ms)
    }

    @Test fun switchingToAnotherAppEndsTheSpan() {
        val s = UsageAnalyzer.foregroundSpans(listOf(e("insta", true, 10_000), e("other", true, 40_000)), 0, 100_000, watched)
        assertEquals(30_000L, s.single().ms)
    }

    @Test fun unwatchedAppsAreIgnored() {
        val s = UsageAnalyzer.foregroundSpans(listOf(e("other", true, 10_000), e("other", false, 50_000)), 0, 100_000, watched)
        assertTrue(s.isEmpty())
    }

    @Test fun shortGapsInTheSameAppAreMerged() {
        val events = listOf(
            e("insta", true, 10_000), e("insta", false, 20_000),
            e("insta", true, 20_500), e("insta", false, 30_000)
        )
        val s = UsageAnalyzer.foregroundSpans(events, 0, 100_000, watched)
        assertEquals(1, s.size)
        assertEquals(20_000L, s[0].ms)
    }

    @Test fun gapsLongerThanThreeSecondsSplitTheSpan() {
        val events = listOf(
            e("insta", true, 10_000), e("insta", false, 20_000),
            e("insta", true, 30_000), e("insta", false, 40_000)
        )
        assertEquals(2, UsageAnalyzer.foregroundSpans(events, 0, 100_000, watched).size)
    }

    @Test fun spansAreClippedToTheSession() {
        val s = UsageAnalyzer.foregroundSpans(listOf(e("insta", true, 1_000), e("insta", false, 20_000)), 5_000, 100_000, watched)
        assertEquals(15_000L, s.single().ms)
    }

    @Test fun sessionPausesAreSubtracted() {
        val spans = listOf(Span("insta", 10_000, 70_000))
        val result = UsageAnalyzer.subtractPauses(spans, listOf(TimeRange(30_000, 40_000)))
        assertEquals(2, result.size)
        assertEquals(50_000L, result.sumOf { it.ms })
    }

    @Test fun briefGlancesAreDropped() {
        assertTrue(UsageAnalyzer.significant(listOf(Span("insta", 0, 2_000))).isEmpty())
        assertEquals(1, UsageAnalyzer.significant(listOf(Span("insta", 0, 3_000))).size)
    }

    @Test fun summaryGroupsByAppAndSortsByTime() {
        val items = listOf(
            Interruption(sessionId = 1, packageName = "a", label = "A", startedAt = 0, durationSec = 60),
            Interruption(sessionId = 1, packageName = "b", label = "B", startedAt = 0, durationSec = 300),
            Interruption(sessionId = 1, packageName = "a", label = "A", startedAt = 0, durationSec = 30)
        )
        val s = UsageAnalyzer.summarize(items)
        assertEquals("B", s[0].label)
        assertEquals(2, s[1].count)
        assertEquals(90, s[1].totalSec)
    }
}
