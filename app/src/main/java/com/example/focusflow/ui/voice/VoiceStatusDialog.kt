package com.example.focusflow.ui.voice

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.focusflow.StatusRequest
import com.example.focusflow.data.AppDb
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.data.Task
import com.example.focusflow.data.TaskStatus
import com.example.focusflow.domain.VoiceMessages
import com.example.focusflow.domain.VoiceOp
import com.example.focusflow.domain.VoicePlan
import com.example.focusflow.system.VoiceActions
import com.example.focusflow.system.VoiceReceiver
import com.example.focusflow.system.VoiceService
import com.example.focusflow.ui.components.DestructiveButton
import com.example.focusflow.ui.components.FFChip
import com.example.focusflow.ui.components.PrimaryButton
import com.example.focusflow.ui.components.SecondaryButton
import kotlinx.coroutines.launch

/**
 * Shown when a voice notification is tapped. Offers the same answers as the notification buttons, and more:
 * any amount of extra time, pending and discard.
 */
@Composable
fun VoiceStatusDialog(request: StatusRequest, tasks: List<Task>, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<Task?>(null) }
    var showExtend by remember { mutableStateOf(false) }

    // If the task was finished or discarded in the meantime there is nothing to ask.
    LaunchedEffect(request.taskId) {
        val t = AppDb.get(ctx).dao().getById(request.taskId)
        snapshot = t
        if (t == null || t.done || t.status == TaskStatus.DISCARDED) onDismiss()
    }
    val task = tasks.firstOrNull { it.id == request.taskId } ?: snapshot ?: return

    fun run(op: VoiceOp, minutes: Int = 0) {
        scope.launch {
            val message = VoiceActions.apply(ctx, op, task.id, minutes)
            VoiceReceiver.clearFor(ctx, task.id)
            if (message != null && ServiceLocator.settings(ctx).voiceSpeak) VoiceService.speak(ctx, listOf(message))
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = { Text(if (request.mode == "incoming") "Task starting soon" else "What is the status of this task?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                task.startMin?.let {
                    Text(
                        "${VoiceMessages.clock(it)} – ${VoiceMessages.clock(it + VoicePlan.durationOf(task))}",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                }
                when {
                    showExtend -> {
                        Text("How much more time do you need?", style = MaterialTheme.typography.bodyMedium)
                        Column(Modifier.horizontalScroll(rememberScrollState())) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(10, 15, 30, 45, 60).forEach { m -> FFChip("+$m min", onClick = { run(VoiceOp.EXTEND, m) }) }
                            }
                        }
                        Text(
                            "Tasks after this one move forward only as far as needed.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    request.mode == "incoming" -> {
                        PrimaryButton("Ongoing", onClick = { run(VoiceOp.ONGOING) }, modifier = Modifier.fillMaxWidth())
                        SecondaryButton("Pending", onClick = { run(VoiceOp.PENDING) }, modifier = Modifier.fillMaxWidth(), neutral = true)
                        DestructiveButton("Discard", onClick = { run(VoiceOp.DISCARD) }, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        PrimaryButton("Done", onClick = { run(VoiceOp.DONE) }, modifier = Modifier.fillMaxWidth())
                        SecondaryButton("Need more time…", onClick = { showExtend = true }, modifier = Modifier.fillMaxWidth())
                        SecondaryButton("Still working", onClick = { run(VoiceOp.ONGOING) }, modifier = Modifier.fillMaxWidth(), neutral = true)
                        SecondaryButton("Pending", onClick = { run(VoiceOp.PENDING) }, modifier = Modifier.fillMaxWidth(), neutral = true)
                        DestructiveButton("Discard", onClick = { run(VoiceOp.DISCARD) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton({ if (showExtend) showExtend = false else onDismiss() }) { Text(if (showExtend) "Back" else "Later") }
        }
    )
}
