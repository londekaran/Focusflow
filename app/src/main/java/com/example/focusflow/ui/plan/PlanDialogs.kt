package com.example.focusflow.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focusflow.data.Task
import com.example.focusflow.data.TaskMove
import com.example.focusflow.ui.IconAction
import com.example.focusflow.ui.TimeDialog
import com.example.focusflow.ui.clockText
import com.example.focusflow.ui.components.FFChip
import com.example.focusflow.ui.shortDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Asks where to move one or more tasks. Nothing moves until "Move" is tapped. For a single task the start
 * time can be changed or cleared too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RescheduleDialog(
    count: Int, today: LocalDate, currentStart: Int?,
    onConfirm: (date: LocalDate, start: Int?, changeStart: Boolean) -> Unit, onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var start by remember { mutableStateOf(currentStart) }
    var startChanged by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val tomorrow = today.plusDays(1)
    val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    if (picking) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { ms ->
                        date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = cs.surface,
            title = { Text(if (count == 1) "Move task to…" else "Move $count tasks to…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FFChip("Today", selected = date == today, onClick = { date = today })
                        FFChip("Tomorrow", selected = date == tomorrow, onClick = { date = tomorrow })
                        FFChip("Next Monday", selected = date == nextMonday, onClick = { date = nextMonday })
                        val other = date != null && date != today && date != tomorrow && date != nextMonday
                        FFChip(if (other) shortDate(date.toString()) else "Pick a date…", selected = other, onClick = { picking = true })
                    }
                    Text(
                        date?.let { "New day: " + it.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) } ?: "Choose a day first.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                    if (count == 1) {
                        Text("Start time", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FFChip(start?.let { clockText(it) } ?: "No time", selected = true, onClick = { showTime = true })
                            if (start != null) {
                                FFChip("Clear", onClick = { start = null; startChanged = true })
                            }
                        }
                    }
                    Text(
                        "A due date that would fall before the new day moves with it. The move is kept in the task's history.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = date != null, onClick = {
                    val d = date
                    if (d != null) onConfirm(d, start, startChanged)
                }) { Text("Move") }
            },
            dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
        )
    }

    if (showTime) {
        TimeDialog("Start time", start ?: (9 * 60), 15,
            onSave = { start = it; startChanged = true; showTime = false }, onDismiss = { showTime = false })
    }
}

@Composable
private fun HoursRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        IconAction(Icons.Rounded.Remove, "Earlier", onMinus)
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
        IconAction(Icons.Rounded.Add, "Later", onPlus)
    }
}

/** The latest reschedules across all tasks, so nothing moves without a trace. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistorySheet(moves: List<TaskMove>, tasks: List<Task>, onOpen: (Long) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val titles = remember(tasks) { tasks.associate { it.id to it.title } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface
    ) {
        Text(
            "Rescheduling history", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (moves.isEmpty()) {
            Text(
                "Nothing has been rescheduled yet.", style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(32.dp))
        } else {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(moves, key = { it.id }) { m ->
                    val title = titles[m.taskId]
                    val movedOn = Instant.ofEpochMilli(m.movedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = title != null) { onOpen(m.taskId) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(title ?: "Deleted task", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            "${shortDate(m.fromDate)} → ${shortDate(m.toDate)}  ·  moved ${shortDate(movedOn)}",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
