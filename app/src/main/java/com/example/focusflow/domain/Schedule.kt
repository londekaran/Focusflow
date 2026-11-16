package com.example.focusflow.domain

import com.example.focusflow.data.Task
import java.time.LocalDate
import java.time.ZoneId

data class CalEvent(val title: String, val startMs: Long, val endMs: Long, val allDay: Boolean)

/** A stretch of a day, in minutes after midnight. */
data class MinRange(val start: Int, val end: Int) {
    val len: Int get() = end - start
}

data class Conflict(val first: String, val second: String, val overlap: MinRange)

/** Plain Kotlin scheduling rules (no Android code), so they can be unit tested. */
object Schedule {
    const val DEFAULT_TASK_MIN = 30

    fun rangeOf(e: CalEvent, date: LocalDate, zone: ZoneId): MinRange? {
        if (e.allDay) return null
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val s = ((maxOf(e.startMs, dayStart) - dayStart) / 60_000).toInt()
        val en = ((minOf(e.endMs, dayEnd) - dayStart) / 60_000).toInt()
        return if (en > s) MinRange(s, en) else null
    }

    /** Timed (not all-day) events as minute ranges of the given day. */
    fun eventRangesOn(events: List<CalEvent>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<MinRange> =
        events.mapNotNull { rangeOf(it, date, zone) }

    /** The time a task with a start time occupies; tasks without an estimate count as 30 minutes. */
    fun taskRange(t: Task): MinRange? = t.startMin?.let { MinRange(it, it + (t.estimateMin ?: DEFAULT_TASK_MIN)) }

    fun merge(ranges: List<MinRange>): List<MinRange> {
        val out = ArrayList<MinRange>()
        for (r in ranges.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && r.start <= last.end) out[out.lastIndex] = MinRange(last.start, maxOf(last.end, r.end))
            else out.add(r)
        }
        return out
    }

    /** Free time inside the work hours, from [from] onwards, with starts rounded up to 5 minutes. */
    fun freeSlots(workStart: Int, workEnd: Int, busy: List<MinRange>, from: Int = workStart, minLen: Int = 15): List<MinRange> {
        val out = ArrayList<MinRange>()
        var cursor = maxOf(workStart, from)
        for (b in merge(busy)) {
            if (b.end <= cursor) continue
            if (b.start > cursor) addFree(out, cursor, minOf(b.start, workEnd), minLen)
            cursor = maxOf(cursor, b.end)
            if (cursor >= workEnd) break
        }
        if (cursor < workEnd) addFree(out, cursor, workEnd, minLen)
        return out
    }

    private fun addFree(out: MutableList<MinRange>, start: Int, end: Int, minLen: Int) {
        val s = (start + 4) / 5 * 5
        if (end - s >= minLen) out.add(MinRange(s, end))
    }

    /** Proposes start times for tasks without one: highest priority first, each in the earliest slot where it fits. */
    fun suggest(tasks: List<Task>, free: List<MinRange>): Map<Long, Int> {
        val slots = free.toMutableList()
        val out = LinkedHashMap<Long, Int>()
        for (t in TaskRules.sortForDisplay(tasks.filter { !it.done && it.startMin == null })) {
            val need = t.estimateMin ?: DEFAULT_TASK_MIN
            val i = slots.indexOfFirst { it.len >= need }
            if (i < 0) continue
            val s = slots[i]
            out[t.id] = s.start
            slots[i] = MinRange(s.start + need, s.end)
        }
        return out
    }

    private fun overlap(a: MinRange, b: MinRange): MinRange? {
        val s = maxOf(a.start, b.start)
        val e = minOf(a.end, b.end)
        return if (e > s) MinRange(s, e) else null
    }

    /** Scheduled tasks that overlap a calendar event or another scheduled task. */
    fun conflicts(tasks: List<Task>, events: List<CalEvent>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Conflict> {
        val evs = events.mapNotNull { e -> rangeOf(e, date, zone)?.let { e.title to it } }
        val scheduled = tasks.filter { !it.done }.mapNotNull { t -> taskRange(t)?.let { t.title to it } }
        val out = ArrayList<Conflict>()
        for ((i, a) in scheduled.withIndex()) {
            for ((title, r) in evs) overlap(a.second, r)?.let { out.add(Conflict(a.first, title, it)) }
            for (j in i + 1 until scheduled.size) {
                overlap(a.second, scheduled[j].second)?.let { out.add(Conflict(a.first, scheduled[j].first, it)) }
            }
        }
        return out
    }
}
