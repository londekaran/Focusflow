package com.example.focusflow.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focusflow.data.CATEGORIES
import com.example.focusflow.data.Priority
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.data.Task
import com.example.focusflow.data.TaskStatus
import com.example.focusflow.domain.Routine
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.font.FontWeight
import com.example.focusflow.ui.IconAction
import com.example.focusflow.ui.TimeDialog
import com.example.focusflow.ui.card
import com.example.focusflow.ui.clockText
import com.example.focusflow.ui.components.DestructiveButton
import com.example.focusflow.ui.components.FFChip
import com.example.focusflow.ui.components.PrimaryButton
import com.example.focusflow.ui.components.SegmentedSelector
import com.example.focusflow.ui.components.categoryColor
import com.example.focusflow.ui.fieldColors
import com.example.focusflow.ui.formatMinutes
import com.example.focusflow.ui.shortDate
import com.example.focusflow.ui.theme.FF
import com.example.focusflow.ui.theme.FFShapes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val DURATIONS = listOf(15, 30, 45, 60, 90, 120)

/** Create a new task (id 0) or edit an existing one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(vm: TasksViewModel, id: Long, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val settings = remember { ServiceLocator.settings(ctx) }
    val today = remember { LocalDate.now() }
    val blocks = remember { Routine.decode(settings.routine) }
    // The routine that is running now comes first, then the other routines.
    val currentRoutine = remember {
        Routine.currentName(blocks, java.time.LocalTime.now().let { it.hour * 60 + it.minute }) ?: blocks.firstOrNull()?.name ?: "General"
    }
    val routineChoices = remember { (listOf(currentRoutine) + Routine.sortedByStart(blocks).map { it.name } + "General").distinct() }
    var statusSel by rememberSaveable { mutableIntStateOf(0) }   // 0 pending, 1 ongoing, 2 completed
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var title by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableIntStateOf(Priority.MEDIUM) }
    var category by rememberSaveable { mutableStateOf(currentRoutine) }
    var planned by rememberSaveable { mutableStateOf(today.toString()) }
    var due by rememberSaveable { mutableStateOf<String?>(today.toString()) }
    var estimate by rememberSaveable { mutableStateOf<Int?>(null) }
    var start by rememberSaveable { mutableStateOf<Int?>(null) }
    var triedSave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    var showTime by remember { mutableStateOf(false) }
    var showMinutes by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (!loaded) {
            vm.load(id)?.let { t ->
                title = t.title; desc = t.description; priority = t.priority; category = t.category
                planned = t.date; due = t.dueDate; estimate = t.estimateMin; start = t.startMin
                statusSel = if (t.done) 2 else t.status.coerceIn(0, 1)
            }
            loaded = true
        }
    }
    BackHandler(onBack = onClose)
    // Keep the due date from falling before the planned day.
    LaunchedEffect(planned) { due?.let { if (it < planned) due = planned } }

    // Default start: now, or the day's first routine start if that is later (from the timetable).
    val defaultStart = remember {
        val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        val ws = settings.dayStartMin
        val we = settings.dayEndMin
        val raw = if (we > ws && now < ws) ws else now
        ((raw + 14) / 15 * 15).coerceAtMost(23 * 60 + 45)   // wheel moves in 15-minute steps
    }

    val dueNow = due
    val titleError = triedSave && title.isBlank()
    val dueError = dueNow != null && dueNow < planned
    val canSave = title.isNotBlank() && !dueError

    fun save() {
        triedSave = true
        if (!canSave) return
        vm.save(id) { base ->
            (base ?: Task(title = "")).copy(
                title = title.trim(), description = desc.trim(), priority = priority, category = category,
                date = planned, dueDate = due, estimateMin = estimate, startMin = start,
                status = if (statusSel == 1) TaskStatus.ONGOING else TaskStatus.PENDING,
                done = statusSel == 2,
                completedAt = if (statusSel == 2) (base?.completedAt ?: System.currentTimeMillis()) else null
            )
        }
        onClose()
    }

    val titleSupport: (@Composable () -> Unit)? = if (titleError) { { Text("Enter a title for this task.") } } else null

    Column(Modifier.fillMaxSize().background(cs.background).systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClose)
            Text(
                if (id == 0L) "Create Task" else "Edit Task", Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = { save() }) { Text("Save", style = MaterialTheme.typography.labelLarge) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FieldLabel("Title")
            TextField(
                value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What needs doing?") }, singleLine = true, isError = titleError,
                supportingText = titleSupport, textStyle = MaterialTheme.typography.titleMedium,
                shape = FFShapes.small, colors = fieldColors()
            )

            FieldLabel("Description")
            TextField(
                value = desc, onValueChange = { desc = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Optional notes") }, minLines = 3, maxLines = 6,
                shape = FFShapes.small, colors = fieldColors()
            )

            FieldLabel("Routine")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (routineChoices + category).distinct().forEach { c ->
                    FFChip(c, selected = category == c, dot = categoryColor(c), onClick = { category = c })
                }
            }

            FieldLabel("Status")
            SegmentedSelector(listOf("Pending", "Ongoing", "Completed"), statusSel, { statusSel = it })

            FieldLabel("Priority")
            SegmentedSelector(
                options = listOf("High", "Medium", "Low"),
                selectedIndex = when (priority) { Priority.HIGH -> 0; Priority.MEDIUM -> 1; else -> 2 },
                onSelect = { priority = when (it) { 0 -> Priority.HIGH; 1 -> Priority.MEDIUM; else -> Priority.LOW } },
                dots = listOf(cs.error, FF.colors.warning, FF.colors.success)
            )

            FieldLabel("Planned for")
            PickerField(Icons.Rounded.CalendarMonth, shortDate(planned), null, { pickFor = "planned" }, null)
            val tomorrow = today.plusDays(1).toString()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FFChip("Today", selected = planned == today.toString(), onClick = { planned = today.toString() })
                FFChip("Tomorrow", selected = planned == tomorrow, onClick = { planned = tomorrow })
            }

            FieldLabel("Due date")
            PickerField(
                Icons.Rounded.CalendarMonth, dueNow?.let { shortDate(it) }, "No due date", { pickFor = "due" },
                if (dueNow != null) ({ due = null }) else null
            )
            if (dueError) {
                Text("The due date can't be before the planned day.", style = MaterialTheme.typography.bodySmall, color = cs.error)
            }

            FieldLabel("Start time")
            PickerField(
                Icons.Rounded.Schedule, start?.let { clockText(it) }, "No start time", { showTime = true },
                if (start != null) ({ start = null }) else null
            )
            if (start != null) {
                val on = settings.remindersEnabled && settings.taskRemindersEnabled
                Text(
                    if (on) "You'll get a reminder ${settings.taskLeadMin} minutes before the start time. Change this in Settings."
                    else "Start-time reminders are switched off in Settings.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }

            FieldLabel("Estimated duration")
            val est = estimate
            val custom = est != null && est !in DURATIONS
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FFChip("None", selected = est == null, onClick = { estimate = null })
                DURATIONS.forEach { m -> FFChip(formatMinutes(m), selected = est == m, onClick = { estimate = m }) }
                FFChip(if (custom && est != null) formatMinutes(est) else "Other…", selected = custom, onClick = { showMinutes = true })
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Save Task", onClick = { save() }, modifier = Modifier.fillMaxWidth())
            if (id != 0L) {
                DestructiveButton("Delete task", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    pickFor?.let { which ->
        val initial = LocalDate.parse(if (which == "planned") planned else (due ?: planned))
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickFor = null },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        if (which == "planned") planned = d else due = d
                    }
                    pickFor = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickFor = null }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        TimeDialog("Start time", start ?: defaultStart, 15, onSave = { start = it; showTime = false }, onDismiss = { showTime = false })
    }

    if (showMinutes) {
        MinutesDialog(estimate ?: 60, onSave = { estimate = it; showMinutes = false }, onDismiss = { showMinutes = false })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = cs.surface,
            title = { Text("Delete this task?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton({ vm.delete(id); confirmDelete = false; onClose() }) { Text("Delete", color = cs.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/** A tappable field that opens a picker. When [value] is null the [placeholder] is shown. */
