package com.example.focusflow.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.example.focusflow.FocusState
import com.example.focusflow.UiTick
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.hasOverlayAccess
import com.example.focusflow.hasUsageAccess
import com.example.focusflow.system.CalendarReader
import com.example.focusflow.system.DataWipe
import com.example.focusflow.system.ProfilePhoto
import com.example.focusflow.ui.account.planLabel
import com.example.focusflow.ui.components.ProfileAvatar
import com.example.focusflow.ui.components.rememberProfilePhotoPicker
import com.example.focusflow.ui.rowSurface
import com.example.focusflow.system.ReminderScheduler
import com.example.focusflow.ui.Chip
import com.example.focusflow.ui.IconAction
import com.example.focusflow.ui.PillField
import com.example.focusflow.ui.components.CollapsibleSection
import com.example.focusflow.ui.SwitchCard
import com.example.focusflow.ui.TimeDialog
import com.example.focusflow.ui.card
import com.example.focusflow.ui.clockText
import com.example.focusflow.ui.focus.FocusViewModel
import com.example.focusflow.ui.plan.PlanViewModel
import com.example.focusflow.ui.theme.ThemeState
import com.example.focusflow.ui.voice.VoiceSettingsSection
import kotlinx.coroutines.launch
import androidx.compose.material3.Text

private class TimeEdit(val title: String, val value: Int, val onSave: (Int) -> Unit)

private fun open(ctx: Context, intent: Intent) {
    try { ctx.startActivity(intent) } catch (e: Exception) { /* settings page not available on this phone */ }
}

private fun appDetails(ctx: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))

