package com.example.focusflow.ui.home

import com.example.focusflow.ui.components.ProfileAvatar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.focusflow.UiTick
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.domain.FocusRules
import com.example.focusflow.domain.TaskRules
import com.example.focusflow.ui.Chip
import com.example.focusflow.ui.components.CategoryChip
import com.example.focusflow.ui.components.FFCard
import com.example.focusflow.ui.components.FocusFlowWordmark
import com.example.focusflow.ui.components.PrimaryButton
import com.example.focusflow.ui.components.PriorityChip
import com.example.focusflow.ui.components.ProgressRing
import com.example.focusflow.ui.components.SecondaryButton
import com.example.focusflow.ui.components.TaskRow
import com.example.focusflow.ui.focus.FocusViewModel
import com.example.focusflow.ui.formatHm
import com.example.focusflow.ui.formatMinutes
import com.example.focusflow.ui.theme.FFType
import com.example.focusflow.ui.tasks.TasksViewModel
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(
    vm: TasksViewModel, focusVm: FocusViewModel,
    onOpen: (Long) -> Unit, onProfile: () -> Unit, onReview: () -> Unit, onRoutine: () -> Unit,
    onSeeAll: () -> Unit, onAdd: () -> Unit, onStartFocus: (Long?) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val tasks by vm.tasks.collectAsState()
    val sessions by focusVm.sessions.collectAsState()
    val tick = UiTick.resume.intValue
    val today = remember(tick) { LocalDate.now() }
    val hour = remember(tick) { LocalTime.now().hour }
    val name = remember(tick) { ServiceLocator.settings(ctx).userName }
    val photoV = remember(tick) { ServiceLocator.settings(ctx).photoVersion }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val (done, total) = remember(tasks, today) { TaskRules.todayProgress(tasks, today) }
    val fs = remember(sessions, today) { FocusRules.statsFor(sessions, today) }
    val focusTask = remember(tasks, today) { TaskRules.nextFocusTask(tasks, today) }
    val upcoming = remember(tasks, today, focusTask) { TaskRules.upcomingScheduled(tasks, today, focusTask?.id, 4) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // A. Top bar: logo and wordmark, profile button
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            FocusFlowWordmark(Modifier.weight(1f))
            ProfileAvatar(
                44.dp, name, photoV,
                Modifier.semantics { contentDescription = "Profile and settings" }.clickable(onClick = onProfile)
            )
        }

        // B. Greeting
        Spacer(Modifier.height(20.dp))
        Text(
            greeting + if (name.isNotBlank()) ", $name" else "",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(2.dp))
        Text("Stay focused. You've got this!", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)

        // Now / next block of the day's routine
        val blocks = remember(tick) { com.example.focusflow.domain.Routine.decode(ServiceLocator.settings(ctx).routine) }
        val nowMin = remember(tick) { LocalTime.now().let { it.hour * 60 + it.minute } }
        val cur = com.example.focusflow.domain.Routine.at(blocks, nowMin)
        val nxt = blocks.filter { it.start > nowMin }.minByOrNull { it.start }
        if (blocks.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            FFCard(Modifier.fillMaxWidth(), onClick = onRoutine) {
                Text(if (cur != null) "Now" else "Next", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                val b = cur ?: nxt
                Text(
                    if (b != null) "${b.name}  ·  ${com.example.focusflow.ui.clockText(b.start)}–${com.example.focusflow.ui.clockText(b.end)}" else "Nothing more today",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // C. Daily progress
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FFCard(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProgressRing(if (total == 0) 0f else done.toFloat() / total, ringSize = 84.dp, stroke = 9.dp) {
                        Text("$done/$total", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Tasks completed", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            FFCard(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(formatHm(fs.sec / 60), style = FFType.metric)
                    Spacer(Modifier.height(4.dp))
                    Text("Focus time", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text(
                        "${fs.sessions} session${if (fs.sessions == 1) "" else "s"} today",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
            }
        }

        // D. Today's focus
        Spacer(Modifier.height(24.dp))
        Text("Today's Focus", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (focusTask != null) {
            FFCard(Modifier.fillMaxWidth(), onClick = { onOpen(focusTask.id) }) {
                Text(focusTask.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PriorityChip(focusTask.priority)
                    CategoryChip(focusTask.category)
                    focusTask.estimateMin?.let {
                        Text(formatMinutes(it), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    "Start focus session", onClick = { onStartFocus(focusTask.id) },
                    modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow
                )
            }
        } else {
            FFCard(Modifier.fillMaxWidth()) {
                Text("Nothing planned for today.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text("Add a task to decide what to focus on.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Add task", onClick = onAdd, modifier = Modifier.fillMaxWidth())
            }
        }

        // E. Upcoming tasks
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Upcoming", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
        Spacer(Modifier.height(4.dp))
        if (upcoming.isEmpty()) {
            Text("No upcoming tasks.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                upcoming.forEach { t ->
                    TaskRow(t, today, onToggle = { vm.toggle(t) }, onOpen = { onOpen(t.id) }, compact = true)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
