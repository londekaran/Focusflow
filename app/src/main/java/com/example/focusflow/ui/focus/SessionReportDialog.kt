package com.example.focusflow.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.data.FocusSession
import com.example.focusflow.data.Interruption
import com.example.focusflow.domain.UsageAnalyzer
import com.example.focusflow.ui.formatMinutes

private fun seconds(sec: Int): String = if (sec < 60) "$sec s" else formatMinutes(sec / 60)

@Composable
fun SessionReportDialog(session: FocusSession, interruptions: List<Interruption>, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val summary = remember(interruptions) { UsageAnalyzer.summarize(interruptions) }
    val reason = when (session.endReason) {
        "completed" -> "completed"
        "ended" -> "ended early"
        "routine_end" -> "routine ended"
        "interrupted" -> "interrupted"
        else -> "saving…"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text("Session report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Focused ${seconds(session.activeSec)} of ${session.plannedMin} min planned  ·  $reason")
                when (session.monitorState) {
                    "on" -> {
                        if (interruptions.isEmpty()) {
                            Text("No distracting app use detected.")
                        } else {
                            val total = interruptions.sumOf { it.durationSec }
                            val n = interruptions.size
                            Text(
                                "$n interruption${if (n == 1) "" else "s"}  ·  ${seconds(total)} in distracting apps",
                                fontWeight = FontWeight.Medium
                            )
                            summary.forEach { Text("${it.label}: ${it.count}×  ·  ${seconds(it.totalSec)}") }
                        }
                    }
                    "no_access" -> Text("App usage wasn't recorded because usage access isn't allowed.")
                    "no_apps" -> Text("App usage wasn't recorded because no distracting apps were chosen.")
                    else -> Text("App monitoring was off for this session.")
                }
                Text(
                    "Based on Android's usage log, which can lag by a few seconds. Nothing leaves your phone.",
                    fontSize = 12.sp, color = cs.onSurfaceVariant
                )
            }
        }
    )
}
