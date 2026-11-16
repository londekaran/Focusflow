package com.example.focusflow.data

import android.content.Context

/** Settings held in SharedPreferences. Everything stays on the phone. */
class SettingsStore(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun put(k: String, v: Int) { p.edit().putInt(k, v).apply() }
    private fun put(k: String, v: Boolean) { p.edit().putBoolean(k, v).apply() }

    /** Shown in the Home greeting. Stays on the phone. */
    var userName: String
        get() = p.getString("userName", "") ?: ""
        set(v) { p.edit().putString("userName", v.trim()).apply() }

    /** 0 = no profile picture; goes up by one each time the picture changes, so screens reload it. */
    var photoVersion: Int
        get() = p.getInt("photoVersion", 0)
        set(v) { put("photoVersion", v) }

    // First-run onboarding and profile details (all stay on the phone)
    /** True once the first-time onboarding was completed. Never shown again after that. */
    var onboardingDone: Boolean
        get() = p.getBoolean("onboardingDone", false)
        set(v) { put("onboardingDone", v) }
    var phone: String
        get() = p.getString("phone", "") ?: ""
        set(v) { p.edit().putString("phone", v.trim()).apply() }
    /** Birthday as UTC midnight in epoch millis, or -1 when not set. */
    var birthdayMillis: Long
        get() = p.getLong("birthdayMillis", -1L)
        set(v) { p.edit().putLong("birthdayMillis", v).apply() }
    var gender: String
        get() = p.getString("gender", "") ?: ""
        set(v) { p.edit().putString("gender", v).apply() }
    var occupation: String
        get() = p.getString("occupation", "") ?: ""
        set(v) { p.edit().putString("occupation", v.trim()).apply() }
    var organization: String
        get() = p.getString("organization", "") ?: ""
        set(v) { p.edit().putString("organization", v.trim()).apply() }
    /** What the person mainly uses AMU for: Work, Study or Personal. */
    var focusArea: String
        get() = p.getString("focusArea", "") ?: ""
        set(v) { p.edit().putString("focusArea", v).apply() }

    /** Student, Working employee, Self-employed or Other. */
    var persona: String
        get() = p.getString("persona", "") ?: ""
        set(v) { p.edit().putString("persona", v).apply() }
    /** The day's timetable, see [com.example.focusflow.domain.Routine]. */
    var routine: String
        get() = p.getString("routine", "") ?: ""
        set(v) { p.edit().putString("routine", v).apply() }

    /** Adult-content blocker (local DNS filter through a VPN-slot; no traffic leaves except DNS lookups). */
    var nsfwBlock: Boolean
        get() = p.getBoolean("nsfwBlock", false)
        set(v) { put("nsfwBlock", v) }
    var nsfwBlockedCount: Int
        get() = p.getInt("nsfwBlockedCount", 0)
        set(v) { put("nsfwBlockedCount", v) }

    /** Start a focus session by itself when a listed task's time begins (nothing starts when no task is listed). */
    var autoFocusTasks: Boolean
        get() = p.getBoolean("autoFocusTasks", true)
        set(v) { put("autoFocusTasks", v) }
    /** The routine occurrence whose automatic focus the user stopped by hand; it is not restarted. */
    var focusSuppressKey: String
        get() = p.getString("focusSuppressKey", "") ?: ""
        set(v) { p.edit().putString("focusSuppressKey", v).apply() }
    /** The routine occurrence for which the "tap to start focus" notification was already shown. */
    var focusPromptKey: String
        get() = p.getString("focusPromptKey", "") ?: ""
        set(v) { p.edit().putString("focusPromptKey", v).apply() }
    /** The default social-app block list has been filled in once. */
    var defaultAppsSeeded: Boolean
        get() = p.getBoolean("defaultAppsSeeded", false)
        set(v) { put("defaultAppsSeeded", v) }
    /** Task ids that currently have an auto-focus alarm, so old ones can be cancelled. */
    var focusAlarmIds: Set<Long>
        get() = (p.getString("focusAlarmIds", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.toSet()
        set(v) { p.edit().putString("focusAlarmIds", v.joinToString(",")).apply() }

    // Demo account (for future use): nothing is sent anywhere and no password is kept.
    var accountEmail: String
        get() = p.getString("accountEmail", "") ?: ""
        set(v) { p.edit().putString("accountEmail", v).apply() }
    /** "google", "email", or "" when signed out. */
    var accountProvider: String
        get() = p.getString("accountProvider", "") ?: ""
        set(v) { p.edit().putString("accountProvider", v).apply() }
    /** Demo subscription: "free" or "pro". No payment is involved. */
    var plan: String
        get() = p.getString("plan", "free") ?: "free"
        set(v) { p.edit().putString("plan", v).apply() }

    // Appearance: 0 = system default, 1 = light, 2 = dark
    var themeMode: Int
        get() = p.getInt("themeMode", 0)
        // commit() (not apply()) so the choice is on disk even if the app is swiped away right after.
        set(v) { p.edit().putInt("themeMode", v).commit() }

    // Focus and distraction tracking
    var focusMin: Int
        get() = p.getInt("focusMin", 25)
        set(v) { put("focusMin", v) }
    /** Master switch: when off, no app usage is read during focus sessions. */
    var monitoringEnabled: Boolean
        get() = p.getBoolean("monitoring", true)
        set(v) { put("monitoring", v) }
    /** Notify while a session is running when a distracting app is opened. */
    var liveAlerts: Boolean
        get() = p.getBoolean("liveAlerts", true)
        set(v) { put("liveAlerts", v) }

    /** Cover a chosen app with a full-screen warning during a session. Needs "Display over other apps". */
    var blockApps: Boolean
        get() = p.getBoolean("blockApps", true)
        set(v) { put("blockApps", v) }

    /** The day's window comes from the routine timetable (work hours below are only the fallback). */
    val dayStartMin: Int get() = com.example.focusflow.domain.Routine.window(com.example.focusflow.domain.Routine.decode(routine), workStartMin, workEndMin).first
    val dayEndMin: Int get() = com.example.focusflow.domain.Routine.window(com.example.focusflow.domain.Routine.decode(routine), workStartMin, workEndMin).second
    /** How often a running task asks "ongoing or pending?". */
    var taskCheckMin: Int
        get() = p.getInt("taskCheckMin", 15)
        set(v) { put("taskCheckMin", v) }

    // Fallback work hours, in minutes after midnight
    var workStartMin: Int
        get() = p.getInt("workStartMin", 9 * 60)
        set(v) { put("workStartMin", v) }
    var workEndMin: Int
        get() = p.getInt("workEndMin", 17 * 60)
        set(v) { put("workEndMin", v) }

    // Reminders
    var remindersEnabled: Boolean
        get() = p.getBoolean("remindersEnabled", true)
        set(v) { put("remindersEnabled", v) }
    var morningEnabled: Boolean
        get() = p.getBoolean("morningEnabled", true)
        set(v) { put("morningEnabled", v) }
    var morningMin: Int
        get() = p.getInt("morningMin", 8 * 60 + 30)
        set(v) { put("morningMin", v) }
    var followUpEnabled: Boolean
        get() = p.getBoolean("followUpEnabled", true)
        set(v) { put("followUpEnabled", v) }
    var followUpMin: Int
        get() = p.getInt("followUpMin", 15 * 60)
        set(v) { put("followUpMin", v) }
    var reviewEnabled: Boolean
        get() = p.getBoolean("reviewEnabled", true)
        set(v) { put("reviewEnabled", v) }
    var reviewMin: Int
        get() = p.getInt("reviewMin", 18 * 60 + 30)
        set(v) { put("reviewMin", v) }
    var taskRemindersEnabled: Boolean
        get() = p.getBoolean("taskRemindersEnabled", true)
        set(v) { put("taskRemindersEnabled", v) }
    var taskLeadMin: Int
        get() = p.getInt("taskLeadMin", 10)
        set(v) { put("taskLeadMin", v) }
    var resumeEnabled: Boolean
        get() = p.getBoolean("resumeEnabled", true)
        set(v) { put("resumeEnabled", v) }
    var resumeDelayMin: Int
        get() = p.getInt("resumeDelayMin", 15)
        set(v) { put("resumeDelayMin", v) }
    /** Upper limit for reminder notifications per day, so the app never floods the phone. */
    var maxPerDay: Int
        get() = p.getInt("maxPerDay", 6)
        set(v) { put("maxPerDay", v) }
    var exactReminders: Boolean
        get() = p.getBoolean("exactReminders", false)
        set(v) { put("exactReminders", v) }

    // Breaks
    var breakEnabled: Boolean
        get() = p.getBoolean("breakEnabled", true)
        set(v) { put("breakEnabled", v) }
    var breakMin: Int
        get() = p.getInt("breakMin", 5)
        set(v) { put("breakMin", v) }

    // Calendar
    var calendarEnabled: Boolean
        get() = p.getBoolean("calendarEnabled", false)
        set(v) { put("calendarEnabled", v) }

    // Voice assistant
    /** Master switch for the spoken workflow. Off by default; the user turns it on in Settings. */
    var voiceEnabled: Boolean
        get() = p.getBoolean("voiceEnabled", false)
        set(v) { put("voiceEnabled", v) }
    /** When off, the same events show notifications without speaking. */
    var voiceSpeak: Boolean
        get() = p.getBoolean("voiceSpeak", true)
        set(v) { put("voiceSpeak", v) }
    /** The morning briefing is this many minutes before work starts (08:30 for a 09:00 start). */
    var briefingOffsetMin: Int
        get() = p.getInt("briefingOffsetMin", 10)
        set(v) { put("briefingOffsetMin", v) }
    var incomingLeadMin: Int
        get() = p.getInt("incomingLeadMin", 5)
        set(v) { put("incomingLeadMin", v) }
    var deadlineLeadMin: Int
        get() = p.getInt("deadlineLeadMin", 5)
        set(v) { put("deadlineLeadMin", v) }
    var checkInEveryMin: Int
        get() = p.getInt("checkInEveryMin", 50)
        set(v) { put("checkInEveryMin", v) }
    /** At the briefing, tasks without a start time are placed into free slots of the work day. */
    var autoSchedule: Boolean
        get() = p.getBoolean("autoSchedule", true)
        set(v) { put("autoSchedule", v) }
    /** Keys of the voice alarms currently set, so stale ones can be cancelled. */
    var voiceAlarmKeys: Set<String>
        get() = (p.getString("voiceAlarmKeys", "") ?: "").split("|").filter { it.isNotEmpty() }.toSet()
        set(v) { p.edit().putString("voiceAlarmKeys", v.joinToString("|")).apply() }

    /** Ids of tasks that currently have a start-time alarm, so old ones can be cancelled. */
    var scheduledTaskIds: Set<Long>
        get() = (p.getString("scheduledTaskIds", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.toSet()
        set(v) { p.edit().putString("scheduledTaskIds", v.joinToString(",")).apply() }

    /** Counts a reminder against today's limit. Returns false when the limit is already reached. */
    fun tryConsumeBudget(day: String): Boolean {
        val n = if (p.getString("budgetDay", "") == day) p.getInt("budgetCount", 0) else 0
        if (n >= maxPerDay) return false
        p.edit().putString("budgetDay", day).putInt("budgetCount", n + 1).apply()
        return true
    }
}
