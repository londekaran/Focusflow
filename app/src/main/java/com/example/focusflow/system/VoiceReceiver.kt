package com.example.focusflow.system

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.focusflow.data.AppDb
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.domain.VoiceAction
import com.example.focusflow.domain.VoiceKind
import com.example.focusflow.domain.VoiceMessages
import com.example.focusflow.domain.VoiceOp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Receives the voice alarms (speak and notify) and the answers tapped on those notifications. */
class VoiceReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STATUS = "com.example.focusflow.VOICE_STATUS"

        private fun notificationId(kind: VoiceKind, taskId: Long): Int = when (kind) {
            VoiceKind.MORNING -> 4001
            VoiceKind.WORK_START -> 4002
            VoiceKind.CHECKIN -> 4003
            VoiceKind.INCOMING -> 5000 + (taskId % 1000).toInt()
            VoiceKind.DEADLINE -> 6000 + (taskId % 1000).toInt()
            VoiceKind.ROUTINE_START -> 4010 + (taskId % 10).toInt()
            VoiceKind.TASK_CHECK -> 7000 + (taskId % 1000).toInt()
        }

        /** Removes any voice notification still showing for this task. */
        fun clearFor(ctx: Context, taskId: Long) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.cancel(notificationId(VoiceKind.INCOMING, taskId))
            nm.cancel(notificationId(VoiceKind.DEADLINE, taskId))
            nm.cancel(notificationId(VoiceKind.TASK_CHECK, taskId))
            nm.cancel(notificationId(VoiceKind.CHECKIN, 0))
        }

        private fun answer(ctx: Context, a: VoiceAction, taskId: Long, nid: Int): PendingIntent {
            val i = Intent(ctx, VoiceReceiver::class.java).setAction(ACTION_STATUS)
                .putExtra("op", a.op.name).putExtra("taskId", taskId).putExtra("minutes", a.minutes).putExtra("nid", nid)
            i.data = Uri.parse("focusflow://voice-answer/${a.op.name}/${a.minutes}/$taskId")
            return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    VoiceScheduler.ACTION_EVENT -> onEvent(app, intent)
                    ACTION_STATUS -> onAnswer(app, intent)
                }
            } catch (e: Exception) {
                // A failed reminder must never crash the app.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun onEvent(ctx: Context, intent: Intent) {
        val kind = runCatching { VoiceKind.valueOf(intent.getStringExtra("kind") ?: "") }.getOrNull() ?: return
        val taskId = intent.getLongExtra("taskId", 0L)
        val s = ServiceLocator.settings(ctx)
        if (!s.voiceEnabled) return

        // At the briefing and at the start of work, tasks without a time are placed into the day first.
        if ((kind == VoiceKind.MORNING || kind == VoiceKind.WORK_START || kind == VoiceKind.ROUTINE_START) && s.autoSchedule) {
            VoiceActions.autoScheduleToday(ctx)
        }
        val tasks = AppDb.get(ctx).dao().getAll()
        val now = LocalTime.now()
        val content = VoiceMessages.build(kind, taskId, tasks, LocalDate.now(), now.hour * 60 + now.minute, com.example.focusflow.domain.Routine.decode(s.routine))
        if (content != null) {
            Notifications.ensureChannels(ctx)
            val nid = notificationId(kind, taskId)
            Notifications.post(
                ctx, nid, Notifications.CH_VOICE, content.title, content.text,
                Notifications.openApp(ctx, content.route),
                content.actions.map { it.label to answer(ctx, it, taskId, nid) }
            )
            if (s.voiceSpeak) VoiceService.speakReliably(ctx, content.speech)
        }
        ReminderScheduler.scheduleAll(ctx)   // also plans the next days
    }

    private suspend fun onAnswer(ctx: Context, intent: Intent) {
        val op = runCatching { VoiceOp.valueOf(intent.getStringExtra("op") ?: "") }.getOrNull() ?: return
        val taskId = intent.getLongExtra("taskId", 0L)
        val nid = intent.getIntExtra("nid", -1)
        val message = VoiceActions.apply(ctx, op, taskId, intent.getIntExtra("minutes", 0))
        if (nid >= 0) ctx.getSystemService(NotificationManager::class.java).cancel(nid)
        if (message != null && ServiceLocator.settings(ctx).voiceSpeak) VoiceService.speakReliably(ctx, listOf(message))
    }
}
