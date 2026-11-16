package com.example.focusflow.system

import android.content.Context
import com.example.focusflow.FocusState
import com.example.focusflow.data.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DataWipe {
    /** Deletes all tasks, history, sessions, interruptions, breaks and chosen apps. Settings are kept. */
    suspend fun wipeAll(ctx: Context) = withContext(Dispatchers.IO) {
        AppDb.get(ctx).clearAllTables()
        ReminderScheduler.cancelAll(ctx)
        FocusState.distractions.value = 0
    }
}
