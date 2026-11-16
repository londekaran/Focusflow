package com.example.focusflow.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.data.Task
import com.example.focusflow.domain.CalEvent
import com.example.focusflow.domain.MinRange
import com.example.focusflow.domain.Schedule
import com.example.focusflow.ui.Chip
import com.example.focusflow.ui.SectionTitle
import com.example.focusflow.ui.card
import com.example.focusflow.ui.clockText
import com.example.focusflow.ui.formatMinutes
import com.example.focusflow.ui.tasks.TasksViewModel
import java.time.LocalDate
import java.time.ZoneId

private fun span(r: MinRange) = "${clockText(r.start)}–${clockText(r.end)}"

/** The day's calendar events, free time inside work hours, conflicts, and suggested start times. */
@Composable
fun ScheduleSection(
    day: LocalDate, today: LocalDate, tasks: List<Task>, events: List<CalEvent>, calendarOn: Boolean,
    nowMin: Int, workStart: Int, workEnd: Int, vm: TasksViewModel, onConnect: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    val pending = tasks.filter { it.date == day.toString() && !it.done }
    val busy = Schedule.eventRangesOn(events, day, zone) + pending.mapNotNull { Schedule.taskRange(it) }
    val free = Schedule.freeSlots(workStart, workEnd, busy, if (day == today) nowMin else workStart)
    val suggestions = Schedule.suggest(pending, free)
    val conflicts = Schedule.conflicts(pending, events, day, zone)
    val titleOf = pending.associate { it.id to it.title }

    SectionTitle("Schedule")
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!calendarOn) {
            Text(
                "Your calendar isn't connected. AMU can read event titles and times from your phone's " +
                    "calendars (read-only, and only on this phone) to show your day and find free time. " +
                    "Planning works without it.",
                fontSize = 13.sp, color = cs.onSurfaceVariant
            )
            Chip("Show my calendar") { onConnect() }
        } else {
            val allDay = events.filter { it.allDay }
            val timed = events.filter { !it.allDay }
            if (events.isEmpty()) Text("No calendar events this day.", fontSize = 14.sp, color = cs.onSurfaceVariant)
            allDay.forEach { Text("All day  ·  ${it.title}", fontSize = 14.sp) }
            timed.forEach { e ->
                val r = Schedule.eventRangesOn(listOf(e), day, zone).firstOrNull()
                Text("${r?.let { span(it) } ?: ""}  ${e.title}", fontSize = 14.sp)
            }
        }

        Text("Free time in your day", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        if (free.isEmpty()) {
            Text("No free time left.", fontSize = 14.sp)
        } else {
            free.take(4).forEach { Text("${span(it)}  (${formatMinutes(it.len)})", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }

        conflicts.forEach {
            Text("“${it.first}” overlaps “${it.second}” (${span(it.overlap)})", fontSize = 13.sp, color = cs.error)
        }

        if (suggestions.isNotEmpty()) {
            Text("Suggested start times", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            suggestions.entries.take(4).forEach { (id, start) ->
                Text("${clockText(start)}  ${titleOf[id] ?: ""}", fontSize = 14.sp)
            }
            val n = suggestions.size
            Chip("Schedule $n task${if (n == 1) "" else "s"}") { vm.setStarts(suggestions) }
        }
    }
}
