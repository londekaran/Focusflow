package com.example.focusflow.ui.plan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.absoluteValue
import kotlin.math.sign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.focusflow.Route
import com.example.focusflow.UiTick
import com.example.focusflow.data.Task
import com.example.focusflow.domain.PlanRules
import com.example.focusflow.system.CalendarReader
import com.example.focusflow.ui.IconAction
import com.example.focusflow.ui.PillField
import com.example.focusflow.ui.RoundButton
import com.example.focusflow.ui.components.FFChip
import com.example.focusflow.ui.components.SegmentedSelector
import com.example.focusflow.ui.tasks.TasksViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

private val MODES = listOf("Tasks", "Day", "Week", "Month", "Routine")

/** The Plan tab: Daily, Weekly and Calendar views of the same tasks. */
@Composable
fun PlanScreen(
    vm: TasksViewModel, planVm: PlanViewModel, onOpen: (Long) -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit,
    onStartRoutineFocus: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val tasks by vm.tasks.collectAsState()
    val moves by vm.recentMoves.collectAsState()
    val workStart by planVm.workStart.collectAsState()
    val workEnd by planVm.workEnd.collectAsState()
    val events by planVm.events.collectAsState()
    val monthEvents by planVm.monthEvents.collectAsState()
    val calendarOn by planVm.calendarOn.collectAsState()
    val tick = UiTick.resume.intValue
    val today = remember(tick) { LocalDate.now() }
    val nowMin = remember(tick) { LocalTime.now().let { it.hour * 60 + it.minute } }
    var mode by rememberSaveable { mutableIntStateOf(0) }   // 0 tasks, 1 day, 2 week, 3 month, 4 routine
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var text by remember { mutableStateOf("") }
    var moving by remember { mutableStateOf<List<Task>?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val day = LocalDate.parse(selected)

    val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        planVm.setCalendarOn(ok)
    }
    val connect: () -> Unit = {
        if (CalendarReader.hasPermission(ctx)) planVm.setCalendarOn(true)
        else calLauncher.launch(Manifest.permission.READ_CALENDAR)
    }

    LaunchedEffect(tick) { planVm.refreshHours() }
    LaunchedEffect(day, calendarOn, tick) { planVm.loadEvents(day) }
    LaunchedEffect(mode, YearMonth.from(day), calendarOn, tick) { if (mode == 3) planVm.loadMonth(YearMonth.from(day)) }
    LaunchedEffect(Route.planNextWeek.value) {
        if (Route.planNextWeek.value) {
            selected = PlanRules.weekStart(today.plusWeeks(1)).toString()
            mode = 2
            Route.planNextWeek.value = false
        }
    }
    LaunchedEffect(Route.planRoutine.value) {
        if (Route.planRoutine.value) { mode = 4; Route.planRoutine.value = false }
    }

    fun add() {
        val t = text.trim()
        if (t.isNotEmpty()) {
            val st = com.example.focusflow.data.ServiceLocator.settings(ctx)
            val blocks = com.example.focusflow.domain.Routine.decode(st.routine)
            val now = LocalTime.now().let { it.hour * 60 + it.minute }
            vm.addForDate(t, day, com.example.focusflow.domain.Routine.currentName(blocks, now) ?: "General")
            text = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (mode == 4) "My day" else "Plan", Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.headlineMedium)
            if (day != today) FFChip("Today", onClick = { selected = today.toString() })
            IconAction(Icons.Rounded.History, "Rescheduling history") { showHistory = true }
        }
        SegmentedSelector(MODES, mode, { mode = it })
        Spacer(Modifier.height(12.dp))

        // Swipe left/right to move between Tasks, Day, Week, Month and Routine. Only horizontal drags are taken,
        // and children (vertical lists, the filter chip row) get the gesture first, so scrolling is unaffected.
        val density = LocalDensity.current
        val swipeAt = with(density) { 72.dp.toPx() }
        var dragX by remember { mutableFloatStateOf(0f) }
        val swipeState = rememberDraggableState { dragX += it }
        Box(
            Modifier.weight(1f).fillMaxWidth().draggable(
                state = swipeState, orientation = Orientation.Horizontal,
                onDragStarted = { dragX = 0f },
                onDragStopped = { v ->
                    val go = dragX.absoluteValue > swipeAt || v.absoluteValue > 1800f
                    if (go) {
                        val dir = if (dragX != 0f) -sign(dragX).toInt() else -sign(v).toInt()
                        mode = (mode + dir).coerceIn(0, MODES.lastIndex)
                    }
                    dragX = 0f
                }
            )
        ) {
            AnimatedContent(
                targetState = mode,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val fwd = targetState > initialState
                    (slideInHorizontally(tween(220)) { if (fwd) it / 5 else -it / 5 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(160)) { if (fwd) -it / 5 else it / 5 } + fadeOut(tween(120)))
                },
                label = "planMode"
            ) { m ->
                when (m) {
                    0 -> com.example.focusflow.ui.tasks.TasksScreen(vm, onOpen, onAdd, onEdit, showTitle = false)
                    1 -> DailyPlanner(
                        day, today, tasks, events, calendarOn, nowMin, workStart, workEnd, vm,
                        onOpen = onOpen, onMove = { moving = it }, onSelect = { selected = it.toString() },
                        onConnect = connect, modifier = Modifier.fillMaxSize()
                    )
                    2 -> WeeklyPlanner(
                        day, today, tasks, nowMin, workStart, workEnd,
                        onSelect = { selected = it.toString() },
                        onOpenDay = { selected = it.toString(); mode = 1 },
                        modifier = Modifier.fillMaxSize()
                    )
                    4 -> RoutineTab(Modifier.fillMaxSize(), onChanged = { planVm.refreshHours() }, onStartFocus = onStartRoutineFocus)
                    else -> CalendarTab(
                        day, today, tasks, events, monthEvents, calendarOn, nowMin, workStart, workEnd, vm,
                        onOpen = onOpen, onSelect = { selected = it.toString() }, onConnect = connect,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (mode == 1) {
            Row(Modifier.fillMaxWidth().imePadding().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                PillField(text, { text = it }, "Add a task for ${dayLabel(day, today).lowercase()}", { add() }, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                RoundButton(Icons.Rounded.Add, "Add") { add() }
            }
        }
    }

    moving?.let { list ->
        RescheduleDialog(
            count = list.size, today = today, currentStart = if (list.size == 1) list[0].startMin else null,
            onConfirm = { d, s, changed -> vm.reschedule(list, d, s, changed); moving = null },
            onDismiss = { moving = null }
        )
    }
    if (showHistory) {
        HistorySheet(moves, tasks, onOpen = { showHistory = false; onOpen(it) }, onDismiss = { showHistory = false })
    }
}
