package com.example.focusflow.domain

import com.example.focusflow.data.FocusSession
import com.example.focusflow.data.Interruption
import com.example.focusflow.data.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayStats(
    val date: LocalDate,
    val planned: Int,
    val completed: Int,
    val focusSec: Int,
    val sessions: Int,
    val interruptions: Int,
    val distractedSec: Int
)

data class EstimateSample(val title: String, val estMin: Int, val actualMin: Int)

data class AppUsage(val pkg: String, val label: String, val count: Int, val totalSec: Int)

data class DistractionSummary(val interruptions: Int, val sessionsAffected: Int, val totalSec: Int, val apps: List<AppUsage>)

/** Plain Kotlin statistics and rule-based insights. No AI, no network: every sentence comes from recorded data. */
object Analytics {
    private fun dayOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

    fun minutesText(min: Int): String = when {
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60} min"
    }

    fun dayStats(
        tasks: List<Task>, sessions: List<FocusSession>, interruptions: List<Interruption>,
        date: LocalDate, zone: ZoneId = ZoneId.systemDefault()
    ): DayStats {
        val d = date.toString()
        val sess = sessions.filter { dayOf(it.startedAt, zone) == date && it.activeSec > 0 }
        val ints = interruptions.filter { dayOf(it.startedAt, zone) == date }
        return DayStats(
            date = date,
            planned = tasks.count { it.date == d },
            completed = tasks.count { t -> t.done && t.completedAt?.let { dayOf(it, zone) == date } == true },
            focusSec = sess.sumOf { it.activeSec },
            sessions = sess.size,
            interruptions = ints.size,
            distractedSec = ints.sumOf { it.durationSec }
        )
    }

    fun plannedInRange(tasks: List<Task>, from: LocalDate, to: LocalDate): List<Task> =
        tasks.filter { it.date >= from.toString() && it.date <= to.toString() }

    fun interruptionsInRange(items: List<Interruption>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()) =
        items.filter { dayOf(it.startedAt, zone) in from..to }

    /** Number of focus sessions in the range during which app monitoring was on. */
    fun monitoredSessions(sessions: List<FocusSession>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int =
        sessions.count { it.monitorState == "on" && it.activeSec > 0 && dayOf(it.startedAt, zone) in from..to }

    /** Completed tasks that had an estimate and linked focus time, for comparing estimated with actual time. */
    fun estimateSamples(
        tasks: List<Task>, sessions: List<FocusSession>, from: LocalDate, to: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<EstimateSample> = tasks.mapNotNull { t ->
        val est = t.estimateMin ?: return@mapNotNull null
        val c = t.completedAt ?: return@mapNotNull null
        if (!t.done || dayOf(c, zone) !in from..to) return@mapNotNull null
        val actual = sessions.filter { it.taskId == t.id }.sumOf { it.activeSec } / 60
        if (actual < 1) null else EstimateSample(t.title, est, actual)
    }

    /** Totals and a per-app breakdown (longest first) of recorded interruptions. */
    fun distractionSummary(items: List<Interruption>): DistractionSummary = DistractionSummary(
        interruptions = items.size,
        sessionsAffected = items.map { it.sessionId }.distinct().size,
        totalSec = items.sumOf { it.durationSec },
        apps = items.groupBy { it.packageName }
            .map { (pkg, list) -> AppUsage(pkg, list.first().label, list.size, list.sumOf { it.durationSec }) }
            .sortedByDescending { it.totalSec }
    )

    fun insights(
        planned: List<Task>, overdueNow: Int, samples: List<EstimateSample>,
        focusSec: Int, sessionCount: Int, monitoredSessions: Int, interruptionCount: Int, label: String
    ): List<String> {
        val out = ArrayList<String>()
        if (planned.isNotEmpty()) {
            val done = planned.count { it.done }
            out.add("You completed $done of your ${plural(planned.size, "planned task")} $label.")
        }
        if (overdueNow > 0) {
            out.add("${plural(overdueNow, "task")} ${if (overdueNow == 1) "is" else "are"} overdue. Moving ${if (overdueNow == 1) "it" else "them"} in the planner keeps your plan realistic.")
        }
        if (samples.size >= 2) {
            val ratio = samples.sumOf { it.actualMin }.toDouble() / samples.sumOf { it.estMin }
            if (ratio > 1.25) out.add("Several tasks took longer than estimated (about ${Math.round((ratio - 1) * 100)}% longer in total).")
            else if (ratio < 0.75) out.add("Tasks took less time than estimated; you could plan a little more.")
        }
        if (monitoredSessions > 0) {
            val perSession = interruptionCount.toDouble() / monitoredSessions
            out.add(
                when {
                    interruptionCount == 0 -> "No distracting app use was recorded in your monitored focus sessions."
                    perSession >= 2 -> "Your focus sessions were frequently interrupted $label ($interruptionCount interruptions in ${plural(monitoredSessions, "session")})."
                    else -> "Some distracting app use was recorded ($interruptionCount interruptions in ${plural(monitoredSessions, "session")})."
                }
            )
        }
        if (focusSec > 0) out.add("You focused for ${minutesText(focusSec / 60)} across ${plural(sessionCount, "session")} $label.")
        if (out.isEmpty()) out.add("Not enough data yet. Plan tasks and run focus sessions to see insights.")
        return out
    }
}
