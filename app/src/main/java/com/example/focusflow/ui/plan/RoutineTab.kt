package com.example.focusflow.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.focusflow.UiTick
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.domain.DayBlock
import com.example.focusflow.domain.Routine
import com.example.focusflow.ui.PillField
import com.example.focusflow.ui.TimeDialog
import com.example.focusflow.ui.card
import com.example.focusflow.ui.clockText
import com.example.focusflow.ui.components.DestructiveButton
import com.example.focusflow.ui.components.EmptyState
import com.example.focusflow.ui.components.FFChip
import com.example.focusflow.ui.components.SecondaryButton
import com.example.focusflow.ui.LocalFlatRows
import com.example.focusflow.system.ManualStart
import com.example.focusflow.system.RoutineFocus
import com.example.focusflow.ui.theme.FFShapes
import java.time.LocalTime

/**
 * The daily timetable: each routine is a card that expands in place to edit its name, times, focus and
 * blocking. "+ Add Routine Block" creates a block of your own. Blocks keep their stored order (reminder
 * alarms refer to them by position); only the display is sorted by start time, so the list is in time order
 * after every add, rename, delete and start or end time change. [onStartFocus] opens the Focus tab once a focus
 * session has been started for a routine from its Focus section.
 */
@Composable
fun RoutineTab(modifier: Modifier = Modifier, onChanged: () -> Unit = {}, onStartFocus: () -> Unit = {}) {
    val ctx = LocalContext.current
    val s = remember { ServiceLocator.settings(ctx) }
    val cs = MaterialTheme.colorScheme
    val blocks = remember {
        mutableStateListOf<DayBlock>().apply {
            // Persona defaults only until the person has a saved routine; an emptied routine stays empty.
            addAll(if (s.routine.isBlank()) Routine.defaults(s.persona.ifEmpty { "Working employee" }) else Routine.decode(s.routine))
        }
    }
    val tick = UiTick.resume.intValue
    val nowMin = remember(tick) { LocalTime.now().let { it.hour * 60 + it.minute } }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }   // name of the open routine
    var scrollTo by remember { mutableStateOf<String?>(null) }
    var timeEdit by remember { mutableStateOf<Triple<String, Boolean, Int>?>(null) }   // block name, isStart, minutes
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val sorted = Routine.sortedByStart(blocks.toList())

    fun save() {
        s.routine = if (blocks.isEmpty()) "-" else Routine.encode(blocks.toList())
        com.example.focusflow.system.ReminderScheduler.reschedule(ctx)
        onChanged()
    }
    fun startFocus(b: DayBlock) {
        // The routine is looked up again by name inside FocusService, so its saved blocking settings are used.
        val msg = when (RoutineFocus.startManual(ctx, b)) {
            ManualStart.STARTED -> { onStartFocus(); null }
            ManualStart.ALREADY_RUNNING -> { onStartFocus(); "A focus session is already running." }
            ManualStart.FOCUS_OFF -> "Turn on focus mode for ${b.name} first."
            ManualStart.NO_DURATION -> "${b.name} has no length. Set a start and end time first."
            ManualStart.NOT_ALLOWED -> "Android did not allow focus to start. Open AMU and try again."
        }
        if (msg != null) android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
    fun update(old: DayBlock, new: DayBlock) { val i = blocks.indexOf(old); if (i >= 0) { blocks[i] = new; save() } }
    fun addBlock() {
        var name = "New routine"; var n = 2
        while (blocks.any { it.name.equals(name, ignoreCase = true) }) { name = "New routine $n"; n++ }
        val last = blocks.filter { it.start < it.end }.maxOfOrNull { it.end }
        val start = if (last == null) 9 * 60 else last.coerceAtMost(22 * 60)
        blocks.add(DayBlock(name, start, start + 60, 25, true, focusOn = true))
        save()
        expanded = name
        scrollTo = name
    }

    LaunchedEffect(scrollTo) {
        scrollTo?.let { n ->
            val i = sorted.indexOfFirst { it.name == n }
            if (i >= 0) listState.animateScrollToItem(i)
            scrollTo = null
        }
    }

    Box(modifier) {
        if (sorted.isEmpty()) {
            EmptyState(
                Icons.Rounded.Schedule, "No routine blocks yet", "Add your first block with the button below.",
                Modifier.align(Alignment.Center)
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(sorted, key = { it.name }) { b ->
                val isOpen = expanded == b.name
                val now = Routine.at(listOf(b), nowMin) != null
                val angle by animateFloatAsState(if (isOpen) 180f else 0f, tween(200), label = "routineChevron")
                Column(Modifier.fillMaxWidth().animateItem().card()) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(
                                onClickLabel = if (isOpen) "Collapse ${b.name}" else "Expand ${b.name}",
                                role = Role.Button
                            ) { focus.clearFocus(); expanded = if (isOpen) null else b.name }
                            .semantics(mergeDescendants = true) { stateDescription = if (isOpen) "Expanded" else "Collapsed" }
                            .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                b.name + if (now) "  ·  now" else "", style = MaterialTheme.typography.titleMedium,
                                color = if (now) cs.primary else cs.onSurface
                            )
                            Text(
                                "${clockText(b.start)} – ${clockText(b.end)}  ·  " + summary(b),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Rounded.ExpandMore, null, tint = cs.onSurfaceVariant, modifier = Modifier.rotate(angle))
                    }
                    AnimatedVisibility(
                        visible = isOpen,
                        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
                    ) {
                        CompositionLocalProvider(LocalFlatRows provides true) {
                            RoutineEditor(
                                b, blocks.toList(),
                                onUpdate = { new -> update(b, new) },
                                onRename = { n -> if (expanded == b.name) expanded = n; update(b, b.copy(name = n)) },
                                onPickTime = { isStart -> timeEdit = Triple(b.name, isStart, if (isStart) b.start else b.end) },
                                onStartFocus = { startFocus(b) },
                                onDelete = { blocks.remove(b); save(); expanded = null }
                            )
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { addBlock() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp),
            containerColor = cs.primary, contentColor = cs.onPrimary, shape = FFShapes.card
        ) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add Routine Block", style = MaterialTheme.typography.labelLarge)
        }
    }

    timeEdit?.let { (name, isStart, m) ->
        TimeDialog(if (isStart) "Start time" else "End time", m, 15, { v ->
            blocks.firstOrNull { it.name == name }?.let { b -> update(b, if (isStart) b.copy(start = v) else b.copy(end = v)) }
            timeEdit = null
        }, { timeEdit = null })
    }
}

private fun summary(b: DayBlock): String =
    if (!b.focusOn) "Focus off"
    else (if (b.blockOn) "Blocking on" else "No blocking") + if (b.autoFocus) " · focus all routine" else " · task focus"

@Composable
private fun RoutineEditor(
    b: DayBlock, all: List<DayBlock>,
    onUpdate: (DayBlock) -> Unit, onRename: (String) -> Unit,
    onPickTime: (Boolean) -> Unit, onStartFocus: () -> Unit, onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var draft by remember(b.name) { mutableStateOf(b.name) }
    var confirmDelete by remember(b.name) { mutableStateOf(false) }
    val taken = all.any { it.name.equals(draft.trim(), ignoreCase = true) && it.name != b.name }
    fun commitName() {
        val n = draft.trim()
        if (n.isEmpty() || taken) draft = b.name else if (n != b.name) onRename(n)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PillField(
            draft, { draft = it }, "Routine name", { commitName() },
            Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) commitName() }
        )
        if (taken) Text("Another routine already uses this name.", style = MaterialTheme.typography.bodySmall, color = cs.error)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("From", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            FFChip(clockText(b.start), onClick = { onPickTime(true) })
            Text("to", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            FFChip(clockText(b.end), onClick = { onPickTime(false) })
        }

        RoutineSwitch("Focus mode", "Off: no focus sessions and no app restrictions during ${b.name}.", b.focusOn) { onUpdate(b.copy(focusOn = it)) }
        if (b.focusOn) {
            RoutineSwitch("Focus for the whole routine", "One session from start to end. Off: focus starts at each listed task.", b.autoFocus) { onUpdate(b.copy(autoFocus = it)) }
            RoutineSwitch("Block distracting apps", "Covers apps you chose as distracting while this routine runs.", b.blockOn) { onUpdate(b.copy(blockOn = it)) }
            SecondaryButton(
                "Start Focus for This Routine", onClick = onStartFocus,
                modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow
            )
        }
        Spacer(Modifier.height(4.dp))
        DestructiveButton(
            if (confirmDelete) "Tap again to delete ${b.name}" else "Delete routine",
            onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RoutineSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}
