package com.example.focusflow.domain

import com.example.focusflow.data.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

data class DayLoad(
    val date: LocalDate,
    val taskCount: Int,
    val doneCount: Int,
    /** Estimated minutes of all tasks planned for the day (done and not done). */
    val plannedMin: Int,
    /** Estimated minutes of the tasks still to do. */
    val remainingMin: Int,
    /** Minutes available for the remaining tasks (what is left of today's work hours, or a full work day). */
    val capacityMin: Int,
    /** Tasks still to do that have no estimate, so they are not counted in the totals. */
    val unestimated: Int,
    /** Minutes by which the remaining work exceeds the available time (0 when it fits). */
    val overByMin: Int
)

/** A task (when [task] is set) or a calendar event (when null) placed at a time of day. */
data class TimelineItem(val startMin: Int, val endMin: Int, val title: String, val task: Task?)

data class DayTimeline(val allDay: List<String>, val items: List<TimelineItem>, val unscheduled: List<Task>)

/** Plain Kotlin planning rules (no Android code), so they can be unit tested. */
object PlanRules {
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Minutes available for work on [date]: nothing for past days, what is left of the work hours for today,
     * and the full work day for future days. Times are minutes after midnight.
     */
    fun capacity(date: LocalDate, today: LocalDate, nowMin: Int, workStart: Int, workEnd: Int): Int = when {
        date < today -> 0
        date == today -> (workEnd - maxOf(nowMin, workStart)).coerceAtLeast(0)
        else -> (workEnd - workStart).coerceAtLeast(0)
    }

    fun load(
        tasks: List<Task>, date: LocalDate, today: LocalDate,
        nowMin: Int, workStart: Int, workEnd: Int
    ): DayLoad {
        val day = date.toString()
        val onDay = tasks.filter { it.date == day }
        val pending = onDay.filter { !it.done }
        val remaining = pending.sumOf { it.estimateMin ?: 0 }
        val capacity = capacity(date, today, nowMin, workStart, workEnd)
        return DayLoad(
            date = date,
            taskCount = onDay.size,
            doneCount = onDay.count { it.done },
            plannedMin = onDay.sumOf { it.estimateMin ?: 0 },
            remainingMin = remaining,
            capacityMin = capacity,
            unestimated = pending.count { it.estimateMin == null },
            overByMin = if (date >= today) maxOf(0, remaining - capacity) else 0
        )
    }

    /** The weeks of a month as a flat list of whole weeks (Monday first); days outside the month are null. */
    fun monthCells(month: YearMonth): List<LocalDate?> {
        val cells = ArrayList<LocalDate?>()
        repeat(month.atDay(1).dayOfWeek.value - 1) { cells.add(null) }
        for (d in 1..month.lengthOfMonth()) cells.add(month.atDay(d))
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }

    /** Every date that has at least one calendar event on it. */
    fun eventDates(events: List<CalEvent>, zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> {
        val out = HashSet<LocalDate>()
        for (e in events) {
            // All-day events are stored in UTC, timed events in local time.
            val z = if (e.allDay) ZoneOffset.UTC else zone
            var d = Instant.ofEpochMilli(e.startMs).atZone(z).toLocalDate()
            val last = Instant.ofEpochMilli(maxOf(e.startMs, e.endMs - 1)).atZone(z).toLocalDate()
            var n = 0
            while (!d.isAfter(last) && n < 62) {
                out.add(d)
                d = d.plusDays(1)
                n++
            }
        }
        return out
    }

    /** One day laid out in time order: tasks with a start time, timed calendar events, and tasks without a time. */
    fun dayTimeline(tasks: List<Task>, events: List<CalEvent>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DayTimeline {
        val onDay = tasks.filter { it.date == date.toString() }
        val items = ArrayList<TimelineItem>()
        onDay.forEach { t -> Schedule.taskRange(t)?.let { items.add(TimelineItem(it.start, it.end, t.title, t)) } }
        events.forEach { e -> Schedule.rangeOf(e, date, zone)?.let { items.add(TimelineItem(it.start, it.end, e.title, null)) } }
        val unscheduled = TaskRules.sortForDisplay(onDay.filter { it.startMin == null && !it.done }) +
            onDay.filter { it.startMin == null && it.done }
        return DayTimeline(
            allDay = events.filter { it.allDay }.map { it.title },
            items = items.sortedWith(compareBy({ it.startMin }, { it.endMin })),
            unscheduled = unscheduled
        )
    }
}
