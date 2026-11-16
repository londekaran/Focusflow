package com.example.focusflow.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.focusflow.data.Task
import com.example.focusflow.domain.CalEvent
import com.example.focusflow.domain.DayTimeline
import com.example.focusflow.domain.MinRange
import com.example.focusflow.domain.PlanRules
import com.example.focusflow.domain.Schedule
import com.example.focusflow.domain.TaskRules
import com.example.focusflow.domain.TimelineItem
import com.example.focusflow.ui.SectionTitle
import com.example.focusflow.ui.card
import com.example.focusflow.ui.clockText
import com.example.focusflow.ui.components.FFCard
import com.example.focusflow.ui.components.FFChip
import com.example.focusflow.ui.components.LinearProgress
import com.example.focusflow.ui.components.PriorityChip
import com.example.focusflow.ui.components.SecondaryButton
import com.example.focusflow.ui.components.TaskCheckbox
import com.example.focusflow.ui.components.categoryColor
import com.example.focusflow.ui.formatHm
import com.example.focusflow.ui.formatMinutes
import com.example.focusflow.ui.tasks.TaskCard
import com.example.focusflow.ui.tasks.TasksViewModel
import com.example.focusflow.ui.theme.FF
import com.example.focusflow.ui.theme.FFShapes
import java.time.LocalDate
import java.time.ZoneId

private fun span(r: MinRange) = "${clockText(r.start)}–${clockText(r.end % 1440)}"

