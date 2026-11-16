package com.example.focusflow.system

import android.content.Context
import com.example.focusflow.data.AppDb
import com.example.focusflow.data.DistractingApp
import com.example.focusflow.data.ServiceLocator

/** The social apps AMU blocks by default (only those installed on the phone). */
object DefaultBlockList {
    private val SOCIAL = listOf(
        "com.instagram.android", "com.facebook.katana", "com.facebook.orca", "com.twitter.android", "com.snapchat.android",
        "com.google.android.youtube", "com.reddit.frontpage", "com.zhiliaoapp.musically", "com.whatsapp", "com.linkedin.android",
        "org.telegram.messenger", "com.discord", "com.pinterest", "com.netflix.mediaclient", "com.microsoft.teams"
    )

    /** Adds every installed social app to the distracting list. */
    suspend fun addInstalled(ctx: Context) {
        val pm = ctx.packageManager
        val dao = AppDb.get(ctx).distractionDao()
        SOCIAL.forEach { pkg ->
            if (pm.getLaunchIntentForPackage(pkg) != null) {
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                dao.addApp(DistractingApp(pkg, label))
            }
        }
        ServiceLocator.settings(ctx).defaultAppsSeeded = true
    }

    /** First focus session on a phone whose distracting list is empty (e.g. after a reinstall): fill it once. */
    suspend fun seedOnce(ctx: Context) {
        val s = ServiceLocator.settings(ctx)
        if (s.defaultAppsSeeded) return
        if (AppDb.get(ctx).distractionDao().getApps().isEmpty()) addInstalled(ctx) else s.defaultAppsSeeded = true
    }
}