@Composable
fun SettingsScreen(
    focusVm: FocusViewModel, planVm: PlanViewModel, onClose: () -> Unit, onDistractions: () -> Unit,
    onAccount: () -> Unit, onPlans: () -> Unit,
    onAbout: () -> Unit = {}, onTerms: () -> Unit = {}, onPrivacy: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s = remember { ServiceLocator.settings(ctx) }
    val scope = rememberCoroutineScope()
    val tick = UiTick.resume.intValue
    var rev by remember { mutableIntStateOf(0) }
    val refresh = rev   // reading this makes the screen redraw after a setting changes
    var timeEdit by remember { mutableStateOf<TimeEdit?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(s.userName) }
    var photoV by remember { mutableIntStateOf(s.photoVersion) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val running by FocusState.running.collectAsState()
    val inBreak by FocusState.breakRunning.collectAsState()
    val focusMin by focusVm.focusMin.collectAsState()
    val calendarOn by planVm.calendarOn.collectAsState()

    BackHandler(onBack = onClose)

    fun change(reschedule: Boolean = true, block: () -> Unit) {
        block()
        rev++
        if (reschedule) ReminderScheduler.reschedule(ctx)
    }

    val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        planVm.setCalendarOn(ok)
        if (!ok) message = "Calendar access wasn't allowed, so the calendar stays off."
        rev++
    }

    val pickPhoto = rememberProfilePhotoPicker { ok ->
        if (ok) { s.photoVersion = s.photoVersion + 1; photoV = s.photoVersion; photoError = null }
        else photoError = "That picture couldn't be used. Try another one."
    }

    val notifOn = remember(tick, refresh) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val usageOn = remember(tick) { hasUsageAccess(ctx) }
    val overlayOn = remember(tick) { hasOverlayAccess(ctx) }
    val calPerm = remember(tick, calendarOn) { CalendarReader.hasPermission(ctx) }
    val exactAllowed = remember(tick, refresh) {
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    val on = s.remindersEnabled

    Column(Modifier.fillMaxSize().background(cs.background).systemBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Close, "Close", tint = cs.onSurface,
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).padding(10.dp)
            )
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            /* -------------------------------- Profile -------------------------------- */
            Column(Modifier.fillMaxWidth().card().padding(bottom = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(72.dp, name, photoV)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("Profile picture", fontSize = 16.sp)
                        Row {
                            TextButton({ pickPhoto() }) { Text(if (photoV > 0) "Change" else "Choose photo") }
                            if (photoV > 0) {
                                TextButton({
                                    ProfilePhoto.remove(ctx)
                                    s.photoVersion = 0
                                    photoV = 0
                                }) { Text("Remove", color = cs.error) }
                            }
                        }
                    }
                }
                photoError?.let { Text(it, fontSize = 13.sp, color = cs.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                PillField(name, { name = it; s.userName = it }, "Your name", {}, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                InfoText("Your name and picture are used only on Home and in Settings. They stay on this phone.")
            }

            /* -------------------------------- Account -------------------------------- */
            CollapsibleSection("Account", icon = Icons.Rounded.Person, subtitle = "Sign in and subscription") {
                val signedIn = s.accountEmail.isNotBlank()
                ActionRow(
                    if (signedIn) "Signed in" else "Sign in",
                    if (signedIn) s.accountEmail else "With Google or email. A demo for now.",
                    if (signedIn) "Manage" else "Sign in"
                ) { onAccount() }
                ActionRow("Subscription", "${planLabel(s.plan)} plan", "View plans") { onPlans() }
            }

            /* ------------------------------ Appearance ------------------------------ */
            CollapsibleSection("Appearance", icon = Icons.Rounded.Palette, subtitle = "System default, light or dark") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System default" to 0, "Light" to 1, "Dark" to 2).forEach { (label, mode) ->
                        Chip(label, selected = ThemeState.mode.intValue == mode) {
                            ThemeState.set(ctx, mode)
                        }
                    }
                }
            }

            /* ---------------------------- Voice assistant ---------------------------- */
            VoiceSettingsSection()

            /* ------------------------- Adult content blocker ------------------------- */
            val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
                if (r.resultCode == android.app.Activity.RESULT_OK) { s.nsfwBlock = true; com.example.focusflow.system.NsfwVpnService.start(ctx) }
                else message = "Permission wasn't given, so the blocker stays off."
                rev++
            }
            CollapsibleSection("Adult Content Blocker", icon = Icons.Rounded.Shield, subtitle = "Filter adult sites on this phone") {
                SwitchCard("Block porn and NSFW sites", "Works in every browser and app. Blocked so far: ${s.nsfwBlockedCount}", s.nsfwBlock) { v ->
                    if (v) {
                        val i = android.net.VpnService.prepare(ctx)
                        if (i != null) vpnLauncher.launch(i) else { s.nsfwBlock = true; com.example.focusflow.system.NsfwVpnService.start(ctx) }
                    } else { s.nsfwBlock = false; com.example.focusflow.system.NsfwVpnService.stop(ctx) }
                    rev++
                }
                InfoText("Uses a local filter on this phone (Android shows it as a VPN). Only website names are checked and nothing is recorded. Turn off \"Secure DNS\" in your browser, and set Android Private DNS to Off or Automatic, so it can't bypass the filter.")
            }

            /* ------------------------------ Reminders ------------------------------ */
            CollapsibleSection("Reminders", icon = Icons.Rounded.NotificationsActive, subtitle = "Plan, follow-up and task alerts") {
                SwitchCard("Reminders", "Plan, follow-up, review and task notifications. Off stops all of them.", on) { v ->
                    change { s.remindersEnabled = v }
                }
                SwitchCard("Morning plan", "Today's tasks and deadlines.", s.morningEnabled, on) { v -> change { s.morningEnabled = v } }
                TimeRow("Morning plan time", s.morningMin, on && s.morningEnabled) {
                    timeEdit = TimeEdit("Morning plan time", s.morningMin) { m -> change { s.morningMin = m } }
                }
                SwitchCard("Follow-up", "A nudge about unfinished tasks. Skipped when nothing is left.", s.followUpEnabled, on) { v ->
                    change { s.followUpEnabled = v }
                }
                TimeRow("Follow-up time", s.followUpMin, on && s.followUpEnabled) {
                    timeEdit = TimeEdit("Follow-up time", s.followUpMin) { m -> change { s.followUpMin = m } }
                }
                SwitchCard("Daily review", "A short summary of your day. Tap it to open your review.", s.reviewEnabled, on) { v ->
                    change { s.reviewEnabled = v }
                }
                TimeRow("Daily review time", s.reviewMin, on && s.reviewEnabled) {
                    timeEdit = TimeEdit("Daily review time", s.reviewMin) { m -> change { s.reviewMin = m } }
                }
                SwitchCard("Task start reminders", "For tasks that have a start time. You can snooze or mark them done.", s.taskRemindersEnabled, on) { v ->
                    change { s.taskRemindersEnabled = v }
                }
                StepRow("Remind me before the start", "${s.taskLeadMin} min", on && s.taskRemindersEnabled,
                    onMinus = { change { s.taskLeadMin = (s.taskLeadMin - 5).coerceAtLeast(5) } },
                    onPlus = { change { s.taskLeadMin = (s.taskLeadMin + 5).coerceAtMost(60) } })
                SwitchCard("Get back to it", "After you end a session early, one reminder to resume that task.", s.resumeEnabled, on) { v ->
                    change { s.resumeEnabled = v }
                }
                StepRow("Remind me after", "${s.resumeDelayMin} min", on && s.resumeEnabled,
                    onMinus = { change { s.resumeDelayMin = (s.resumeDelayMin - 5).coerceAtLeast(5) } },
                    onPlus = { change { s.resumeDelayMin = (s.resumeDelayMin + 5).coerceAtMost(60) } })
                StepRow("Daily limit", "${s.maxPerDay} reminders", on,
                    onMinus = { change(false) { s.maxPerDay = (s.maxPerDay - 1).coerceAtLeast(2) } },
                    onPlus = { change(false) { s.maxPerDay = (s.maxPerDay + 1).coerceAtMost(12) } })
                SwitchCard(
                    "Exact timing",
                    "Reminders arrive on the minute instead of a few minutes late. Android may ask you to allow this separately.",
                    s.exactReminders, on
                ) { v -> change { s.exactReminders = v } }
                if (s.exactReminders && !exactAllowed && Build.VERSION.SDK_INT >= 31) {
                    ActionRow("Exact alarms", "Not allowed by Android yet. Reminders still work, just less precisely.", "Allow") {
                        open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                    }
                }
                ActionRow("Notifications", if (notifOn) "Allowed" else "Blocked in Android settings. No reminders can appear.", "Open") {
                    open(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
            }

            /* ---------------------------- Focus and breaks ---------------------------- */
            CollapsibleSection("Focus & Breaks", icon = Icons.Rounded.Timer, subtitle = "Session length, breaks, distractions") {
                SwitchCard("Auto-start focus for tasks", "Starts a focus timer when a listed task begins. No task, no timer.", s.autoFocusTasks) { s.autoFocusTasks = it; rev++; com.example.focusflow.system.ReminderScheduler.reschedule(ctx) }
                StepRow("Default session length", "$focusMin min", true,
                    onMinus = { focusVm.setFocusMin((focusMin - 5).coerceAtLeast(5)); rev++ },
                    onPlus = { focusVm.setFocusMin((focusMin + 5).coerceAtMost(1440)); rev++ })
                SwitchCard(
                    "Break reminders",
                    "After a finished session, suggests a short break: stretch, walk, drink water or rest your eyes.",
                    s.breakEnabled
                ) { v -> change(false) { s.breakEnabled = v } }
                StepRow("Break length", "${s.breakMin} min", s.breakEnabled,
                    onMinus = { change(false) { s.breakMin = (s.breakMin - 1).coerceAtLeast(3) } },
                    onPlus = { change(false) { s.breakMin = (s.breakMin + 1).coerceAtMost(30) } })
                ActionRow("Distraction tracking", "Choose which apps count as distracting, and how they are tracked.", "Open") { onDistractions() }
            }

            /* -------------------------------- Calendar -------------------------------- */
            CollapsibleSection("Calendar", icon = Icons.Rounded.CalendarMonth, subtitle = "Show your phone calendar in Plan") {
                SwitchCard(
                    "Show my calendar",
                    "Reads event titles and times from your phone's calendars to show your day and find free time. Read-only; nothing leaves your phone.",
                    calendarOn && calPerm
                ) { v ->
                    if (v) {
                        if (CalendarReader.hasPermission(ctx)) { planVm.setCalendarOn(true); rev++ }
                        else calLauncher.launch(Manifest.permission.READ_CALENDAR)
                    } else {
                        planVm.setCalendarOn(false)
                        rev++
                    }
                }
            }

            /* ------------------------------- Permissions ------------------------------- */
            CollapsibleSection("Permissions", icon = Icons.Rounded.VerifiedUser, subtitle = "Usage access, overlay, calendar") {
                ActionRow("Usage access", if (usageOn) "Allowed" else "Not allowed. App usage can't be tracked.", "Manage") {
                    open(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                ActionRow("Display over other apps", if (overlayOn) "Allowed" else "Not allowed. Distracting apps can't be blocked.", "Manage") {
                    open(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                }
                ActionRow("Calendar", if (calPerm) "Allowed" else "Not allowed", "App permissions") { open(ctx, appDetails(ctx)) }
                InfoText("You can turn off any permission in Android settings at any time. AMU keeps working without them, with those features switched off.")
            }

            /* --------------------------------- Privacy --------------------------------- */
            CollapsibleSection("Data & Privacy", icon = Icons.Rounded.Storage, subtitle = "What is stored, delete your data") {
                InfoText(
                    "Everything you enter stays on this phone. AMU has no accounts, no analytics and no tracking. The only internet use is the optional adult-content blocker, which looks up website names. " +
                        "It never reads messages, keystrokes or passwords. App usage is read only for the apps you chose, only during focus sessions, and only if you allow it."
                )
                val canWipe = !running && !inBreak
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(CircleShape)
                        .background(cs.error.copy(alpha = if (canWipe) 0.12f else 0.05f))
                        .clickable(enabled = canWipe) { confirmWipe = true }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (canWipe) "Delete all my data" else "End the running session to delete data",
                        color = if (canWipe) cs.error else cs.onSurfaceVariant, fontWeight = FontWeight.Medium
                    )
                }
                message?.let { Text(it, fontSize = 14.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }

            /* ---------------------------------- Battery ---------------------------------- */
            CollapsibleSection("If reminders arrive late", icon = Icons.Rounded.BatteryAlert, subtitle = "Battery settings that can delay alerts") {
                InfoText(
                    "Many phones, including Samsung, stop background apps to save battery. That can delay reminders and the focus timer notification. " +
                        "On Samsung: Settings > Apps > AMU > Battery > Unrestricted. Then open Settings > Battery > Background usage limits and make sure AMU is not in Sleeping apps or Deep sleeping apps."
                )
                ActionRow("App settings", "Open AMU's settings page in Android", "Open") { open(ctx, appDetails(ctx)) }
            }

            /* ---------------------------------- About ---------------------------------- */
            CollapsibleSection("About", icon = Icons.Rounded.Info, subtitle = "Version, terms and privacy policy") {
                ActionRow("About AMU", "Version, features and developer", "Open") { onAbout() }
                ActionRow("Terms & Conditions", "The terms for using the app", "Read") { onTerms() }
                ActionRow("Privacy Policy", "What the app stores and why", "Read") { onPrivacy() }
            }

            Text(
                "AMU · karan.n.londe@gmail.com · all data stays on this phone",
                fontSize = 12.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
            Text(
                "© 2026 Karan N Londe. All rights reserved",
                fontSize = 12.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )

        }
    }

    timeEdit?.let { e ->
        TimeDialog(e.title, e.value, 15, onSave = { e.onSave(it); timeEdit = null }, onDismiss = { timeEdit = null })
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = cs.surface,
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This permanently deletes your tasks, reschedule history, focus sessions, interruptions, break records and your list of distracting apps from this phone. " +
                        "Your settings are kept. This can't be undone."
                )
            },
            confirmButton = {
                TextButton({
                    confirmWipe = false
                    scope.launch {
                        DataWipe.wipeAll(ctx)
                        message = "All data was deleted."
                    }
                }) { Text("Delete", color = cs.error) }
            },
            dismissButton = { TextButton({ confirmWipe = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().rowSurface().clickable(enabled = enabled, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, color = if (enabled) cs.onSurface else cs.onSurfaceVariant)
        Text(clockText(minutes), fontSize = 16.sp, color = if (enabled) cs.primary else cs.onSurfaceVariant)
    }
}

@Composable
private fun StepRow(label: String, value: String, enabled: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().rowSurface().padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, color = if (enabled) cs.onSurface else cs.onSurfaceVariant)
        if (enabled) IconAction(Icons.Rounded.Remove, "Less", onMinus)
        Text(
            value, fontSize = 15.sp, textAlign = TextAlign.Center,
            color = if (enabled) cs.onSurface else cs.onSurfaceVariant, modifier = Modifier.widthIn(min = 72.dp)
        )
        if (enabled) IconAction(Icons.Rounded.Add, "More", onPlus)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, button: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().rowSurface().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 13.sp, color = cs.onSurfaceVariant)
        }
        TextButton(onClick) { Text(button) }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}
