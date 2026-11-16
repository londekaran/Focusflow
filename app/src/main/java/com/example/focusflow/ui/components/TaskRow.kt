package com.example.focusflow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.focusflow.data.Task
import com.example.focusflow.data.TaskStatus
import com.example.focusflow.domain.TaskRules
import com.example.focusflow.ui.clockText
import com.example.focusflow.ui.formatMinutes
import com.example.focusflow.ui.theme.FFShapes
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Round completion checkbox with a short fill and tick animation. The touch area is 48dp. */
@Composable
fun TaskCheckbox(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (checked) cs.primary else Color.Transparent, label = "fill")
    val edge by animateColorAsState(if (checked) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.6f), label = "edge")
    val tick by animateFloatAsState(if (checked) 1f else 0f, label = "tick")
    Box(
        modifier.size(48.dp).clip(CircleShape)
            .semantics { contentDescription = if (checked) "Mark task incomplete" else "Mark task complete" }
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(fill).border(2.dp, edge, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Check, null, tint = cs.onPrimary,
                modifier = Modifier.size(16.dp).graphicsLayer { scaleX = tick; scaleY = tick; alpha = tick }
            )
        }
    }
}

internal fun relDate(iso: String, today: LocalDate): String {
    val d = LocalDate.parse(iso)
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

private fun scheduleText(t: Task, today: LocalDate, overdue: Boolean): String {
    val parts = ArrayList<String>()
    if (overdue) parts.add("Overdue")
    if (t.status == TaskStatus.ONGOING && !t.done) parts.add("Ongoing")
    parts.add(relDate(t.date, today) + (t.startMin?.let { ", ${clockText(it)}" } ?: ""))
    t.dueDate?.let { if (it != t.date) parts.add("Due ${relDate(it, today)}") }
    t.estimateMin?.let { parts.add(formatMinutes(it)) }
    return parts.joinToString("  ·  ")
}

/**
 * One task as a row: a category colour strip, the checkbox, title, schedule line and chips.
 * [compact] drops the chip line and shows a small priority chip at the right instead.
 * (The Plan > Tasks list uses ExpandableTaskCard instead.)
 */
@Composable
fun TaskRow(
    task: Task, today: LocalDate, onToggle: () -> Unit, onOpen: () -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val overdue = TaskRules.isOverdue(task, today)
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .ffClickable(FFShapes.card, role = Role.Button, onClick = onOpen)
            .background(cs.surface, FFShapes.card)
            .border(1.dp, cs.outlineVariant, FFShapes.card),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(categoryColor(task.category)))
        TaskCheckbox(task.done, onToggle, Modifier.padding(start = 4.dp))
        Column(Modifier.weight(1f).padding(vertical = if (compact) 10.dp else 12.dp, horizontal = 4.dp)) {
            Text(
                task.title, style = MaterialTheme.typography.titleMedium,
                color = if (task.done) cs.onSurfaceVariant else cs.onSurface,
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                scheduleText(task, today, overdue), style = MaterialTheme.typography.bodySmall,
                color = if (overdue) cs.error else cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryChip(task.category)
                    PriorityChip(task.priority)
                }
            }
        }
        if (compact) {
            PriorityChip(task.priority, Modifier.padding(end = 12.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}
