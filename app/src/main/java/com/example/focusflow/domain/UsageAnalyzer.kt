package com.example.focusflow.domain

import com.example.focusflow.data.Interruption

/** A foreground/background event for an app, as read from Android's usage log. */
data class UsageEvt(val pkg: String, val resumed: Boolean, val time: Long)

/** A stretch of time an app was in the foreground. */
data class Span(val pkg: String, val start: Long, val end: Long) {
    val ms: Long get() = end - start
}

data class TimeRange(val start: Long, val end: Long)

data class AppSummary(val label: String, val count: Int, val totalSec: Int)

/**
 * Turns raw usage events into interruption spans. Plain Kotlin, so it can be unit tested.
 * Android's usage log can lag by a few seconds, so tiny gaps are merged and very short glances are dropped.
 */
object UsageAnalyzer {
    const val MERGE_GAP_MS = 3_000L
    const val MIN_MS = 3_000L

    /** Foreground spans of the watched apps between [from] and [to]. */
    fun foregroundSpans(events: List<UsageEvt>, from: Long, to: Long, watched: Set<String>): List<Span> {
        val open = HashMap<String, Long>()
        val raw = ArrayList<Span>()

        fun close(pkg: String, t: Long) {
            val s = open.remove(pkg) ?: return
            if (t > s) raw.add(Span(pkg, s, t))
        }

        for (e in events.sortedBy { it.time }) {
            if (e.resumed) {
                // Another app coming to the foreground means the previous one has left it.
                for (p in open.keys.toList()) if (p != e.pkg) close(p, e.time)
                if (e.pkg !in open) open[e.pkg] = e.time
            } else {
                close(e.pkg, e.time)
            }
        }
        for (p in open.keys.toList()) close(p, to)

        val clipped = raw.filter { it.pkg in watched }.mapNotNull {
            val s = maxOf(it.start, from)
            val en = minOf(it.end, to)
            if (en > s) Span(it.pkg, s, en) else null
        }

        val merged = ArrayList<Span>()
        for (s in clipped.sortedWith(compareBy({ it.pkg }, { it.start }))) {
            val last = merged.lastOrNull()
            if (last != null && last.pkg == s.pkg && s.start - last.end <= MERGE_GAP_MS) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, s.end))
            } else {
                merged.add(s)
            }
        }
        return merged.sortedBy { it.start }
    }

    /** Removes the time the focus session itself was paused. */
    fun subtractPauses(spans: List<Span>, pauses: List<TimeRange>): List<Span> {
        val out = ArrayList<Span>()
        for (s in spans) {
            var pieces = listOf(s)
            for (p in pauses) pieces = pieces.flatMap { cut(it, p) }
            out.addAll(pieces)
        }
        return out
    }

    private fun cut(s: Span, p: TimeRange): List<Span> {
        if (p.end <= s.start || p.start >= s.end) return listOf(s)
        val res = ArrayList<Span>()
        if (p.start > s.start) res.add(Span(s.pkg, s.start, p.start))
        if (p.end < s.end) res.add(Span(s.pkg, p.end, s.end))
        return res
    }

    /** Drops very short glances. */
    fun significant(spans: List<Span>, minMs: Long = MIN_MS): List<Span> = spans.filter { it.ms >= minMs }

    fun summarize(items: List<Interruption>): List<AppSummary> =
        items.groupBy { it.packageName }
            .map { (_, list) -> AppSummary(list.first().label, list.size, list.sumOf { it.durationSec }) }
            .sortedByDescending { it.totalSec }
}
