package com.example.focusflow.system

import android.content.Context
import android.content.Intent
import com.example.focusflow.FocusService
import com.example.focusflow.FocusState
import com.example.focusflow.domain.DayBlock
import com.example.focusflow.domain.Routine
import java.time.LocalTime

/** What happened when the person asked to start focus for a routine by hand. */
enum class ManualStart { STARTED, ALREADY_RUNNING, FOCUS_OFF, NO_DURATION, NOT_ALLOWED }

/**
 * The one place that asks [FocusService] to start or stop a session for a routine or task. It never starts a
 * second session (the service also ignores a start while one is running) and drops repeated requests that arrive
 * within a few seconds of each other, so overlapping alarms and repeated taps cannot create duplicates.
 *
 * Every start that belongs to a routine passes the routine's name ("block"). The service then looks that routine
 * up and uses its own blocking settings, instead of guessing from the clock.
 */
object RoutineFocus {
    const val ORIGIN_MANUAL = "manual"
    const val ORIGIN_TASK = "task"
    const val ORIGIN_ROUTINE = "routine"

    private var lastLaunchAt = 0L

    private enum class Launch { STARTED, SKIPPED, FAILED }

    /** Whole minutes (rounded up) from now until [b] ends. */
    fun minutesUntilEnd(b: DayBlock, nowSec: Int = LocalTime.now().toSecondOfDay()): Int {
        val leftSec = ((b.end * 60 - nowSec) + 86400) % 86400
        return Math.ceil(leftSec / 60.0).toInt().coerceAtLeast(1)
    }

    /**
     * [force] is used right after a stop request: the stop is processed before this start, in order.
     * [endAtMs] (0 = none) is a hard deadline: the service ends the session then, even if it was paused.
     */
    @Synchronized
    private fun launch(
        ctx: Context, minutes: Int, taskId: Long, origin: String, block: String?, force: Boolean, endAtMs: Long = 0L
    ): Launch {
        val now = System.currentTimeMillis()
        if (!force) {
            if (FocusState.running.value) return Launch.SKIPPED
            if (now - lastLaunchAt < 3000) return Launch.SKIPPED   // a start is already on its way
        }
        val i = Intent(ctx, FocusService::class.java).setAction(FocusService.ACTION_START)
            .putExtra("minutes", minutes).putExtra("taskId", taskId)
            .putExtra("origin", origin).putExtra("block", block).putExtra("endAt", endAtMs)
        return try { ctx.startForegroundService(i); lastLaunchAt = now; Launch.STARTED } catch (e: Exception) { Launch.FAILED }
    }

    /** Focus for a listed task. False when Android did not allow starting it from the background. */
    fun startTask(ctx: Context, taskId: Long, minutes: Int, block: String?): Boolean =
        launch(ctx, minutes, taskId, ORIGIN_TASK, block, false) != Launch.FAILED

    /** Focus for the rest of routine [b]. False when Android did not allow starting it from the background. */
    fun startRoutine(ctx: Context, b: DayBlock, force: Boolean = false): Boolean =
        launch(ctx, minutesUntilEnd(b), -1L, ORIGIN_ROUTINE, b.name, force) != Launch.FAILED

    /**
     * Focus for routine [b], started by the person (no task needed). It lasts until the routine's scheduled end,
     * or for the routine's whole length when it is not running right now, and ends by itself at that moment.
     * It never replaces a session that is already running.
     */
    @Synchronized
    fun startManual(ctx: Context, b: DayBlock): ManualStart {
        if (!b.focusOn) return ManualStart.FOCUS_OFF
        val nowSec = LocalTime.now().toSecondOfDay()
        val minutes = Routine.manualFocusMinutes(b, nowSec) ?: return ManualStart.NO_DURATION
        val leftMs = (Routine.secondsLeft(b, nowSec)?.times(1000L)) ?: (minutes * 60_000L)
        val endAt = System.currentTimeMillis() + leftMs
        return when (launch(ctx, minutes, -1L, ORIGIN_MANUAL, b.name, false, endAt)) {
            Launch.STARTED -> ManualStart.STARTED
            Launch.SKIPPED -> ManualStart.ALREADY_RUNNING
            Launch.FAILED -> ManualStart.NOT_ALLOWED
        }
    }

    /** Ends the running automatic session; its statistics are saved like any other session. */
    fun stopAuto(ctx: Context) {
        try {
            ctx.startService(Intent(ctx, FocusService::class.java).setAction(FocusService.ACTION_STOP).putExtra("reason", "routine_end"))
        } catch (e: Exception) { /* the service is not running, nothing to stop */ }
    }
}
