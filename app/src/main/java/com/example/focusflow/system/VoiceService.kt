package com.example.focusflow.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Speaks a few sentences and stops itself. It runs as a short foreground service so Android keeps the app
 * alive long enough for the speech engine to start and finish, even when the screen is off.
 */
class VoiceService : Service() {
    companion object {
        private const val EXTRA_TEXTS = "texts"
        private const val NOTIFICATION_ID = 7001

        /** Starts the service. Returns false if Android refused to start it from the background. */
        fun speak(ctx: Context, texts: List<String>): Boolean {
            if (texts.isEmpty()) return true
            val i = Intent(ctx, VoiceService::class.java).putStringArrayListExtra(EXTRA_TEXTS, ArrayList(texts))
            return try {
                ContextCompat.startForegroundService(ctx, i)
                true
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Speaks through the service; if Android won't allow that right now, speaks directly for up to nine
         * seconds instead, so a reminder is not lost.
         */
        suspend fun speakReliably(ctx: Context, texts: List<String>) {
            if (texts.isEmpty() || speak(ctx, texts)) return
            val done = CompletableDeferred<Unit>()
            var speaker: Speaker? = null
            withContext(Dispatchers.Main) {
                speaker = Speaker(ctx) { done.complete(Unit) }.also { it.speak(texts) }
            }
            withTimeoutOrNull(9_000) { done.await() }
            withContext(Dispatchers.Main) { speaker?.shutdown() }
        }
    }

    private var speaker: Speaker? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        val n = NotificationCompat.Builder(this, Notifications.CH_VOICE_SVC)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("AMU")
            .setContentText("Speaking a reminder")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        val texts = i?.getStringArrayListExtra(EXTRA_TEXTS)
        if (texts.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val s = speaker ?: Speaker(this) {
            speaker = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }.also { speaker = it }
        s.speak(texts)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }
}
