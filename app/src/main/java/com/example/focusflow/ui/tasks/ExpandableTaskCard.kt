package com.example.focusflow.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.focusflow.data.Task
import com.example.focusflow.data.TaskStatus
import com.example.focusflow.domain.TaskRules
import com.example.focusflow.ui.card
import com.example.focusflow.ui.clockText
import com.example.focusflow.ui.components.CategoryChip
import com.example.focusflow.ui.components.PriorityChip
import com.example.focusflow.ui.components.TaskCheckbox
import com.example.focusflow.ui.components.ffClickable
import com.example.focusflow.ui.components.relDate
import com.example.focusflow.ui.formatMinutes
import com.example.focusflow.ui.theme.FF
import com.example.focusflow.ui.theme.FFShapes
import java.time.LocalDate

/**
 * A task in the Plan > Tasks list, drawn like a routine card: the same rounded, bordered surface, the same
 * title and subtitle styles and the same chevron. Tapping the title area opens the card in place to show the
 * task's actions (there is no pop-up menu); tapping it again closes it. The completion checkbox and the actions
 * are separate touch targets, so using them never opens or closes the card.
 *
 * It only reports what the person asked for. The caller owns which card is open and runs the real task
 * operations (TasksViewModel), and shows the delete confirmation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandableTaskCard(
    task: Task,
    today: LocalDate,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleDone: () -> Unit,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onSetStatus: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val overdue = TaskRules.isOverdue(task, today)
    val angle by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "taskChevron")

    Column(modifier.fillMaxWidth().card()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            TaskCheckbox(task.done, onToggleDone, Modifier.padding(start = 4.dp))
            Row(
                Modifier.weight(1f)
                    .clickable(
                        onClickLabel = if (expanded) "Collapse ${task.title}" else "Expand ${task.title}",
                        role = Role.Button, onClick = onToggleExpand
                    )
                    .semantics(mergeDescendants = true) { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .heightIn(min = 64.dp).padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title, style = MaterialTheme.typography.titleMedium,
                        color = if (task.done) cs.onSurfaceVariant else cs.onSurface,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        scheduleLine(task, today), style = MaterialTheme.typography.bodySmall,
                        color = if (overdue && !task.done) cs.error else cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val (statusLabel, statusColor) = statusOf(task, overdue)
                    Text(
                        buildAnnotatedString {
                            append(task.category)
                            append("  ·  ")
                            withStyle(SpanStyle(color = statusColor, fontWeight = FontWeight.Medium)) { append(statusLabel) }
                        },
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ExpandMore, null, tint = cs.onSurfaceVariant, modifier = Modifier.rotate(angle))
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryChip(task.category)
                    PriorityChip(task.priority)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionPill("Details", Icons.Rounded.Info, onClick = onDetails)
                    ActionPill("Edit", Icons.Rounded.Edit, onClick = onEdit)
                    // Only the status change that makes sense for the task as it is now.
                    if (!task.done && task.status != TaskStatus.ONGOING) {
                        ActionPill("Ongoing", Icons.Rounded.PlayArrow) { onSetStatus(TaskStatus.ONGOING) }
                    }
                    if (!task.done && task.status == TaskStatus.ONGOING) {
                        ActionPill("Pending", Icons.Rounded.Schedule) { onSetStatus(TaskStatus.PENDING) }
                    }
                    ActionPill("Delete", Icons.Rounded.Delete, destructive = true, onClick = onDelete)
                }
            }
        }
    }
}

/** "Today, 09:00 AM  ·  Due Tomorrow  ·  30m": when the task is planned, its deadline and its estimate. */
private fun scheduleLine(t: Task, today: LocalDate): String {
    val parts = ArrayList<String>()
    parts.add(relDate(t.date, today) + (t.startMin?.let { ", ${clockText(it)}" } ?: ""))
    t.dueDate?.let { if (it != t.date) parts.add("Due ${relDate(it, today)}") }
    t.estimateMin?.let { parts.add(formatMinutes(it)) }
    return parts.joinToString("  ·  ")
}

@Composable
private fun statusOf(t: Task, overdue: Boolean): Pair<String, Color> = when {
    t.done -> "Completed" to FF.colors.success
    t.status == TaskStatus.ONGOING -> (if (overdue) "Ongoing · Overdue" else "Ongoing") to
        (if (overdue) MaterialTheme.colorScheme.error else FF.colors.info)
    overdue -> "Pending · Overdue" to MaterialTheme.colorScheme.error
    else -> "Pending" to MaterialTheme.colorScheme.onSurfaceVariant
}

/** A small pill-shaped action inside an open card. */
@Composable
private fun ActionPill(label: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = if (destructive) FF.colors.errorContainer else cs.surfaceVariant
    val fg = if (destructive) cs.error else cs.onSurface
    Row(
        Modifier.heightIn(min = 44.dp)
            .ffClickable(FFShapes.chip, role = Role.Button, onClick = onClick)
            .background(bg)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}
