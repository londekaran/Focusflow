package com.example.focusflow

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.focusflow.data.AppDb
import com.example.focusflow.data.BreakLog
import com.example.focusflow.data.FocusSession
import com.example.focusflow.data.Interruption
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.data.SettingsStore
import com.example.focusflow.data.progressSummary
import com.example.focusflow.domain.BreakRules
import com.example.focusflow.domain.TimeRange
import com.example.focusflow.domain.UsageAnalyzer
import com.example.focusflow.domain.UsageEvt
import com.example.focusflow.system.BlockOverlay
import com.example.focusflow.system.Notifications
import com.example.focusflow.system.ReminderScheduler
import com.example.focusflow.system.RoutineFocus
import com.example.focusflow.system.RoutineFocusManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Observed by the Compose UI. */
object FocusState {
    val running = MutableStateFlow(false)
    val paused = MutableStateFlow(false)
    val remainingSec = MutableStateFlow(0)
    val totalSec = MutableStateFlow(0)
    val taskId = MutableStateFlow<Long?>(null)
    /** Live estimate during a session; replaced by the exact count from the usage log when the session ends. */
    val distractions = MutableStateFlow(0)
    val lastApp = MutableStateFlow<String?>(null)
    /** Who started the running session: "manual", "task" or "routine" ("none" when idle). */
    val origin = MutableStateFlow("none")
    /** The routine an automatic session belongs to. */
    val blockName = MutableStateFlow<String?>(null)
    /** When the session was planned to end (epoch ms), used to notice a schedule edit. */
    val plannedEndMs = MutableStateFlow(0L)

    val breakRunning = MutableStateFlow(false)
    val breakRemainingSec = MutableStateFlow(0)
    val breakTotalSec = MutableStateFlow(0)
    val breakTip = MutableStateFlow("")
}

class FocusService : Service() {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_PROGRESS = "progress"
        const val ACTION_BREAK_START = "break_start"
        const val ACTION_BREAK_END = "break_end"
        private const val FG_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickJob: Job? = null
    private var monitorJob: Job? = null
    private var breakJob: Job? = null
    @Volatile private var activeMs = 0L
    @Volatile private var sessionId = 0L
    @Volatile private var breakLogId = 0L
    private var startedAt = 0L
    private var plannedMin = 0
    @Volatile private var finishing = false
    /** A wall-clock moment (epoch ms) at which the session must end even if paused, e.g. a routine's end. 0 = none. */
    @Volatile private var hardEndMs = 0L
    private var lastAlertAt = 0L
    private var breakSuggested = 0
    private var lastPkg: String? = null
    // Which app is on screen, rebuilt from the usage events since the last check.
    private var fgPkg: String? = null
    private var eventCursor = 0L
    // Full-screen warning drawn over a distracting app.
    private val blocker by lazy { BlockOverlay(this) { finish("ended") } }

