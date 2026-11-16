package com.example.focusflow.domain

import com.example.focusflow.data.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TaskStats(
    val completedToday: Int,
    val pending: Int,
    val overdue: Int,
    val today: Int,
    val upcoming: Int,
    val plannedMinutesToday: Int
)

/**
 * Plain Kotlin rules (no Android code), so they can be unit tested.
 * Dates are yyyy-MM-dd strings, which sort correctly as text.
 *
 * - A task's deadline is its due date, or its planned day if it has no due date.
 * - Overdue = not done and the deadline is before today.
 * - Today = not done, not overdue, planned for today or earlier.
 * - Upcoming = not done and planned after today.
 */
object TaskRules {
    fun effectiveDue(t: Task): String = t.dueDate ?: t.date

    fun isOverdue(t: Task, today: LocalDate): Boolean = !t.done && effectiveDue(t) < today.toString()

    fun todayList(tasks: List<Task>, today: LocalDate): List<Task> =
        tasks.filter { !it.done && !isOverdue(it, today) && it.date <= today.toString() }

    fun upcomingList(tasks: List<Task>, today: LocalDate): List<Task> =
        tasks.filter { !it.done && it.date > today.toString() }

    fun overdueList(tasks: List<Task>, today: LocalDate): List<Task> =
        tasks.filter { isOverdue(it, today) }

    fun doneList(tasks: List<Task>): List<Task> =
        tasks.filter { it.done }.sortedByDescending { it.completedAt ?: 0L }

    fun completedToday(tasks: List<Task>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Task> =
        tasks.filter {
            val c = it.completedAt ?: return@filter false
            it.done && Instant.ofEpochMilli(c).atZone(zone).toLocalDate() == today
        }

    fun nextDeadlines(tasks: List<Task>, today: LocalDate, limit: Int = 3): List<Task> =
        tasks.filter {
            val d = it.dueDate ?: return@filter false
            !it.done && d >= today.toString()
        }.sortedBy { it.dueDate }.take(limit)

    fun sortForDisplay(tasks: List<Task>): List<Task> =
        tasks.sortedWith(
            compareByDescending<Task> { it.priority }
                .thenBy { it.startMin ?: Int.MAX_VALUE }
                .thenBy { it.dueDate ?: "9999-12-31" }
                .thenBy { it.createdAt }
        )

    fun stats(tasks: List<Task>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): TaskStats {
        val todayItems = todayList(tasks, today)
        return TaskStats(
            completedToday = completedToday(tasks, today, zone).size,
            pending = tasks.count { !it.done && !isOverdue(it, today) },
            overdue = tasks.count { isOverdue(it, today) },
            today = todayItems.size,
            upcoming = upcomingList(tasks, today).size,
            plannedMinutesToday = todayItems.sumOf { it.estimateMin ?: 0 }
        )
    }

    /**
     * Moves a task to another day. If its deadline would then fall before the new day, the deadline moves too,
     * so a rescheduled task is no longer overdue. The old dates are kept in the task's move history.
     */
    fun reschedule(t: Task, to: LocalDate): Task {
        val d = to.toString()
        val newDue = t.dueDate?.let { if (it < d) d else it }
        return t.copy(date = d, dueDate = newDue)
    }

    /** Done and total tasks planned for [today]. */
    fun todayProgress(tasks: List<Task>, today: LocalDate): Pair<Int, Int> {
        val onDay = tasks.filter { it.date == today.toString() }
        return onDay.count { it.done } to onDay.size
    }

    /** The task to work on next: highest priority among today's open tasks, earliest start time breaking ties. */
    fun nextFocusTask(tasks: List<Task>, today: LocalDate): Task? =
        sortForDisplay(todayList(tasks, today)).firstOrNull()

    /** The next open tasks in date and time order. Overdue tasks are left out. */
    fun upcomingScheduled(tasks: List<Task>, today: LocalDate, excludeId: Long?, limit: Int): List<Task> =
        tasks.filter { !it.done && !isOverdue(it, today) && it.id != excludeId }
            .sortedWith(compareBy<Task>({ it.date }, { it.startMin ?: Int.MAX_VALUE }).thenByDescending { it.priority })
            .take(limit)

    fun summaryText(tasks: List<Task>, today: LocalDate): String {
        val s = stats(tasks, today)
        return "${s.completedToday} completed today, ${s.pending} pending, ${s.overdue} overdue."
    }
}
