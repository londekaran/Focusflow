package com.example.focusflow.system

import android.content.Context
import com.example.focusflow.data.AppDb
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.data.TaskStatus
import com.example.focusflow.domain.Replan
import com.example.focusflow.domain.Schedule
import com.example.focusflow.domain.VoiceMessages
import com.example.focusflow.domain.VoiceOp
import com.example.focusflow.domain.VoicePlan
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What the answers do. Used by the notification buttons and by the in-app status dialog, so both behave the same.
 * Every change refreshes all alarms, so moved tasks get their new reminders straight away.
 */
object VoiceActions {
    /** Applies [op] to the task and returns a short sentence to confirm it (or null if the task is gone). */
    suspend fun apply(ctx: Context, op: VoiceOp, taskId: Long, minutes: Int = 0): String? {
        val dao = AppDb.get(ctx).dao()
        val t = dao.getById(taskId) ?: return null
        val now = System.currentTimeMillis()
        val message = when (op) {
            VoiceOp.ONGOING -> {
                dao.update(t.copy(status = TaskStatus.ONGOING, updatedAt = now))
                "Okay, ${t.title} is ongoing."
            }
            VoiceOp.PENDING -> {
                // Pending: not started yet, so it no longer holds a time slot.
                dao.update(t.copy(status = TaskStatus.PENDING, startMin = null, updatedAt = now))
                "Okay, ${t.title} is pending."
            }
            VoiceOp.DISCARD -> {
                dao.update(t.copy(status = TaskStatus.DISCARDED, updatedAt = now))
                "Discarded ${t.title}."
            }
            VoiceOp.DONE -> {
                dao.update(t.copy(done = true, completedAt = now, status = TaskStatus.PENDING, updatedAt = now))
                "Great, ${t.title} is done."
            }
            VoiceOp.EXTEND -> extend(ctx, t.id, t.title, minutes, now)
        }
        ReminderScheduler.scheduleAll(ctx)
        return message
    }

    private suspend fun extend(ctx: Context, id: Long, title: String, minutes: Int, now: Long): String {
        val dao = AppDb.get(ctx).dao()
        val changed = Replan.extend(dao.getAll(), id, minutes)
        changed.forEach { dao.update(it.copy(updatedAt = now, status = if (it.id == id) TaskStatus.ONGOING else it.status)) }
        val moved = changed.filter { it.id != id }.sortedBy { it.startMin }
        val st = ServiceLocator.settings(ctx)
        val cat = dao.getById(id)?.category
        val workEnd = com.example.focusflow.domain.Routine.decode(st.routine).firstOrNull { it.name == cat && it.start < it.end }?.end ?: st.dayEndMin
        val lastEnd = changed.maxOfOrNull { (it.startMin ?: 0) + VoicePlan.durationOf(it) } ?: 0
        var text = "Extended $title by $minutes minutes."
        moved.firstOrNull()?.let { text += " ${it.title} now starts at ${VoiceMessages.clock(it.startMin ?: 0)}." }
        if (moved.size > 1) text += " ${moved.size - 1} more task${if (moved.size == 2) "" else "s"} moved too."
        if (changed.any { it.startMin != null } && lastEnd > workEnd) text += " Your schedule now runs past the end of this routine."
        return text
    }

    /** Gives today's tasks that have no time a start time in the free slots of the work day. Returns how many were placed. */
    suspend fun autoScheduleToday(ctx: Context): Int {
        val s = ServiceLocator.settings(ctx)
        val dao = AppDb.get(ctx).dao()
        val today = LocalDate.now()
        val day = today.toString()
        val todayTasks = dao.getAll().filter { it.date == day && !it.done }
        val unscheduled = todayTasks.filter { it.startMin == null }
        if (unscheduled.isEmpty()) return 0
        val events = if (s.calendarEnabled && CalendarReader.hasPermission(ctx)) CalendarReader.eventsOn(ctx, today) else emptyList()
        val busy = (Schedule.eventRangesOn(events, today, ZoneId.systemDefault()) + todayTasks.mapNotNull { Schedule.taskRange(it) }).toMutableList()
        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val blocks = com.example.focusflow.domain.Routine.decode(s.routine)
        val now = System.currentTimeMillis()
        var count = 0
        // Each task is placed inside its own routine's time window (work hours when it has no routine).
        unscheduled.groupBy { it.category }.forEach { (cat, group) ->
            val b = blocks.firstOrNull { it.name == cat && it.start < it.end }
            val ws = b?.start ?: s.dayStartMin
            val we = b?.end ?: s.dayEndMin
            val free = Schedule.freeSlots(ws, we, busy, maxOf(ws, nowMin))
            Schedule.suggest(group, free).forEach { (id, start) ->
                dao.getById(id)?.let { t ->
                    dao.update(t.copy(startMin = start, updatedAt = now))
                    Schedule.taskRange(t.copy(startMin = start))?.let { busy.add(it) }
                    count++
                }
            }
        }
        val placed = count
        return placed
    }
}
