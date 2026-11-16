package com.example.focusflow.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.focusflow.data.AppDb
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.domain.VoicePlan
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sets one alarm for every upcoming voice event and keeps them in step with the tasks. Call [scheduleAll]
 * again after anything changes (it is cheap): alarms that are no longer needed are cancelled and moved ones updated.
 * Exact alarms are used when Android allows them, so speech starts on the minute.
 */
object VoiceScheduler {
    const val ACTION_EVENT = "com.example.focusflow.VOICE_EVENT"

    private fun pending(ctx: Context, key: String, kind: String = "", taskId: Long = 0L): PendingIntent {
        val i = Intent(ctx, VoiceReceiver::class.java).setAction(ACTION_EVENT)
            .putExtra("key", key).putExtra("kind", kind).putExtra("taskId", taskId)
        i.data = Uri.parse("focusflow://voice/$key")
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** True when Android lets the app set alarms that fire exactly on time. */
    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun set(ctx: Context, atMs: Long, p: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (canScheduleExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
        }
    }

    suspend fun scheduleAll(ctx: Context) {
        val s = ServiceLocator.settings(ctx)
        if (!s.voiceEnabled) {
            cancelAll(ctx)
            return
        }
        val tasks = AppDb.get(ctx).dao().getAll()
        val wanted = VoicePlan.events(
            tasks, LocalDate.now(), System.currentTimeMillis(), ZoneId.systemDefault(),
            s.dayStartMin, s.dayEndMin, s.briefingOffsetMin, s.incomingLeadMin, s.deadlineLeadMin, s.checkInEveryMin,
            com.example.focusflow.domain.Routine.decode(s.routine), s.taskCheckMin
        ).associateBy { it.key }
        val am = ctx.getSystemService(AlarmManager::class.java)
        (s.voiceAlarmKeys - wanted.keys).forEach { am.cancel(pending(ctx, it)) }
        wanted.values.forEach { e -> set(ctx, e.atMs, pending(ctx, e.key, e.kind.name, e.taskId)) }
        s.voiceAlarmKeys = wanted.keys
    }

    fun cancelAll(ctx: Context) {
        val s = ServiceLocator.settings(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        s.voiceAlarmKeys.forEach { am.cancel(pending(ctx, it)) }
        s.voiceAlarmKeys = emptySet()
    }
}
