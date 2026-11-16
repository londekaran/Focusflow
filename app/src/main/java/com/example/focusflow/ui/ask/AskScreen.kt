package com.example.focusflow.ui.ask

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.data.Task
import com.example.focusflow.domain.TaskRules
import com.example.focusflow.ui.Chip
import com.example.focusflow.ui.Header
import com.example.focusflow.ui.PillField
import com.example.focusflow.ui.RoundButton
import com.example.focusflow.ui.card
import java.time.LocalDate

@Composable
fun AskScreen(tasks: List<Task>) {
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }

    fun ask(query: String) {
        val s = query.lowercase()
        answer = if (listOf("task", "pending", "left", "done", "complete", "progress", "overdue").any { it in s })
            TaskRules.summaryText(tasks, LocalDate.now())
        else "I can answer questions about your task progress."
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { q = it; ask(it) }
    }

    Column(Modifier.fillMaxSize()) {
        Header("Ask", "Check your progress anytime")

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("How many pending?", "How many done?", "Any overdue?", "My progress").forEach { label ->
                Chip(label) { q = label; ask(label) }
            }
        }

        answer?.let {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().card().padding(20.dp)) { Text(it, fontSize = 18.sp) }
        }

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth().imePadding().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PillField(
                q, { q = it }, "Ask about your tasks", { ask(q.ifBlank { "progress" }) }, Modifier.weight(1f),
                trailing = {
                    Icon(
                        Icons.Rounded.Mic, "Voice", tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable {
                            voice.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            )
                        }.padding(8.dp)
                    )
                }
            )
            Spacer(Modifier.width(10.dp))
            RoundButton(Icons.Rounded.ArrowUpward, "Ask") { ask(q.ifBlank { "progress" }) }
        }
    }
}
