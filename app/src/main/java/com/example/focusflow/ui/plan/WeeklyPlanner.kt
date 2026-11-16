package com.example.focusflow.ui.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.focusflow.data.Task
import com.example.focusflow.domain.PlanRules
import com.example.focusflow.ui.components.FFCard
import com.example.focusflow.ui.components.FFChip
import com.example.focusflow.ui.components.LinearProgress
import com.example.focusflow.ui.formatHm
import com.example.focusflow.ui.formatMinutes
import com.example.focusflow.ui.shortDate
import com.example.focusflow.ui.theme.FF
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Seven day cards, Monday to Sunday. Tap a day to plan it. */
@Composable
internal fun WeeklyPlanner(
    day: LocalDate, today: LocalDate, tasks: List<Task>, nowMin: Int, workStart: Int, workEnd: Int,
    onSelect: (LocalDate) -> Unit, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val start = PlanRules.weekStart(day)

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DateNavRow(
            "${shortDate(start.toString())} – ${shortDate(start.plusDays(6).toString())}",
            { onSelect(day.minusWeeks(1)) }, { onSelect(day.plusWeeks(1)) }
        )
        if (PlanRules.weekStart(today) != start) FFChip("This week", onClick = { onSelect(today) })

        (0L..6L).forEach { i ->
            val d = start.plusDays(i)
            val l = PlanRules.load(tasks, d, today, nowMin, workStart, workEnd)
            val titles = tasks.filter { it.date == d.toString() && !it.done }.take(2).map { it.title }
            FFCard(Modifier.fillMaxWidth(), onClick = { onOpenDay(d) }) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.width(56.dp)) {
                        Text(d.format(DateTimeFormatter.ofPattern("EEE")), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Text(
                            "${d.dayOfMonth}", style = MaterialTheme.typography.titleLarge,
                            color = if (d == today) FF.colors.primaryText else cs.onSurface
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        val count = "${l.taskCount} task${if (l.taskCount == 1) "" else "s"}"
                        if (d >= today) {
                            Text(
                                "$count  ·  ${formatHm(l.remainingMin)} of ${formatHm(l.capacityMin)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgress(fraction(l), color = if (l.overByMin > 0) FF.colors.warning else cs.primary)
                            if (l.overByMin > 0) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Warning, null, tint = FF.colors.warning, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Overloaded by ${formatMinutes(l.overByMin)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            Text("$count  ·  ${l.doneCount} done", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        if (titles.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                titles.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        Text("Tap a day to plan it.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}
