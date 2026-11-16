package com.example.focusflow.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.focusflow.data.AppDb
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.domain.ReminderRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sets Android alarms for reminders. By default alarms are inexact (Android may delay them a few minutes to save
 * battery). Exact timing only applies if the user turns it on and Android allows it.
 */
object ReminderScheduler {
    const val ACTION_REMIND = "com.example.focusflow.REMIND"
    const val KIND_MORNING = "morning"
    const val KIND_FOLLOWUP = "followup"
    const val KIND_REVIEW = "review"
    const val KIND_TASK = "task"
    const val KIND_RESUME = "resume"
    const val KIND_ROUTINE = "routine"
    const val KIND_TASKFOCUS = "taskfocus"
    const val KIND_ROUTINESYNC = "routinesync"

    private fun pi(ctx: Context, kind: String, taskId: Long, variant: String = ""): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_REMIND)
            .putExtra("kind", kind).putExtra("taskId", taskId)
        i.data = Uri.parse("focusflow://remind/$kind/$taskId$variant")
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun at(ctx: Context, kind: String, taskId: Long, whenMs: Long, variant: String = "") {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, kind, taskId, variant)
        val exact = ServiceLocator.settings(ctx).exactReminders &&
            (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, p)
        }
    }

    fun cancel(ctx: Context, kind: String, taskId: Long, variant: String = "") {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, kind, taskId, variant))
    }

    fun snooze(ctx: Context, kind: String, taskId: Long, minutes: Int) {
        at(ctx, kind, taskId, System.currentTimeMillis() + minutes * 60_000L, "/snooze")
    }

    /** Recomputes every alarm from the current tasks and settings. */
    suspend fun scheduleAll(ctx: Context) {
        val s = ServiceLocator.settings(ctx)
        // The spoken workflow keeps its own alarms; they are refreshed together with the reminders.
        try { VoiceScheduler.scheduleAll(ctx) } catch (e: Exception) { /* never let voice break the reminders */ }
        scheduleTaskFocus(ctx, s)
        // Catch up with the timetable (restart, missed alarm, edit), then arm the next routine boundary.
        try { RoutineFocusManager.reconcile(ctx) } catch (e: Exception) { /* never let focus break the reminders */ }
        scheduleBoundary(ctx, s)
        if (!s.remindersEnabled) {
            cancelAll(ctx)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        scheduleRoutine(ctx, s, now, zone)

        fun daily(kind: String, enabled: Boolean, minOfDay: Int) {
            if (enabled) at(ctx, kind, 0, ReminderRules.nextOccurrence(minOfDay, now).atZone(zone).toInstant().toEpochMilli())
            else cancel(ctx, kind, 0)
        }
        daily(KIND_MORNING, s.morningEnabled && !s.voiceEnabled, s.morningMin)
        daily(KIND_FOLLOWUP, s.followUpEnabled && !s.voiceEnabled, s.followUpMin)
        daily(KIND_REVIEW, s.reviewEnabled, s.reviewMin)

        // One alarm per task that has a start time in the next two weeks.
        val nowMs = System.currentTimeMillis()
        val horizon = nowMs + 14 * 24 * 3_600_000L
        val wanted = HashMap<Long, Long>()
        if (s.taskRemindersEnabled && !s.voiceEnabled) {
            AppDb.get(ctx).dao().getAll().filter { !it.done && it.startMin != null }.forEach { t ->
                ReminderRules.taskAlarmAt(t, s.taskLeadMin, zone)?.let { if (it in nowMs..horizon) wanted[t.id] = it }
            }
        }
        (s.scheduledTaskIds - wanted.keys).forEach { cancel(ctx, KIND_TASK, it) }
        wanted.forEach { (id, whenMs) -> at(ctx, KIND_TASK, id, whenMs) }
        s.scheduledTaskIds = wanted.keys
    }

    fun cancelAll(ctx: Context) {
        val s = ServiceLocator.settings(ctx)
        cancelRoutine(ctx)
        cancel(ctx, KIND_MORNING, 0)
        cancel(ctx, KIND_FOLLOWUP, 0)
        cancel(ctx, KIND_REVIEW, 0)
        s.scheduledTaskIds.forEach { cancel(ctx, KIND_TASK, it) }
        s.scheduledTaskIds = emptySet()
    }

    /** Id for a routine alarm: (block number + 1) * 1000 + minutes before the end (0 = at the end, 999 = at the start). */
    private fun routineId(idx: Int, lead: Int) = (idx + 1) * 1000L + lead

    private fun cancelRoutine(ctx: Context) {
        for (idx in 0 until 20) for (lead in listOf(30, 10, 0, 999)) cancel(ctx, KIND_ROUTINE, routineId(idx, lead))
    }

    /** Alarms 30 and 10 minutes before each routine ends, at its end, and (if chosen) at its start. */
    private fun scheduleRoutine(ctx: Context, s: com.example.focusflow.data.SettingsStore, now: LocalDateTime, zone: ZoneId) {
        cancelRoutine(ctx)
        com.example.focusflow.domain.Routine.decode(s.routine).forEachIndexed { idx, b ->
            if (idx >= 20) return@forEachIndexed
            fun ms(min: Int) = ReminderRules.nextOccurrence(((min % 1440) + 1440) % 1440, now).atZone(zone).toInstant().toEpochMilli()
            val len = ((b.end - b.start) + 1440) % 1440
            for (lead in listOf(30, 10, 0)) if (len > lead) at(ctx, KIND_ROUTINE, routineId(idx, lead), ms(b.end - lead))
        }
    }

    /**
     * One alarm, always the same one, for the next moment any routine starts or ends. When it fires, the focus
     * manager is reconciled and the next boundary is armed. A single fixed alarm cannot be duplicated or conflict
     * with itself, however many routines overlap.
     */
    private fun scheduleBoundary(ctx: Context, s: com.example.focusflow.data.SettingsStore) {
        val blocks = com.example.focusflow.domain.Routine.decode(s.routine)
        if (blocks.isEmpty()) { cancel(ctx, KIND_ROUTINESYNC, 0L); return }
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val today = java.time.LocalDate.now()
        var best = Long.MAX_VALUE
        blocks.forEach { b ->
            for (m0 in listOf(b.start, b.end)) {
                val m = ((m0 % 1440) + 1440) % 1440
                var t = today.atTime(m / 60, m % 60).atZone(zone).toInstant().toEpochMilli()
                if (t <= nowMs + 1000) t = today.plusDays(1).atTime(m / 60, m % 60).atZone(zone).toInstant().toEpochMilli()
                if (t < best) best = t
            }
        }
        at(ctx, KIND_ROUTINESYNC, 0L, best)
    }

    /** One alarm at the start of every listed task (today and tomorrow) so a focus session can start by itself. */
    private suspend fun scheduleTaskFocus(ctx: Context, s: com.example.focusflow.data.SettingsStore) {
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val wanted = HashMap<Long, Long>()
        if (s.autoFocusTasks) {
            val days = listOf(java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(1)).map { it.toString() }
            AppDb.get(ctx).dao().getAll()
                .filter { !it.done && it.startMin != null && it.date in days && it.status != com.example.focusflow.data.TaskStatus.DISCARDED }
                .forEach { t ->
                    val ms = java.time.LocalDate.parse(t.date).atStartOfDay(zone).plusMinutes(t.startMin!!.toLong()).toInstant().toEpochMilli()
                    if (ms > nowMs) wanted[t.id] = ms
                }
        }
        (s.focusAlarmIds - wanted.keys).forEach { cancel(ctx, KIND_TASKFOCUS, it) }
        wanted.forEach { (id, ms) -> at(ctx, KIND_TASKFOCUS, id, ms) }
        s.focusAlarmIds = wanted.keys
    }

    /** Fire-and-forget version for use from screens. */
    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch { scheduleAll(app) }
    }
}
