package com.example.focusflow.system

import android.content.Context
import com.example.focusflow.FocusState
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.domain.DayBlock
import com.example.focusflow.domain.Routine
import java.time.LocalDate
import java.time.LocalTime

/**
 * Keeps the running focus session in step with the timetable.
 *
 * [reconcile] looks at the clock, decides which routine is active and what focus should be doing, and fixes any
 * difference. It is idempotent, so it is safe to call from everywhere: routine-boundary alarms, phone restart,
 * clock or time-zone changes, schedule edits and whenever the app opens. That is what makes missed alarms harmless.
 *
 *  - Focus off for the active routine: automatic sessions stop and nothing is blocked.
 *  - Whole-routine focus on: one session runs from the routine's start to its end.
 *  - Task-based focus: sessions start at listed tasks (see ReminderReceiver) and stop when their routine is over.
 *  - A session the user started by hand is never stopped or replaced.
 */
object RoutineFocusManager {

    @Synchronized
    fun reconcile(ctx: Context) {
        val app = ctx.applicationContext
        val s = ServiceLocator.settings(app)
        val blocks = Routine.decode(s.routine)
        val now = LocalTime.now()
        val cur = Routine.at(blocks, now.hour * 60 + now.minute)

        var afterStop = false
        if (FocusState.running.value) {
            if (FocusState.origin.value == RoutineFocus.ORIGIN_MANUAL) return
            if (!isStale(blocks, cur, FocusState.origin.value, FocusState.blockName.value, FocusState.plannedEndMs.value)) return
            RoutineFocus.stopAuto(app)
            afterStop = true
        }
        if (cur == null || !cur.focusOn || !cur.autoFocus) return
        if (s.focusSuppressKey == occurrenceKey(cur)) return
        if (!RoutineFocus.startRoutine(app, cur, afterStop)) promptToStart(app, cur)
    }

    /** An automatic session no longer matches the timetable. */
    private fun isStale(blocks: List<DayBlock>, cur: DayBlock?, origin: String, name: String?, plannedEndMs: Long): Boolean {
        if (origin == RoutineFocus.ORIGIN_ROUTINE) {
            return cur == null || cur.name != name || !cur.focusOn || !cur.autoFocus ||
                Math.abs(endInstantMs(cur) - plannedEndMs) > 90_000   // the routine's end was edited
        }
        // A task session ends with its routine. Tasks outside every routine ("General") are left alone.
        val owner = blocks.firstOrNull { it.name == name } ?: return false
        return cur == null || cur.name != owner.name || !cur.focusOn
    }

    /** The exact moment [b] ends, counted from now. */
    private fun endInstantMs(b: DayBlock): Long {
        val leftSec = ((b.end * 60 - LocalTime.now().toSecondOfDay()) + 86400) % 86400
        return System.currentTimeMillis() + leftSec * 1000L
    }

    /** Identifies one occurrence of a routine (a routine that crosses midnight belongs to the day it began). */
    fun occurrenceKey(b: DayBlock): String {
        val now = LocalTime.now()
        val day = if (b.start > b.end && now.hour * 60 + now.minute < b.start) LocalDate.now().minusDays(1) else LocalDate.now()
        return "${b.name}|$day|${b.start}"
    }

    /** The user ended an automatic routine session by hand: do not restart it for this occurrence. */
    fun suppress(ctx: Context, blockName: String) {
        val s = ServiceLocator.settings(ctx)
        val now = LocalTime.now()
        Routine.decode(s.routine).firstOrNull { it.name == blockName && Routine.contains(it, now.hour * 60 + now.minute) }
            ?.let { s.focusSuppressKey = occurrenceKey(it) }
    }

    /** Android refused a background start: ask the user to start it with one tap (once per occurrence). */
    private fun promptToStart(ctx: Context, b: DayBlock) {
        val s = ServiceLocator.settings(ctx)
        val key = occurrenceKey(b)
        if (s.focusPromptKey == key) return
        s.focusPromptKey = key
        Notifications.ensureChannels(ctx)
        Notifications.post(
            ctx, 2900, Notifications.CH_REMIND, "${b.name} focus",
            "Tap to start focus and app blocking for the rest of ${b.name}.", Notifications.openApp(ctx, "focus")
        )
    }
}
