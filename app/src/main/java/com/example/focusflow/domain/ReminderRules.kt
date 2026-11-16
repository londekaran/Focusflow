package com.example.focusflow.domain

import com.example.focusflow.data.FocusSession
import com.example.focusflow.data.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class Msg(val title: String, val text: String)

/** Plain Kotlin rules for when reminders fire and what they say. */
object ReminderRules {
    private fun hm(min: Int): String = when {
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60} min"
    }

    private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

    /** The next time (strictly after [now]) the given time of day occurs. */
    fun nextOccurrence(minOfDay: Int, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(minOfDay / 60, minOfDay % 60)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** When to remind about a task with a start time, or null if it has none. */
    fun taskAlarmAt(t: Task, leadMin: Int, zone: ZoneId): Long? {
        val s = t.startMin ?: return null
        return LocalDate.parse(t.date).atTime(s / 60, s % 60).atZone(zone).toInstant().toEpochMilli() - leadMin * 60_000L
    }

    fun morning(tasks: List<Task>, today: LocalDate): Msg? {
        val st = TaskRules.stats(tasks, today)
        val dueToday = tasks.filter { !it.done && it.dueDate == today.toString() }
        if (st.today == 0 && st.overdue == 0 && dueToday.isEmpty()) return null
        val parts = ArrayList<String>()
        if (st.today > 0) {
            parts.add(plural(st.today, "task") + " planned" + if (st.plannedMinutesToday > 0) " (about ${hm(st.plannedMinutesToday)})" else "")
        }
        if (st.overdue > 0) parts.add("${st.overdue} overdue")
        if (dueToday.isNotEmpty()) parts.add("due today: " + dueToday.take(2).joinToString(", ") { it.title })
        return Msg("Today's plan", parts.joinToString("  ·  "))
    }

    fun followUp(tasks: List<Task>, today: LocalDate): Msg? {
        val left = TaskRules.sortForDisplay(TaskRules.overdueList(tasks, today) + TaskRules.todayList(tasks, today))
        if (left.isEmpty()) return null
        val names = left.take(2).joinToString(", ") { it.title }
        val more = if (left.size > 2) " and ${left.size - 2} more" else ""
        return Msg("Still to do today", "${plural(left.size, "task")} unfinished: $names$more")
    }

    fun review(tasks: List<Task>, sessions: List<FocusSession>, today: LocalDate): Msg {
        val st = TaskRules.stats(tasks, today)
        val f = FocusRules.statsFor(sessions, today)
        val unfinished = st.today + st.overdue
        return Msg(
            "Daily review",
            "${st.completedToday} done, $unfinished unfinished. Focus: ${hm(f.sec / 60)} in ${plural(f.sessions, "session")}. Tap to review."
        )
    }
}