    // Distraction tracking for the running session
    @Volatile private var monitorState = "off"
    @Volatile private var watched: Set<String> = emptySet()
    @Volatile private var allApps: Set<String> = emptySet()
    private val everWatched = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var labels: Map<String, String> = emptyMap()
    private val pauses = ArrayList<TimeRange>()
    private var pauseStart = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        when (i?.action) {
            ACTION_START -> startSession(
                i.getIntExtra("minutes", 25), i.getLongExtra("taskId", -1L), i.getStringExtra("origin") ?: "manual",
                i.getStringExtra("block"), i.getLongExtra("endAt", 0L)
            )
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            ACTION_STOP -> finish(i.getStringExtra("reason") ?: "ended")
            ACTION_BREAK_START -> startBreak(i.getIntExtra("minutes", 5))
            ACTION_BREAK_END -> finishBreak(false)
            ACTION_PROGRESS -> scope.launch {
                val s = AppDb.get(this@FocusService).dao().progressSummary()
                notify(3, Notifications.CH_ALERT, "Task progress", s)
            }
            else -> if (!FocusState.running.value && !FocusState.breakRunning.value) stopSelf()
        }
        return START_NOT_STICKY
    }

    /* ------------------------------ focus session ------------------------------ */

    private fun startSession(minutes: Int, task: Long, origin: String, block: String?, endAt: Long) {
        if (FocusState.running.value) {
            // A session is already running: just make sure the service stays in the foreground.
            ServiceCompat.startForeground(this, 1, ongoing(), FG_TYPE)
            return
        }
        if (FocusState.breakRunning.value) endBreakState()
        // Starting a session cancels any "get back to it" reminder.
        ReminderScheduler.cancel(this, ReminderScheduler.KIND_RESUME, 0)
        task.takeIf { it > 0 }?.let { ReminderScheduler.cancel(this, ReminderScheduler.KIND_RESUME, it) }

        finishing = false
        hardEndMs = endAt
        plannedMin = minutes
        activeMs = 0L
        sessionId = 0L
        startedAt = System.currentTimeMillis()
        lastPkg = null
        fgPkg = packageName   // sessions are started from inside AMU
        eventCursor = startedAt
        lastAlertAt = 0L
        breakSuggested = 0
        monitorState = "off"
        watched = emptySet()
        allApps = emptySet()
        everWatched.clear()
        labels = emptyMap()
        pauses.clear()
        FocusState.totalSec.value = minutes * 60
        FocusState.remainingSec.value = minutes * 60
        FocusState.paused.value = false
        FocusState.distractions.value = 0
        FocusState.taskId.value = if (task > 0) task else null
        FocusState.origin.value = origin
        FocusState.blockName.value = block
        FocusState.plannedEndMs.value = if (endAt > 0L) endAt else startedAt + minutes * 60_000L

        ServiceCompat.startForeground(this, 1, ongoing(), FG_TYPE)
        FocusState.running.value = true

        tickJob = scope.launch {
            val db = AppDb.get(this@FocusService)
            val settings = ServiceLocator.settings(this@FocusService)
            com.example.focusflow.system.DefaultBlockList.seedOnce(this@FocusService)
            val apps = db.distractionDao().getApps()
            allApps = apps.map { it.packageName }.toSet()
            refreshWatched()
            labels = apps.associate { it.packageName to it.label }
            monitorState = when {
                !settings.monitoringEnabled -> "off"
                !hasUsageAccess(this@FocusService) -> "no_access"
                apps.isEmpty() -> "no_apps"
                else -> "on"
            }
            sessionId = db.sessionDao().insert(
                FocusSession(
                    taskId = FocusState.taskId.value, startedAt = startedAt,
                    plannedMin = plannedMin, monitorState = monitorState
                )
            )

            if (monitorState == "on" && (settings.liveAlerts || settings.blockApps)) {
                monitorJob = launch {
                    while (isActive) {
                        refreshWatched()   // the routine may have changed since the last second
                        // Nothing to watch (blocking off for this routine, or paused): do not poll or cover anything.
                        if (!FocusState.paused.value && watched.isNotEmpty()) checkForeground() else blocker.hide()
                        delay(1000)
                    }
                }
            }

            var last = SystemClock.elapsedRealtime()
            var ticks = 0
            while (isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                if (!FocusState.paused.value) activeMs += now - last
                last = now
                val remaining = (FocusState.totalSec.value - activeMs / 1000).toInt().coerceAtLeast(0)
                FocusState.remainingSec.value = remaining
                if (remaining == 0) { finish("completed"); break }
                // The routine this session belongs to has ended (paused time does not stretch it).
                if (hardEndMs > 0L && System.currentTimeMillis() >= hardEndMs) { finish("routine_end"); break }
                ticks++
                // Long sessions (over an hour): suggest a short break every 50 minutes of focus.
                val n = (activeMs / 1000).toInt() / (50 * 60)
                if (FocusState.totalSec.value > 3600 && n > breakSuggested && remaining > 5 * 60) {
                    breakSuggested = n
                    notify(5, Notifications.CH_ALERT, "Take a short break?", "You've focused ${n * 50} minutes. Stand up, stretch and drink some water for 5 minutes, then carry on.")
                }
                // Save progress regularly so little is lost if the app is killed.
                if (ticks % 15 == 0) db.sessionDao().saveProgress(sessionId, (activeMs / 1000).toInt())
            }
        }
    }

    /**
     * Which apps are blocked right now.
     *
     * A session that belongs to a routine (started for that routine, or for one of its tasks) uses that routine's
     * own settings: the distracting apps minus the apps the routine allows, and nothing at all when focus or
     * blocking is off for it. Earlier this always looked at whichever routine the clock said was running, so a
     * session started for routine A while routine B was running (or while no routine was) was judged by B's rules
     * and could end up blocking nothing. Only sessions with no routine (a plain start from the Focus tab) still
     * follow the clock, and those always block, as before.
     */
    private fun refreshWatched() {
        val blocks = com.example.focusflow.domain.Routine.decode(ServiceLocator.settings(this).routine)
        val owner = FocusState.blockName.value?.let { n -> blocks.firstOrNull { it.name == n } }
        val w = if (owner != null) {
            com.example.focusflow.domain.Routine.watchedFor(owner, allApps)
        } else {
            val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            com.example.focusflow.domain.Routine.watchedNow(
                blocks, nowMin, allApps, manualSession = FocusState.origin.value == RoutineFocus.ORIGIN_MANUAL
            )
        }
        watched = w
        everWatched.addAll(w)
    }

    private fun setPaused(p: Boolean) {
        if (!FocusState.running.value || FocusState.paused.value == p) return
        val now = System.currentTimeMillis()
        if (p) pauseStart = now else pauses.add(TimeRange(pauseStart, now))
        FocusState.paused.value = p
        getSystemService(NotificationManager::class.java).notify(1, ongoing())
    }

    private fun finish(reason: String) {
        if (!FocusState.running.value || finishing) {
            if (!finishing && !FocusState.breakRunning.value) stopSelf()
            return
        }
        finishing = true
        watched = emptySet()   // a check that is already running can no longer cover an app
        tickJob?.cancel()
        monitorJob?.cancel()
        blocker.hide()
        val endedAt = System.currentTimeMillis()
        if (FocusState.paused.value) pauses.add(TimeRange(pauseStart, endedAt))
        // A finished session counts exactly its planned time; an early end counts the time actually focused.
        val activeSec = if (reason == "completed") plannedMin * 60 else (activeMs / 1000).toInt()
        val id = sessionId
        val started = startedAt
        val monitor = monitorState
        val watchedNow = everWatched.toSet()
        val labelsNow = labels
        val pausesNow = pauses.toList()
        val task = FocusState.taskId.value
        val origin = FocusState.origin.value
        val blockN = FocusState.blockName.value
        FocusState.running.value = false
        FocusState.paused.value = false
        FocusState.remainingSec.value = 0
        FocusState.totalSec.value = 0
        FocusState.taskId.value = null
        FocusState.origin.value = "none"
        FocusState.blockName.value = null
        FocusState.plannedEndMs.value = 0L

        val settings = ServiceLocator.settings(this)
        // Ended by hand during an automatic routine session: do not restart it for this occurrence of the routine.
        // (A session the person started for a routine, without a task, counts the same way.)
        if (reason == "ended" && blockN != null &&
            (origin == RoutineFocus.ORIGIN_ROUTINE || (origin == RoutineFocus.ORIGIN_MANUAL && task == null))
        ) RoutineFocusManager.suppress(this, blockN)
        if (reason == "completed") notifyCompleted(settings)
        if (reason == "ended" && task != null && settings.resumeEnabled && settings.remindersEnabled) {
            ReminderScheduler.at(
                this, ReminderScheduler.KIND_RESUME, task,
                System.currentTimeMillis() + settings.resumeDelayMin * 60_000L
            )
        }
        scope.launch {
            val db = AppDb.get(this@FocusService)
            if (id != 0L) {
                db.sessionDao().close(id, endedAt, activeSec, reason)
                if (monitor == "on") saveReport(db, id, started, endedAt, watchedNow, labelsNow, pausesNow)
            }
            withContext(Dispatchers.Main) {
                if (!FocusState.breakRunning.value && !FocusState.running.value) {   // a new session may already have started
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun notifyCompleted(s: SettingsStore) {
        val nm = getSystemService(NotificationManager::class.java)
        val b = NotificationCompat.Builder(this, Notifications.CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(Notifications.openApp(this, "focus"))
        if (s.breakEnabled) {
            val tip = BreakRules.tip((System.currentTimeMillis() / 1000).toInt())
            val text = "Time for a ${s.breakMin}-minute break. $tip"
            val start = PendingIntent.getForegroundService(
                this, 20,
                Intent(this, FocusService::class.java).setAction(ACTION_BREAK_START).putExtra("minutes", s.breakMin),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.setContentTitle("Session complete").setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .addAction(0, "Start break", start)
        } else {
            b.setContentTitle("Focus session complete").setContentText("Nice work. Open the app to see your session report.")
        }
        nm.notify(2, b.build())
    }

    /** Reads Android's usage log for the session window and stores the interruptions it finds. */
    private suspend fun saveReport(
        db: AppDb, id: Long, from: Long, to: Long,
        watched: Set<String>, labels: Map<String, String>, pauses: List<TimeRange>
    ) {
        val spans = UsageAnalyzer.significant(
            UsageAnalyzer.subtractPauses(
                UsageAnalyzer.foregroundSpans(readEvents(from, to), from, to, watched), pauses
            )
        )
        val items = spans.map {
            Interruption(
                sessionId = id, packageName = it.pkg, label = labels[it.pkg] ?: it.pkg,
                startedAt = it.start, durationSec = (it.ms / 1000).toInt()
            )
        }
        if (items.isNotEmpty()) db.distractionDao().addInterruptions(items)
        if (!FocusState.running.value) FocusState.distractions.value = items.size
    }

    private fun readEvents(from: Long, to: Long): List<UsageEvt> = try {
        val usm = getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        val out = ArrayList<UsageEvt>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> out.add(UsageEvt(e.packageName, true, e.timeStamp))
                UsageEvents.Event.ACTIVITY_PAUSED -> out.add(UsageEvt(e.packageName, false, e.timeStamp))
            }
        }
        out
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * The app on screen right now, or null. Replays the usage events since the last check, so the answer
     * stays right even when the app has been sitting there for minutes (not just when it just opened).
     */
    private fun pollForeground(): String? {
        try {
            val usm = getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(eventCursor, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> fgPkg = e.packageName
                    UsageEvents.Event.ACTIVITY_PAUSED -> if (e.packageName == fgPkg) fgPkg = null
                }
            }
            // Look back one second next time: Android can write usage events a moment late.
            eventCursor = now - 1000
        } catch (e: Exception) {
            // Usage access was taken away: keep the last known answer.
        }
        return fgPkg
    }

    /**
     * Runs every second during a session. When a chosen app is on screen it is covered by a full-screen
     * warning (if that is switched on and allowed), otherwise the person gets a notification instead.
     */
    private fun checkForeground() {
        if (finishing) return
        val latest = pollForeground()
        val changed = latest != lastPkg
        if (changed) {
            lastPkg = latest
            if (latest != null) FocusState.lastApp.value = latest
        }
        if (latest == null || latest !in watched) {
            blocker.hide()
            return
        }

        val cfg = ServiceLocator.settings(this)
        val name = labels[latest] ?: "A distracting app"
        val canBlock = cfg.blockApps && BlockOverlay.canShow(this)
        val now = System.currentTimeMillis()

        // Count each visit once (at most one per 30 seconds), as before.
        if (changed && now - lastAlertAt > 30_000) {
            lastAlertAt = now
            FocusState.distractions.value += 1
            if (!canBlock && cfg.liveAlerts) {
                notify(4, Notifications.CH_ALERT, "Stay focused!", "You opened $name during your focus session.")
            }
        }
        if (canBlock && !finishing && FocusState.running.value) {
            val left = FocusState.remainingSec.value
            blocker.show(name, "%02d:%02d left".format(left / 60, left % 60))
        }
    }

    /* ----------------------------------- break ----------------------------------- */

    private fun startBreak(minutes: Int) {
        if (FocusState.running.value) {
            ServiceCompat.startForeground(this, 1, ongoing(), FG_TYPE)
            return
        }
        if (FocusState.breakRunning.value) {
            ServiceCompat.startForeground(this, 1, breakNotification(), FG_TYPE)
            return
        }
        getSystemService(NotificationManager::class.java).cancel(2)
        val tip = BreakRules.tip((System.currentTimeMillis() / 1000).toInt())
        FocusState.breakTip.value = tip
        FocusState.breakTotalSec.value = minutes * 60
        FocusState.breakRemainingSec.value = minutes * 60
        FocusState.breakRunning.value = true
        ServiceCompat.startForeground(this, 1, breakNotification(), FG_TYPE)
        breakJob = scope.launch {
            val dao = AppDb.get(this@FocusService).breakDao()
            breakLogId = dao.insert(BreakLog(startedAt = System.currentTimeMillis(), plannedMin = minutes, suggestion = tip))
            val end = SystemClock.elapsedRealtime() + minutes * 60_000L
            while (isActive) {
                delay(1000)
                val left = ((end - SystemClock.elapsedRealtime()) / 1000).toInt().coerceAtLeast(0)
                FocusState.breakRemainingSec.value = left
                if (left == 0) { finishBreak(true); break }
            }
        }
    }

    /** Clears the break state and records its end. Does not stop the service. */
    private fun endBreakState() {
        breakJob?.cancel()
        val id = breakLogId
        breakLogId = 0L
        FocusState.breakRunning.value = false
        FocusState.breakRemainingSec.value = 0
        FocusState.breakTotalSec.value = 0
        if (id != 0L) {
            val now = System.currentTimeMillis()
            scope.launch { AppDb.get(this@FocusService).breakDao().close(id, now) }
        }
    }

    private fun finishBreak(natural: Boolean) {
        if (!FocusState.breakRunning.value) {
            if (!FocusState.running.value) stopSelf()
            return
        }
        endBreakState()
        if (natural) {
            Notifications.post(
                this, 5, Notifications.CH_BREAK, "Break over",
                "Ready for the next focus session?", Notifications.openApp(this, "focus")
            )
        }
        scope.launch {
            withContext(Dispatchers.Main) {
                if (!FocusState.running.value) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    /* -------------------------------- notifications -------------------------------- */

    private fun pi(action: String, code: Int) = PendingIntent.getService(
        this, code,
        Intent(this, FocusService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun ongoing(): Notification {
        val paused = FocusState.paused.value
        val remaining = FocusState.remainingSec.value
        val b = NotificationCompat.Builder(this, Notifications.CH_FOCUS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("AMU")
            .setContentIntent(Notifications.openApp(this, "focus"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (paused) {
            b.setContentText("Paused · %02d:%02d left".format(remaining / 60, remaining % 60))
        } else {
            b.setContentText(FocusState.blockName.value?.let { "$it focus running" } ?: "Focus session running")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() + remaining * 1000L)
        }
        b.addAction(0, if (paused) "Resume" else "Pause", pi(if (paused) ACTION_RESUME else ACTION_PAUSE, 12))
        b.addAction(0, "End", pi(ACTION_STOP, 11))
        b.addAction(0, "Progress", pi(ACTION_PROGRESS, 10))
        return b.build()
    }

    private fun breakNotification(): Notification =
        NotificationCompat.Builder(this, Notifications.CH_FOCUS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Break")
            .setContentText(FocusState.breakTip.value)
            .setContentIntent(Notifications.openApp(this, "focus"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis() + FocusState.breakRemainingSec.value * 1000L)
            .addAction(0, "End break", pi(ACTION_BREAK_END, 13))
            .build()

    private fun notify(id: Int, ch: String, title: String, text: String) {
        getSystemService(NotificationManager::class.java).notify(
            id,
            NotificationCompat.Builder(this, ch).setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title).setContentText(text).setAutoCancel(true)
                .setContentIntent(Notifications.openApp(this, "focus"))
                .build()
        )
    }

    override fun onDestroy() {
        blocker.hide()
        scope.cancel()
        super.onDestroy()
    }
}