@Composable
private fun PickerField(icon: ImageVector, value: String?, placeholder: String?, onClick: () -> Unit, onClear: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).card().clickable(onClick = onClick).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            value ?: placeholder.orEmpty(), Modifier.weight(1f).padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (value != null) cs.onSurface else cs.onSurfaceVariant
        )
        if (onClear != null) IconAction(Icons.Rounded.Close, "Clear", onClear)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinutesDialog(initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val values = remember { (1..120).map { it * 5 } }   // 5 min to 10 h, scroll to choose
    val state = rememberLazyListState(initialFirstVisibleItemIndex = ((initial.coerceIn(5, 600) / 5) - 1).coerceIn(0, values.lastIndex))
    val selected by remember {
        derivedStateOf {
            val li = state.layoutInfo
            val center = (li.viewportStartOffset + li.viewportEndOffset) / 2
            li.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }?.index?.let { values[it] } ?: initial
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = { Text("Estimated duration") },
        text = {
            Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(44.dp).background(cs.primaryContainer, FFShapes.small))
                LazyColumn(
                    state = state, flingBehavior = rememberSnapFlingBehavior(state),
                    contentPadding = PaddingValues(vertical = 44.dp), modifier = Modifier.fillMaxSize()
                ) {
                    items(values) { v ->
                        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                            Text(
                                formatMinutes(v), style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (v == selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (v == selected) cs.primary else cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSave(selected) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