/** The daily planner: capacity, overload warning, a vertical timeline, unscheduled tasks and free time. */
@Composable
internal fun DailyPlanner(
    day: LocalDate, today: LocalDate, tasks: List<Task>, events: List<CalEvent>, calendarOn: Boolean,
    nowMin: Int, workStart: Int, workEnd: Int, vm: TasksViewModel,
    onOpen: (Long) -> Unit, onMove: (List<Task>) -> Unit, onSelect: (LocalDate) -> Unit, onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val load = remember(tasks, day, today, nowMin, workStart, workEnd) { PlanRules.load(tasks, day, today, nowMin, workStart, workEnd) }
    val timeline = remember(tasks, events, day) { PlanRules.dayTimeline(tasks, events, day) }
    val onDay = remember(tasks, day) { tasks.filter { it.date == day.toString() } }
    val scheduled = onDay.count { it.startMin != null }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DateNavRow(dayLabel(day, today), { onSelect(day.minusDays(1)) }, { onSelect(day.plusDays(1)) })

        // Capacity
        FFCard(Modifier.fillMaxWidth()) {
            Text(
                if (onDay.isEmpty()) "No tasks planned yet" else "$scheduled of ${onDay.size} tasks planned",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            if (day >= today) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Workload", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Text(formatHm(load.remainingMin), style = MaterialTheme.typography.titleSmall)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (day == today) "Left today" else "Available", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Text(formatHm(load.capacityMin), style = MaterialTheme.typography.titleSmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgress(fraction(load), color = if (load.overByMin > 0) FF.colors.warning else cs.primary)
                if (load.unestimated > 0) {
                    Spacer(Modifier.height(10.dp))
                    val n = load.unestimated
                    Text(
                        "$n task${if (n == 1) "" else "s"} without an estimate ${if (n == 1) "isn't" else "aren't"} counted",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
            } else {
                Text("${load.doneCount} of ${load.taskCount} tasks done", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }

        // Overbooking warning
        if (load.overByMin > 0) {
            Row(
                Modifier.fillMaxWidth().clip(FFShapes.card).background(FF.colors.warningContainer).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, null, tint = FF.colors.warning)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Your schedule is overloaded by ${formatMinutes(load.overByMin)}.", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (day == today) "Only ${formatMinutes(load.capacityMin)} is left in your day. Consider moving something to another day."
                        else "Move a task to another day or shorten an estimate.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
            }
        }

        // Unfinished from earlier days: moved only when you choose
        if (day == today) {
            val overdue = TaskRules.sortForDisplay(TaskRules.overdueList(tasks, today))
            if (overdue.isNotEmpty()) {
                SectionTitle("Unfinished from earlier  ·  ${overdue.size}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FFChip("Move all to today", onClick = { vm.reschedule(overdue, today) })
                    FFChip("Move all to…", onClick = { onMove(overdue) })
                }
                overdue.forEach { t -> PlanRow(t, today, vm, onOpen, onMove) }
            }
        }

        // Timeline
        SectionTitle("Timeline")
        timeline.allDay.forEach {
            Text("All day  ·  $it", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (timeline.items.isEmpty()) {
            Text(
                "Nothing has a time yet. Give tasks a start time, or use the suggestions below.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
            )
        } else {
            TimelineList(timeline, vm, onOpen)
        }

        if (timeline.unscheduled.isNotEmpty()) {
            SectionTitle("Not scheduled yet  ·  ${timeline.unscheduled.size}")
            timeline.unscheduled.forEach { t -> PlanRow(t, today, vm, onOpen, onMove) }
        }

        if (day >= today) {
            SectionTitle("Free time")
            FreeTimeCard(day, today, tasks, events, calendarOn, nowMin, workStart, workEnd, vm, onConnect, showSuggestions = true)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** A task row with a button to reschedule it. */
@Composable
internal fun PlanRow(t: Task, today: LocalDate, vm: TasksViewModel, onOpen: (Long) -> Unit, onMove: (List<Task>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { TaskCard(t, today, onToggle = { vm.toggle(t) }, onOpen = { onOpen(t.id) }) }
        if (!t.done) {
            Icon(
                Icons.Rounded.DateRange, "Reschedule", tint = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(48.dp).clip(CircleShape).clickable { onMove(listOf(t)) }.padding(12.dp)
            )
        }
    }
}

/** The day's timeline: time on the left, a line with a coloured dot, then the task or event. */
@Composable
internal fun TimelineList(timeline: DayTimeline, vm: TasksViewModel, onOpen: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        timeline.items.forEach { item -> TimelineRow(item, vm, onOpen) }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem, vm: TasksViewModel, onOpen: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val task = item.task
    val dot = if (task != null) categoryColor(task.category) else FF.colors.info
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(48.dp).padding(top = 12.dp)) {
            Text(clockText(item.startMin), style = MaterialTheme.typography.titleSmall)
            Text(clockText(item.endMin % 1440), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(cs.outlineVariant))
            Box(Modifier.padding(top = 16.dp).size(10.dp).clip(CircleShape).background(dot))
        }
        Box(Modifier.weight(1f).padding(bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().card()
                    .then(if (task != null) Modifier.clickable { onOpen(task.id) } else Modifier)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = if (task != null) 0.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title, style = MaterialTheme.typography.titleSmall,
                        color = if (task?.done == true) cs.onSurfaceVariant else cs.onSurface,
                        textDecoration = if (task?.done == true) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    if (task != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PriorityChip(task.priority)
                            Text(
                                task.category + "  ·  " + formatMinutes(task.estimateMin ?: Schedule.DEFAULT_TASK_MIN),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1
                            )
                        }
                    } else {
                        Text("Calendar event", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                if (task != null) TaskCheckbox(task.done, onToggle = { vm.toggle(task) })
            }
        }
    }
}

/** Free slots inside your work hours, schedule conflicts, and (optionally) suggested start times. */
@Composable
internal fun FreeTimeCard(
    day: LocalDate, today: LocalDate, tasks: List<Task>, events: List<CalEvent>, calendarOn: Boolean,
    nowMin: Int, workStart: Int, workEnd: Int, vm: TasksViewModel, onConnect: (() -> Unit)?, showSuggestions: Boolean
) {
    val cs = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    val pending = tasks.filter { it.date == day.toString() && !it.done }
    val busy = Schedule.eventRangesOn(events, day, zone) + pending.mapNotNull { Schedule.taskRange(it) }
    val free = Schedule.freeSlots(workStart, workEnd, busy, if (day == today) nowMin else workStart)
    val suggestions = if (showSuggestions) Schedule.suggest(pending, free) else emptyMap()
    val conflicts = Schedule.conflicts(pending, events, day, zone)
    val titleOf = pending.associate { it.id to it.title }

    FFCard(Modifier.fillMaxWidth()) {
        if (free.isEmpty()) {
            Text("No free time left in your day.", style = MaterialTheme.typography.bodyMedium)
        } else {
            free.take(4).forEach {
                Text(
                    "${span(it)}  (${formatMinutes(it.len)})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        conflicts.forEach {
            Spacer(Modifier.height(6.dp))
            Text(
                "“${it.first}” overlaps “${it.second}” (${span(it.overlap)})",
                style = MaterialTheme.typography.bodySmall, color = cs.error
            )
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Suggested start times", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            suggestions.entries.take(4).forEach { (id, start) ->
                Text("${clockText(start)}  ${titleOf[id] ?: ""}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
            val n = suggestions.size
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Schedule $n task${if (n == 1) "" else "s"}", onClick = { vm.setStarts(suggestions) }, modifier = Modifier.fillMaxWidth())
        }
        if (!calendarOn && onConnect != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Your calendar isn't connected. AMU can read event titles and times (read-only, only on this phone) " +
                    "to show your day and find free time. Planning works without it.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Show my calendar", onClick = onConnect, modifier = Modifier.fillMaxWidth(), neutral = true)
        }
    }
}
