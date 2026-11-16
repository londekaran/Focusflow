package com.example.focusflow.ui.voice

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.focusflow.UiTick
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.system.ReminderScheduler
import com.example.focusflow.system.VoiceScheduler
import com.example.focusflow.system.VoiceService
import com.example.focusflow.ui.components.CollapsibleSection
import com.example.focusflow.ui.SwitchCard
import com.example.focusflow.ui.TimeDialog
import com.example.focusflow.ui.clockText
import com.example.focusflow.ui.rowSurface
import com.example.focusflow.ui.components.SecondaryButton
import com.example.focusflow.ui.components.StepperRow
import kotlinx.coroutines.launch

private fun open(ctx: android.content.Context, intent: Intent) {
    try { ctx.startActivity(intent) } catch (e: Exception) { /* settings page not available on this phone */ }
}

/** The voice assistant settings: on/off, and when each spoken message happens. */
@Composable
fun VoiceSettingsSection() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s = remember { ServiceLocator.settings(ctx) }
    val scope = rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    val refresh = rev   // reading this makes the section redraw after a change
    val tick = UiTick.resume.intValue
    val exactOk = remember(tick, refresh) { VoiceScheduler.canScheduleExact(ctx) }
    val on = s.voiceEnabled

    fun change(block: () -> Unit) {
        block()
        rev++
        ReminderScheduler.reschedule(ctx)   // refreshes every alarm, voice ones included
    }

    CollapsibleSection("Voice Assistant", icon = Icons.Rounded.RecordVoiceOver, subtitle = "Spoken reminders and check-ins") {
        SwitchCard(
            "Voice assistant",
            "Lists your tasks before each routine, warns before each task starts, asks for its status every " +
                "${s.taskCheckMin} minutes while it runs, and again before it ends.",
            on
        ) { v -> change { s.voiceEnabled = v } }
        SwitchCard("Speak aloud", "Off: you still get the notifications and buttons, without speech.", s.voiceSpeak, on) { v ->
            change { s.voiceSpeak = v }
        }

        StepperRow(
            "Routine briefing", "${s.briefingOffsetMin} min before",
            onMinus = { change { s.briefingOffsetMin = (s.briefingOffsetMin - 5).coerceAtLeast(0) } },
            onPlus = { change { s.briefingOffsetMin = (s.briefingOffsetMin + 5).coerceAtMost(120) } },
            enabled = on
        )
        StepperRow(
            "Task incoming alert", "${s.incomingLeadMin} min before",
            onMinus = { change { s.incomingLeadMin = (s.incomingLeadMin - 1).coerceAtLeast(1) } },
            onPlus = { change { s.incomingLeadMin = (s.incomingLeadMin + 1).coerceAtMost(30) } },
            enabled = on
        )
        StepperRow(
            "Deadline status question", "${s.deadlineLeadMin} min before",
            onMinus = { change { s.deadlineLeadMin = (s.deadlineLeadMin - 1).coerceAtLeast(1) } },
            onPlus = { change { s.deadlineLeadMin = (s.deadlineLeadMin + 1).coerceAtMost(30) } },
            enabled = on
        )
        StepperRow(
"Task status follow-up every", "${s.taskCheckMin} min",
            onMinus = { change { s.taskCheckMin = (s.taskCheckMin - 5).coerceAtLeast(5) } },
            onPlus = { change { s.taskCheckMin = (s.taskCheckMin + 5).coerceAtMost(60) } },
            enabled = on
        )
        SwitchCard(
            "Plan my day automatically",
            "Before each routine, tasks without a start time are placed into free slots of that routine, in priority order.",
            s.autoSchedule, on
        ) { v -> change { s.autoSchedule = v } }

        if (on && !exactOk && Build.VERSION.SDK_INT >= 31) {
            Column(Modifier.fillMaxWidth().rowSurface().padding(16.dp)) {
                Text("Allow exact alarms", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Without this, Android may delay spoken reminders by several minutes. Allow \"Alarms & reminders\" for AMU so they speak on time.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    "Open alarm settings",
                    onClick = { open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Column(Modifier.fillMaxWidth().rowSurface().padding(16.dp)) {
            Text("Test the voice", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Speech uses your media volume and the phone's text-to-speech engine. If you hear nothing, raise the " +
                    "media volume or check Android's text-to-speech settings.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(
                    "Speak a test", modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch { VoiceService.speakReliably(ctx, listOf("This is AMU. Voice reminders are working.")) }
                    }
                )
                SecondaryButton(
                    "Speech settings", modifier = Modifier.weight(1f), neutral = true,
                    onClick = { open(ctx, Intent("com.android.settings.TTS_SETTINGS")) }
                )
            }
        }
    }

}

