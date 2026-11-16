package com.example.focusflow.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focusflow.data.Task
import com.example.focusflow.domain.CalEvent
import com.example.focusflow.domain.PlanRules
import com.example.focusflow.ui.IconAction
import com.example.focusflow.ui.SectionTitle
import com.example.focusflow.ui.components.FFCard
import com.example.focusflow.ui.components.FFChip
import com.example.focusflow.ui.components.SecondaryButton
import com.example.focusflow.ui.tasks.TasksViewModel
import com.example.focusflow.ui.theme.FF
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** A month grid with markers for tasks and calendar events, and the selected day's schedule below it. */
@Composable
internal fun CalendarTab(
    day: LocalDate, today: LocalDate, tasks: List<Task>, events: List<CalEvent>, monthEvents: List<CalEvent>,
    calendarOn: Boolean, nowMin: Int, workStart: Int, workEnd: Int, vm: TasksViewModel,
    onOpen: (Long) -> Unit, onSelect: (LocalDate) -> Unit, onConnect: () -> Unit, modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val month = YearMonth.from(day)
    val cells = remember(month) { PlanRules.monthCells(month) }
    val pendingByDate = remember(tasks) { tasks.groupBy { it.date }.mapValues { (_, l) -> l.any { !it.done } } }
    val eventDays = remember(monthEvents) { PlanRules.eventDates(monthEvents) }
    val timeline = remember(tasks, events, day) { PlanRules.dayTimeline(tasks, events, day) }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!calendarOn) {
            FFCard(Modifier.fillMaxWidth()) {
                Text("Calendar events are off", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Allow calendar access to see your events next to your tasks and to find free time. " +
                        "AMU only reads event titles and times, on this phone. Your tasks and plans work without it.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Allow calendar access", onClick = onConnect, modifier = Modifier.fillMaxWidth())
            }
        }

        // Month header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.Rounded.ChevronLeft, "Previous month") { onSelect(day.minusMonths(1)) }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium
            )
            IconAction(Icons.Rounded.ChevronRight, "Next month") { onSelect(day.plusMonths(1)) }
        }
        if (month != YearMonth.from(today)) FFChip("Today", onClick = { onSelect(today) })

        // Grid
        FFCard(Modifier.fillMaxWidth(), padding = 8.dp) {
            Row(Modifier.fillMaxWidth()) {
                WEEKDAYS.forEach {
                    Text(
                        it, Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        if (d == null) {
                            Spacer(Modifier.weight(1f).height(52.dp))
                        } else {
                            DayCell(
                                d, selected = d == day, isToday = d == today,
                                hasTasks = pendingByDate.containsKey(d.toString()),
                                hasPending = pendingByDate[d.toString()] == true,
                                hasEvents = d in eventDays,
                                modifier = Modifier.weight(1f)
                            ) { onSelect(d) }
                        }
                    }
                }
            }
        }

        // Selected date schedule
        SectionTitle(dayLabel(day, today))
        timeline.allDay.forEach {
            Text("All day  ·  $it", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (timeline.items.isEmpty()) {
            Text(
                if (timeline.unscheduled.isEmpty()) "Nothing planned for this day."
                else "No tasks or events with a time on this day.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
            )
        } else {
            TimelineList(timeline, vm, onOpen)
        }
        if (timeline.unscheduled.isNotEmpty()) {
            val n = timeline.unscheduled.size
            Text(
                "$n task${if (n == 1) "" else "s"} without a time: " + timeline.unscheduled.take(3).joinToString(", ") { it.title } +
                    if (n > 3) " and ${n - 3} more" else "",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }

        // Free slots
        if (day >= today) {
            SectionTitle("Free slots")
            FreeTimeCard(day, today, tasks, events, calendarOn, nowMin, workStart, workEnd, vm, onConnect = null, showSuggestions = false)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DayCell(
    d: LocalDate, selected: Boolean, isToday: Boolean, hasTasks: Boolean, hasPending: Boolean, hasEvents: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val description = d.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) +
        (if (hasTasks) ", has tasks" else "") + (if (hasEvents) ", has events" else "")
    Column(
        modifier.height(52.dp).clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .then(
                    when {
                        selected -> Modifier.background(cs.primary)
                        isToday -> Modifier.border(1.5.dp, cs.primary, CircleShape)
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${d.dayOfMonth}", style = MaterialTheme.typography.bodyMedium,
                color = if (selected) cs.onPrimary else cs.onSurface
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(5.dp)) {
            if (hasTasks) Box(Modifier.size(5.dp).clip(CircleShape).background(if (hasPending) cs.primary else cs.outline))
            if (hasEvents) Box(Modifier.size(5.dp).clip(CircleShape).background(FF.colors.info))
        }
    }
}
