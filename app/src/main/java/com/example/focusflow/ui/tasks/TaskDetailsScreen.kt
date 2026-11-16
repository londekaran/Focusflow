package com.example.focusflow.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focusflow.UiTick
import com.example.focusflow.data.FocusSession
import com.example.focusflow.data.Task
import com.example.focusflow.data.TaskStatus
import com.example.focusflow.domain.TaskRules
import com.example.focusflow.ui.IconAction
import com.example.focusflow.ui.clockText
import com.example.focusflow.ui.components.CategoryChip
import com.example.focusflow.ui.components.FFCard
import com.example.focusflow.ui.components.PrimaryButton
import com.example.focusflow.ui.components.PriorityChip
import com.example.focusflow.ui.components.SecondaryButton
import com.example.focusflow.ui.formatHm
import com.example.focusflow.ui.formatMinutes
import com.example.focusflow.ui.shortDate
import com.example.focusflow.ui.theme.FF
import com.example.focusflow.ui.theme.FFShapes
import java.time.LocalDate

@Composable
fun TaskDetailsScreen(
    vm: TasksViewModel, sessions: List<FocusSession>, id: Long,
    onBack: () -> Unit, onEdit: (Long) -> Unit, onStartFocus: (Long) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tasks by vm.tasks.collectAsState()
    val today = remember(UiTick.resume.intValue) { LocalDate.now() }
    var snapshot by remember { mutableStateOf<Task?>(null) }
    var exists by remember { mutableStateOf<Boolean?>(null) }
    val moves by remember(id) { vm.movesFor(id) }.collectAsState(emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Confirms the task exists, and leaves this screen if it was deleted.
    LaunchedEffect(id, tasks) {
        snapshot = vm.load(id)
        exists = snapshot != null
    }
    LaunchedEffect(exists) { if (exists == false) onBack() }
    BackHandler(onBack = onBack)

    val task = tasks.firstOrNull { it.id == id } ?: snapshot

    Column(Modifier.fillMaxSize().background(cs.background).systemBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Text(
                "Task Details", Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.titleLarge
            )
            if (task != null) {
                Box {
                    IconAction(Icons.Rounded.MoreVert, "More actions") { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit(task.id) })
                        DropdownMenuItem(text = { Text("Delete", color = cs.error) }, onClick = { menuOpen = false; confirmDelete = true })
                    }
                }
            }
        }

        if (task != null) {
            val overdue = TaskRules.isOverdue(task, today)
            val focusedMin = sessions.filter { it.taskId == task.id }.sumOf { it.activeSec } / 60

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    task.title, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PriorityChip(task.priority)
                    CategoryChip(task.category)
                    when {
                        task.done -> StatusChip("Completed", FF.colors.successContainer, FF.colors.success)
                        task.status == TaskStatus.ONGOING -> StatusChip("Ongoing", FF.colors.infoContainer, FF.colors.info)
                        overdue -> StatusChip("Overdue", FF.colors.errorContainer, cs.error)
                        else -> StatusChip("Pending", FF.colors.infoContainer, FF.colors.info)
                    }
                }

                FFCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                    InfoRow("Due date", task.dueDate?.let { shortDate(it) } ?: "Not set", valueColor = if (overdue && task.dueDate != null) cs.error else null)
                    InfoDivider()
                    InfoRow("Planned date", shortDate(task.date) + (task.startMin?.let { "  ·  ${clockText(it)}" } ?: ""))
                    InfoDivider()
                    InfoRow("Estimated duration", task.estimateMin?.let { formatMinutes(it) } ?: "Not set")
                    InfoDivider()
                    InfoRow("Focused so far", if (focusedMin > 0) formatHm(focusedMin) else "No focus sessions yet")
                }

                FFCard(Modifier.fillMaxWidth()) {
                    Text("Description", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        task.description.ifBlank { "No description." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (task.description.isBlank()) cs.onSurfaceVariant else cs.onSurface
                    )
                }

                if (moves.isNotEmpty()) {
                    FFCard(Modifier.fillMaxWidth()) {
                        Text("Rescheduled ${moves.size} time${if (moves.size == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        moves.forEach { m ->
                            val movedOn = java.time.Instant.ofEpochMilli(m.movedAt)
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                            Text(
                                "${shortDate(m.fromDate)} → ${shortDate(m.toDate)}  ·  moved ${shortDate(movedOn)}",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("Edit", onClick = { onEdit(task.id) }, modifier = Modifier.weight(1f), icon = Icons.Rounded.Edit, neutral = true)
                    PrimaryButton(
                        if (task.done) "Mark Incomplete" else "Mark Complete",
                        onClick = { vm.toggle(task) }, modifier = Modifier.weight(1f)
                    )
                }
                if (!task.done) {
                    SecondaryButton(
                        "Start Focus Session", onClick = { onStartFocus(task.id) },
                        modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = cs.surface,
            title = { Text("Delete this task?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton({ vm.delete(id); confirmDelete = false }) { Text("Delete", color = cs.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StatusChip(label: String, bg: Color, dot: Color) {
    Row(
        Modifier.clip(FFShapes.chip).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
