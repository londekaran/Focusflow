package com.example.focusflow.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.example.focusflow.MainActivity

object Notifications {
    const val CH_FOCUS = "focus"
    const val CH_ALERT = "alert"
    const val CH_REMIND = "reminders"
    const val CH_BREAK = "breaks"
    const val CH_VOICE = "voice"
    const val CH_VOICE_SVC = "voice_service"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_FOCUS, "Focus session", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Alerts", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_BREAK, "Breaks", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_VOICE, "Voice assistant", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_VOICE_SVC, "Speaking", NotificationManager.IMPORTANCE_LOW))
    }

    /** Opens the app; [route] says which screen to show (tasks, plan, focus, review). */
    fun openApp(ctx: Context, route: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("route", route)
        i.data = Uri.parse("focusflow://open/${route ?: "home"}")
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** A notification button that is handled by [ReminderReceiver] (snooze, mark done). */
    fun action(ctx: Context, action: String, kind: String, taskId: Long, nid: Int, minutes: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(action)
            .putExtra("kind", kind).putExtra("taskId", taskId).putExtra("nid", nid).putExtra("minutes", minutes)
        i.data = Uri.parse("focusflow://act/$action/$kind/$taskId")
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun post(
        ctx: Context, id: Int, channel: String, title: String, text: String,
        tap: PendingIntent?, actions: List<Pair<String, PendingIntent>> = emptyList()
    ) {
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        if (tap != null) b.setContentIntent(tap)
        actions.forEach { (label, pi) -> b.addAction(0, label, pi) }
        ctx.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }
}
