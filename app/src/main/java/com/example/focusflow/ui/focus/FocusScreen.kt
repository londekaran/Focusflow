package com.example.focusflow.ui.focus

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.example.focusflow.FocusService
import com.example.focusflow.FocusState
import com.example.focusflow.Route
import com.example.focusflow.UiTick
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.data.Task
import com.example.focusflow.domain.Analytics
import com.example.focusflow.domain.FocusRules
import com.example.focusflow.domain.Routine
import com.example.focusflow.domain.TaskRules
import com.example.focusflow.hasUsageAccess
import com.example.focusflow.hasOverlayAccess
import com.example.focusflow.ui.IconAction
import com.example.focusflow.ui.SwitchCard
import com.example.focusflow.ui.components.CategoryChip
import com.example.focusflow.ui.components.FFCard
import com.example.focusflow.ui.components.FFChip
import com.example.focusflow.ui.components.PrimaryButton
import com.example.focusflow.ui.components.PriorityChip
import com.example.focusflow.ui.components.SecondaryButton
import com.example.focusflow.ui.components.StepperRow
import com.example.focusflow.ui.distraction.DistractionViewModel
import com.example.focusflow.ui.formatHm
import com.example.focusflow.ui.formatMinutes
import com.example.focusflow.ui.theme.FFType
import kotlinx.coroutines.delay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    tasks: List<Task>,
    focusVm: FocusViewModel,
    distractionVm: DistractionViewModel,
    onOpenDistractions: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val running by FocusState.running.collectAsState()
    val autoBlock by FocusState.blockName.collectAsState()
    val origin by FocusState.origin.collectAsState()
    val paused by FocusState.paused.collectAsState()
    val remaining by FocusState.remainingSec.collectAsState()
    val total by FocusState.totalSec.collectAsState()
    val activeTaskId by FocusState.taskId.collectAsState()
    val distractions by FocusState.distractions.collectAsState()
    val inBreak by FocusState.breakRunning.collectAsState()
    val breakLeft by FocusState.breakRemainingSec.collectAsState()
    val breakTotal by FocusState.breakTotalSec.collectAsState()
    val breakTip by FocusState.breakTip.collectAsState()
    val minutes by focusVm.focusMin.collectAsState()
    val sessions by focusVm.sessions.collectAsState()
    val interruptions by distractionVm.interruptions.collectAsState()
    val apps by distractionVm.apps.collectAsState()
    val monitoring by distractionVm.monitoring.collectAsState()
    val live by distractionVm.liveAlerts.collectAsState()
    val block by distractionVm.blockApps.collectAsState()
    var selectedTask by rememberSaveable { mutableLongStateOf(-1L) }
    // A length picked by hand for the chosen task (null = follow the task's estimate, else the default).
    var manualMin by rememberSaveable { mutableStateOf<Int?>(null) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var wasRunning by remember { mutableStateOf(running) }
    var showPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Home and Task Details can ask Focus to pre-select a task.
    val preselect = Route.focusTask.value
    LaunchedEffect(preselect) {
        if (preselect != null) {
            selectedTask = preselect
            Route.focusTask.value = null
        }
    }

    // Picking a different task forgets any manual length chosen for the previous one (never touches a running timer).
    LaunchedEffect(selectedTask) { manualMin = null }

    // Show the session report shortly after a session ends (the usage log is read when it ends).
    LaunchedEffect(running) {
        if (wasRunning && !running) {
            delay(1200)
            showReport = true
        }
        wasRunning = running
    }

    val tick = UiTick.resume.intValue
    val access = remember(tick) { hasUsageAccess(ctx) }
    val today = remember(tick) { LocalDate.now() }
    val breakMin = remember(tick, showSettings) { focusVm.breakMin() }
    val fs = remember(sessions, today) { FocusRules.statsFor(sessions, today) }
    val interruptionsToday = remember(interruptions, today) { Analytics.interruptionsInRange(interruptions, today, today).size }
    val pickable = remember(tasks, today) {
        (TaskRules.sortForDisplay(TaskRules.overdueList(tasks, today) + TaskRules.todayList(tasks, today)) +
                TaskRules.upcomingList(tasks, today).sortedBy { it.date }).take(30)
    }
    val chosen = tasks.firstOrNull { it.id == selectedTask && !it.done }
    // The task's estimated duration drives the timer; the saved default is never overwritten by it.
    val taskMin = chosen?.estimateMin?.takeIf { it in 1..600 }
    val setMin = manualMin ?: taskMin ?: minutes
    val shownTask = if (running) tasks.firstOrNull { it.id == activeTaskId } else chosen
    val trackingActive = monitoring && access && apps.isNotEmpty()

    val ringLeft = if (inBreak) breakLeft else remaining
    val ringTotal = if (inBreak) breakTotal else total
    val frac = if ((running || inBreak) && ringTotal > 0) (ringLeft / ringTotal.toFloat()).coerceIn(0f, 1f) else 0f

    fun applyMin(m: Int) {
        if (taskMin != null) manualMin = m   // task has its own length: don't change the saved default
        else { manualMin = null; focusVm.setFocusMin(m) }
    }

    fun send(action: String, extra: (Intent) -> Unit = {}) {
        val i = Intent(ctx, FocusService::class.java).setAction(action)
        extra(i)
        if (action == FocusService.ACTION_START || action == FocusService.ACTION_BREAK_START) ctx.startForegroundService(i)
        else ctx.startService(i)
    }

    val trackingText = when {
        !monitoring -> "Distraction tracking is off"
        !access -> "Allow usage access to track distractions"
        apps.isEmpty() -> "Choose which apps distract you"
        else -> "Tracking ${apps.size} app${if (apps.size == 1) "" else "s"}" +
                if (block && hasOverlayAccess(ctx)) "  ·  blocking on" else if (live) "  ·  live reminders on" else ""
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Top: title, subtitle and focus settings
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 12.dp)) {
                Text("Focus", style = MaterialTheme.typography.headlineMedium)
            }
            IconAction(Icons.Rounded.Tune, "Focus settings") { showSettings = true }
        }
        Spacer(Modifier.height(16.dp))

        // A. Active task card (or the break tip during a break)
        if (inBreak) {
            FFCard(Modifier.fillMaxWidth()) {
                Text("Break time", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(breakTip, style = MaterialTheme.typography.titleMedium)
            }
        } else if (running && shownTask != null) {
            Text("Working on: ${shownTask.title}", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else if (running && autoBlock != null) {
            Text(
                if (origin == com.example.focusflow.system.RoutineFocus.ORIGIN_MANUAL) "$autoBlock focus" else "$autoBlock focus · automatic",
                style = MaterialTheme.typography.titleMedium
            )
        }
        // Blocking needs two Android permissions; say so instead of failing silently.
        val fixCtx = androidx.compose.ui.platform.LocalContext.current
        val fixTick = com.example.focusflow.UiTick.resume.intValue
        val noUsage = remember(fixTick) { !com.example.focusflow.hasUsageAccess(fixCtx) }
        val noOverlay = remember(fixTick) { !com.example.focusflow.system.BlockOverlay.canShow(fixCtx) }
        if (noUsage || noOverlay) {
            Spacer(Modifier.height(12.dp))
            FFCard(Modifier.fillMaxWidth()) {
                Text("App blocking is not fully on", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (noUsage) "Allow Usage access so AMU can see which app is open." else "Allow Display over other apps so AMU can cover blocked apps.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton({
                    val i = if (noUsage) android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    else android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${fixCtx.packageName}"))
                    fixCtx.startActivity(i)
                }) { Text(if (noUsage) "Open Usage access" else "Open overlay permission") }
            }
        }
        Spacer(Modifier.height(20.dp))

        // B. Timer and duration. Idle: timer on the left, minute selector on the right. While a session or break
        // runs the selector folds away and the same timer moves to the centre. The timer state is only observed
        // here (it lives in FocusService), so switching layout never restarts or duplicates it.
        val active = running || inBreak
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 12.dp
            val idleSize = (maxWidth - SELECTOR_WIDTH - gap).coerceIn(172.dp, TIMER_MAX)
            val activeSize = maxWidth.coerceAtMost(TIMER_MAX)
            val ringSize by animateDpAsState(
                if (active) activeSize else idleSize, tween(350, easing = FastOutSlowInEasing), label = "ringSize"
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                TimerRing(
                    size = ringSize,
                    frac = frac,
                    time = if (running || inBreak) "%02d:%02d".format(ringLeft / 60, ringLeft % 60) else "%02d:00".format(setMin),
                    label = when {
                        inBreak -> "Break"
                        !running -> "Ready"
                        paused -> "Paused"
                        else -> "Focusing"
                    },
                    pulsing = (running && !paused) || inBreak
                )
                AnimatedVisibility(
                    visible = !active,
                    enter = fadeIn(tween(250, delayMillis = 100)) + expandHorizontally(tween(350, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(150)) + shrinkHorizontally(tween(350, easing = FastOutSlowInEasing))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(gap))
                        // Scroll-style duration selector: 1 to 240 minutes (a longer saved length is still shown).
                        Column(Modifier.width(SELECTOR_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
                            com.example.focusflow.ui.WheelPicker(
                                count = maxOf(240, setMin), selected = setMin - 1, onSelect = {},
                                label = { i -> (i + 1).toString() }, rows = 3, description = "Focus minutes wheel",
                                onSettled = { i -> if (i + 1 != setMin) applyMin(i + 1) }
                            )
                            Text("min", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // C. Controls: only the ones that apply right now
        when {
            inBreak -> SecondaryButton(
                "End break", onClick = { send(FocusService.ACTION_BREAK_END) },
                modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Stop, neutral = true
            )
            running -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    if (paused) "Resume" else "Pause",
                    onClick = { send(if (paused) FocusService.ACTION_RESUME else FocusService.ACTION_PAUSE) },
                    modifier = Modifier.weight(1f),
                    icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                )
                SecondaryButton(
                    "End session", onClick = { send(FocusService.ACTION_STOP) },
                    modifier = Modifier.weight(1f), icon = Icons.Rounded.Stop, neutral = true
                )
            }
            else -> PrimaryButton(
                "Start", icon = Icons.Rounded.PlayArrow, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    send(FocusService.ACTION_START) {
                        it.putExtra("minutes", setMin)
                        it.putExtra("taskId", chosen?.id ?: -1L)
                        // A task that belongs to a routine is blocked by that routine's own rules.
                        val owner = chosen?.category?.let { c ->
                            Routine.decode(ServiceLocator.settings(ctx).routine).firstOrNull { b -> b.name == c }
                        }
                        if (owner != null) it.putExtra("block", owner.name)
                    }
                }
            )
        }
        Spacer(Modifier.height(12.dp))

        // D. Supporting actions
        if (!running && !inBreak) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(Icons.Rounded.SwapHoriz, "Task", chosen?.title ?: "Choose", Modifier.weight(1f)) { showPicker = true }
                QuickCard(Icons.Rounded.Coffee, "Take break", "$breakMin min", Modifier.weight(1f)) {
                    send(FocusService.ACTION_BREAK_START) { it.putExtra("minutes", breakMin) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // E. Session information
        if (!inBreak) {
            val stats = if (running) {
                listOf(
                    "Session" to "${total / 60} min",
                    "Focus today" to formatHm(fs.sec / 60),
                    "Interruptions" to if (trackingActive) "$distractions" else "Off"
                )
            } else {
                listOf(
                    "Focus today" to formatHm(fs.sec / 60),
                    "Sessions" to "${fs.sessions}",
                    "Interruptions" to if (trackingActive) "$interruptionsToday" else "Off"
                )
            }
            FFCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    stats.forEach { (label, value) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, style = MaterialTheme.typography.titleMedium)
                            Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!running && sessions.isNotEmpty()) {
                TextButton(onClick = { showReport = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("View last session report")
                }
            }
        }

        // Link to the Distractions screen
        if (!running && !inBreak) {
            Spacer(Modifier.height(4.dp))
            FFCard(Modifier.fillMaxWidth(), onClick = onOpenDistractions, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(trackingText, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showPicker) {
        TaskPickerSheet(
            pickable, chosen?.id,
            onPick = { selectedTask = it; showPicker = false },
            onNone = { selectedTask = -1L; showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
    if (showSettings) {
        FocusSettingsSheet(focusVm, minutes, onOpenDistractions, onDismiss = { showSettings = false })
    }

    val latest = sessions.firstOrNull()
    if (showReport && !running && latest != null) {
        SessionReportDialog(latest, interruptions.filter { it.sessionId == latest.id }) { showReport = false }
    }
}

private val TIMER_MAX = 248.dp
private val SELECTOR_WIDTH = 84.dp

/** The circular timer. The arc animates smoothly, and a soft glow breathes while a session or break is running. */
@Composable
private fun TimerRing(size: Dp, frac: Float, time: String, label: String, pulsing: Boolean) {
    val cs = MaterialTheme.colorScheme
    val arc by animateFloatAsState(frac, animationSpec = tween(1000, easing = LinearEasing), label = "arc")
    val glow = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "glow")
        val a by transition.animateFloat(
            initialValue = 0.04f, targetValue = 0.14f,
            animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glowAlpha"
        )
        a
    } else 0f
    val track = cs.outlineVariant
    val green = cs.primary
    val scale = size / TIMER_MAX   // everything inside follows the animated size
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 12.dp.toPx() * scale
            val topLeft = Offset(sw / 2, sw / 2)
            // `this.size` is the canvas Size; plain `size` would resolve to the Dp parameter of TimerRing.
            val canvasSize = this.size
            val box = Size(canvasSize.width - sw, canvasSize.height - sw)
            if (glow > 0f) drawCircle(green.copy(alpha = glow), radius = canvasSize.minDimension / 2 - sw)
            drawArc(track, 0f, 360f, false, topLeft, box, style = Stroke(sw))
            if (arc > 0.001f) drawArc(green, -90f, 360f * arc, false, topLeft, box, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                time,
                style = FFType.timer.copy(fontSize = FFType.timer.fontSize * scale, lineHeight = FFType.timer.lineHeight * scale),
                color = cs.onSurface, maxLines = 1, softWrap = false
            )
            Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickCard(icon: ImageVector, label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    FFCard(modifier, onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskPickerSheet(
    tasks: List<Task>, selectedId: Long?, onPick: (Long) -> Unit, onNone: () -> Unit, onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface
    ) {
        Text(
            "Choose a task", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            item { PickRow("No task", "Start without linking a task", selectedId == null, onNone) }
            items(tasks, key = { it.id }) { t ->
                val meta = listOfNotNull(t.category, t.estimateMin?.let { formatMinutes(it) }).joinToString("  ·  ")
                PickRow(t.title, meta, selectedId == t.id) { onPick(t.id) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PickRow(title: String, meta: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
        }
        if (selected) Icon(Icons.Rounded.Check, "Selected", tint = cs.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusSettingsSheet(focusVm: FocusViewModel, minutes: Int, onOpenDistractions: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var breakLen by remember { mutableIntStateOf(focusVm.breakMin()) }
    var breakOn by remember { mutableStateOf(focusVm.breakEnabled()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Focus settings", style = MaterialTheme.typography.titleLarge)
            StepperRow(
                "Session length", "$minutes min",
                onMinus = { focusVm.setFocusMin((minutes - 5).coerceAtLeast(5)) },
                onPlus = { focusVm.setFocusMin((minutes + 5).coerceAtMost(1440)) }
            )
            SwitchCard("Break reminders", "Suggest a short break after each finished session.", breakOn) {
                breakOn = it
                focusVm.setBreakEnabled(it)
            }
            StepperRow(
                "Break length", "$breakLen min", enabled = breakOn,
                onMinus = { breakLen = (breakLen - 1).coerceAtLeast(3); focusVm.setBreakMin(breakLen) },
                onPlus = { breakLen = (breakLen + 1).coerceAtMost(30); focusVm.setBreakMin(breakLen) }
            )
            SecondaryButton(
                "Distraction tracking", onClick = { onDismiss(); onOpenDistractions() },
                modifier = Modifier.fillMaxWidth(), neutral = true
            )
        }
    }
}