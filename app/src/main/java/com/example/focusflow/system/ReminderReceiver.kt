package com.example.focusflow.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.focusflow.data.AppDb
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.data.SettingsStore
import com.example.focusflow.domain.Msg
import com.example.focusflow.domain.ReminderRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Receives alarms, notification buttons (snooze, done) and the phone-restarted signal. */
class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SNOOZE = "com.example.focusflow.SNOOZE"
        const val ACTION_DONE = "com.example.focusflow.DONE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(app, intent)
            } catch (e: Exception) {
                // A failed reminder must never crash the app.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(ctx: Context, intent: Intent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val kind = intent.getStringExtra("kind") ?: ""
        val taskId = intent.getLongExtra("taskId", 0L)
        val nid = intent.getIntExtra("nid", -1)
        when (intent.action) {
            ReminderScheduler.ACTION_REMIND -> {
                when (kind) {
                    ReminderScheduler.KIND_TASKFOCUS -> autoFocus(ctx, taskId)
                    ReminderScheduler.KIND_ROUTINESYNC -> Unit   // scheduleAll below reconciles focus with the timetable
                    else -> fire(ctx, kind, taskId)
                }
                ReminderScheduler.scheduleAll(ctx)
            }
            ACTION_SNOOZE -> {
                ReminderScheduler.snooze(ctx, kind, taskId, intent.getIntExtra("minutes", 10))
                if (nid >= 0) nm.cancel(nid)
            }
            ACTION_DONE -> {
                val dao = AppDb.get(ctx).dao()
                dao.getById(taskId)?.let {
                    val now = System.currentTimeMillis()
                    dao.update(it.copy(done = true, completedAt = now, updatedAt = now))
                }
                if (nid >= 0) nm.cancel(nid)
                ReminderScheduler.scheduleAll(ctx)
            }
            else -> ReminderScheduler.scheduleAll(ctx)   // e.g. the phone was restarted
        }
    }

    /** A listed task's time has come: start a focus session for the rest of its slot. No task, no session. */
    private suspend fun autoFocus(ctx: Context, taskId: Long) {
        val s = ServiceLocator.settings(ctx)
        val t = AppDb.get(ctx).dao().getById(taskId) ?: return
        val start = t.startMin ?: return
        if (!s.autoFocusTasks || t.done || t.status == com.example.focusflow.data.TaskStatus.DISCARDED || t.date != LocalDate.now().toString()) return
        val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        val left = start + (t.estimateMin ?: 30) - nowMin
        if (left <= 0 || com.example.focusflow.FocusState.running.value) return
        val blocks = com.example.focusflow.domain.Routine.decode(s.routine)
        val owner = blocks.firstOrNull { it.name == t.category }
        val cur = com.example.focusflow.domain.Routine.at(blocks, nowMin)
        if (owner != null && !owner.focusOn) return          // this routine has focus switched off
        if (cur != null && !cur.focusOn) return              // so does the routine running right now (e.g. Leisure)
        if (cur != null && cur.autoFocus && cur.focusOn) return   // whole-routine focus already covers this task
        if (!RoutineFocus.startTask(ctx, t.id, left, owner?.name)) {
            Notifications.ensureChannels(ctx)
            Notifications.post(ctx, 3000 + (taskId % 1000).toInt(), Notifications.CH_REMIND, "Time to focus: ${t.title}", "Tap to start your focus session.", Notifications.openApp(ctx, "focus"))
        }
    }

    private suspend fun fire(ctx: Context, kind: String, taskId: Long) {
        val db = AppDb.get(ctx)
        val s = ServiceLocator.settings(ctx)
        if (!s.remindersEnabled) return
        val today = LocalDate.now()
        val tasks = db.dao().getAll()
        Notifications.ensureChannels(ctx)
        when (kind) {
            ReminderScheduler.KIND_MORNING ->
                if (s.morningEnabled) ReminderRules.morning(tasks, today)?.let { send(ctx, s, today, 100, it, "tasks", kind, 0L, 60) }
            ReminderScheduler.KIND_FOLLOWUP ->
                if (s.followUpEnabled) ReminderRules.followUp(tasks, today)?.let { send(ctx, s, today, 101, it, "tasks", kind, 0L, 60) }
            ReminderScheduler.KIND_REVIEW ->
                if (s.reviewEnabled) {
                    val msg = ReminderRules.review(tasks, db.sessionDao().getAll(), today)
                    send(ctx, s, today, 102, msg, "review", kind, 0L, 60)
                }
            ReminderScheduler.KIND_TASK -> {
                val t = db.dao().getById(taskId)?.takeIf { !it.done && it.date == today.toString() } ?: return
                val at = t.startMin?.let { "%02d:%02d".format(it / 60, it % 60) } ?: ""
                send(ctx, s, today, 1000 + (taskId % 5000).toInt(), Msg("Starting soon: ${t.title}", "Planned for $at."), "tasks", kind, taskId, s.taskLeadMin.coerceAtLeast(5), withDone = true)
            }
            ReminderScheduler.KIND_ROUTINE -> {
                val idx = (taskId / 1000 - 1).toInt(); val lead = (taskId % 1000).toInt()
                val b = com.example.focusflow.domain.Routine.decode(s.routine).getOrNull(idx) ?: return
                val nid = 2000 + idx * 10 + (lead % 10)
                // A routine that ends after midnight ends on the day after its tasks were planned.
                val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
                val planned = if (b.start > b.end && nowMin < b.start) today.minusDays(1) else today
                val open = tasks.filter { it.category == b.name && !it.done && it.date == planned.toString() && it.status != com.example.focusflow.data.TaskStatus.DISCARDED }
                if (open.isEmpty()) return
                val names = open.take(3).joinToString(", ") { it.title } + if (open.size > 3) " and ${open.size - 3} more" else ""
                if (lead > 0) {
                    Notifications.post(ctx, nid, Notifications.CH_REMIND, "${b.name} ends in $lead min",
                        "${open.size} task${if (open.size == 1) "" else "s"} not finished: $names", Notifications.openApp(ctx, "plan"))
                } else {
                    // The routine is over: move what is left to tomorrow, as the first task of this routine.
                    // Line them up one after another from the routine's start, most important first.
                    var cursor = b.start
                    open.sortedWith(compareBy({ -it.priority }, { it.startMin ?: Int.MAX_VALUE }, { it.id })).forEach { t ->
                        ServiceLocator.repository(ctx).reschedule(listOf(t), planned.plusDays(1), cursor, true)
                        cursor += t.estimateMin ?: 30
                    }
                    open.forEach { t -> db.dao().getById(t.id)?.let { db.dao().update(it.copy(status = com.example.focusflow.data.TaskStatus.PENDING)) } }
                    Notifications.post(ctx, nid, Notifications.CH_REMIND, "Moved to tomorrow",
                        "${open.size} unfinished ${b.name} task${if (open.size == 1) "" else "s"} will be first in the routine tomorrow: $names", Notifications.openApp(ctx, "plan"))
                }
            }
            ReminderScheduler.KIND_RESUME -> {
                val t = db.dao().getById(taskId)?.takeIf { !it.done } ?: return
                send(ctx, s, today, 103, Msg("Back to it?", "Ready to get back to \"${t.title}\"?"), "focus", kind, taskId, s.resumeDelayMin)
            }
        }
    }

    private fun send(
        ctx: Context, s: SettingsStore, today: LocalDate, id: Int, msg: Msg, route: String,
        kind: String, taskId: Long, snoozeMin: Int, withDone: Boolean = false
    ) {
        if (!s.tryConsumeBudget(today.toString())) return   // daily limit reached
        val actions = ArrayList<Pair<String, android.app.PendingIntent>>()
        actions.add("Snooze $snoozeMin min" to Notifications.action(ctx, ACTION_SNOOZE, kind, taskId, id, snoozeMin))
        if (withDone) actions.add("Done" to Notifications.action(ctx, ACTION_DONE, kind, taskId, id, 0))
        Notifications.post(ctx, id, Notifications.CH_REMIND, msg.title, msg.text, Notifications.openApp(ctx, route), actions)
    }
}
