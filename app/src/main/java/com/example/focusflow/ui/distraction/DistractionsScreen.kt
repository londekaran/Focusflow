package com.example.focusflow.ui.distraction

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.focusflow.UiTick
import com.example.focusflow.domain.Analytics
import com.example.focusflow.hasOverlayAccess
import com.example.focusflow.hasUsageAccess
import com.example.focusflow.ui.IconAction
import com.example.focusflow.ui.SectionTitle
import com.example.focusflow.ui.SwitchCard
import com.example.focusflow.ui.components.AppIcon
import com.example.focusflow.ui.components.FFCard
import com.example.focusflow.ui.components.LinearProgress
import com.example.focusflow.ui.components.SecondaryButton
import com.example.focusflow.ui.components.SegmentedSelector
import com.example.focusflow.ui.focus.FocusViewModel
import com.example.focusflow.ui.formatHm
import java.time.LocalDate

private fun duration(sec: Int): String = if (sec < 60) "${sec}s" else formatHm(sec / 60)

/**
 * Where distraction time went. Everything here comes from app use recorded during focus sessions,
 * read from Android's usage log with your permission. Nothing is estimated or invented.
 */
@Composable
fun DistractionsScreen(
    distractionVm: DistractionViewModel, focusVm: FocusViewModel,
    onBack: () -> Unit, onManageApps: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val interruptions by distractionVm.interruptions.collectAsState()
    val apps by distractionVm.apps.collectAsState()
    val monitoring by distractionVm.monitoring.collectAsState()
    val live by distractionVm.liveAlerts.collectAsState()
    val block by distractionVm.blockApps.collectAsState()
    val sessions by focusVm.sessions.collectAsState()
    val tick = UiTick.resume.intValue
    val today = remember(tick) { LocalDate.now() }
    val access = remember(tick) { hasUsageAccess(ctx) }
    val overlay = remember(tick) { hasOverlayAccess(ctx) }
    var period by rememberSaveable { mutableIntStateOf(0) }   // 0 today, 1 seven days, 2 thirty days

    val from = today.minusDays(when (period) { 0 -> 0L; 1 -> 6L; else -> 29L })
    val items = remember(interruptions, from, today) { Analytics.interruptionsInRange(interruptions, from, today) }
    val summary = remember(items) { Analytics.distractionSummary(items) }
    val monitored = remember(sessions, from, today) { Analytics.monitoredSessions(sessions, from, today) }

    BackHandler(onBack = onBack)

    fun openOverlaySettings() {
        try {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        } catch (e: Exception) { /* not available */ }
    }

    fun openUsageSettings() {
        try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (e: Exception) { /* not available */ }
    }

    Column(Modifier.fillMaxSize().background(cs.background).systemBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.padding(top = 4.dp)) {
                Text("Distractions", style = MaterialTheme.typography.headlineMedium)
                Text("Understand where your time goes", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }

            if (!access) {
                FFCard(Modifier.fillMaxWidth()) {
                    Text("Usage access is off", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AMU can only see which apps you open if you allow usage access in Android settings. " +
                            "Without it, everything else still works, but distractions aren't recorded.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Open Android settings", onClick = { openUsageSettings() }, modifier = Modifier.fillMaxWidth())
                }
            }

            SegmentedSelector(listOf("Today", "7 Days", "30 Days"), period, { period = it })

            // Interruption summary
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Interruptions", "${summary.interruptions}", Modifier.weight(1f))
                SummaryCard(
                    "Interrupted sessions",
                    if (monitored > 0) "${summary.sessionsAffected} of $monitored" else "${summary.sessionsAffected}",
                    Modifier.weight(1f)
                )
                SummaryCard("Time distracted", duration(summary.totalSec), Modifier.weight(1f))
            }

            // Top distracting apps
            SectionTitle("Top distracting apps")
            FFCard(Modifier.fillMaxWidth()) {
                if (summary.apps.isEmpty()) {
                    Text(
                        when {
                            !monitoring -> "Tracking is off. Turn it on below to record app use during focus sessions."
                            !access -> "Allow usage access to record app use during focus sessions."
                            apps.isEmpty() -> "Choose the apps that distract you to start recording."
                            else -> "No distracting app use was recorded in this period."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                } else {
                    val maxSec = summary.apps.maxOf { it.totalSec }.coerceAtLeast(1)
                    summary.apps.take(8).forEach { a ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(a.pkg, a.label, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(duration(a.totalSec), style = MaterialTheme.typography.titleSmall)
                                }
                                Text(
                                    "${a.count} time${if (a.count == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearProgress(a.totalSec.toFloat() / maxSec)
                            }
                        }
                    }
                }
            }
            Text(
                "Recorded during focus sessions only, from Android's usage log. It can lag by a few seconds.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )

            // Monitoring settings
            SectionTitle("Monitoring")
            SwitchCard("Track app usage", "Records your chosen apps during focus sessions.", monitoring) { distractionVm.setMonitoring(it) }
            SwitchCard(
                "Block distracting apps",
                "Covers a chosen app with a full-screen warning during a focus session.",
                block && monitoring, enabled = monitoring
            ) { distractionVm.setBlockApps(it) }
            SwitchCard(
                "Live reminders", "A notification when you open a chosen app during a session.",
                live && monitoring, enabled = monitoring
            ) { distractionVm.setLiveAlerts(it) }
            SettingRow(
                "Distracting apps", if (apps.isEmpty()) "None chosen yet" else "${apps.size} selected", "Manage", onManageApps
            )
            SettingRow(
                "Usage access", if (access) "Allowed" else "Not allowed. App usage can't be recorded.",
                if (access) "Manage" else "Allow"
            ) { openUsageSettings() }
            SettingRow(
                "Display over other apps", if (overlay) "Allowed" else "Not allowed. Needed to block apps.",
                if (overlay) "Manage" else "Allow"
            ) { openOverlaySettings() }

            // Live alert information
            FFCard(Modifier.fillMaxWidth()) {
                Text("How alerts work", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        !monitoring -> "Tracking is off, so nothing is recorded, no reminders are sent and no apps are blocked."
                        block && overlay -> "When you open one of your chosen apps during a focus session, AMU covers it with a " +
                            "full-screen warning. You can go back to AMU, go home, or end the session. Android can delay " +
                            "detection by a second or two."
                        block -> "Blocking is switched on but needs \"Display over other apps\". Until you allow it, you get a " +
                            "reminder notification instead."
                        live -> "When you open one of your chosen apps during a focus session, AMU sends one reminder " +
                            "(at most one every 30 seconds). Android can delay it by a few seconds."
                        else -> "Live reminders and blocking are off. Your sessions are still recorded for the report."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier) {
    FFCard(modifier.fillMaxHeight(), padding = 12.dp) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    FFCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}
