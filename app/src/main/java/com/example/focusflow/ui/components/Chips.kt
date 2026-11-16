package com.example.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.focusflow.data.Priority
import com.example.focusflow.ui.theme.FF
import com.example.focusflow.ui.theme.FFShapes

@Composable
private fun Dot(color: Color) {
    Spacer(Modifier.size(8.dp).clip(CircleShape).background(color))
}

/** A selectable filter or option. The selected state is shown by fill and weight, not by colour alone. */
@Composable
fun FFChip(
    label: String, modifier: Modifier = Modifier, selected: Boolean = false,
    dot: Color? = null, onClick: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) cs.primaryContainer else Color.Transparent
    val shaped = modifier
        .semantics { this.selected = selected }
        .let { if (onClick != null) it.ffClickable(FFShapes.chip, role = Role.Tab, onClick = onClick) else it.clip(FFShapes.chip) }
    Row(
        shaped.background(bg)
            .border(1.dp, if (selected) Color.Transparent else cs.outlineVariant, FFShapes.chip)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) {
            Dot(dot)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            color = if (selected) cs.onPrimaryContainer else cs.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun priorityColor(priority: Int): Color = when (priority) {
    Priority.HIGH -> MaterialTheme.colorScheme.error
    Priority.LOW -> FF.colors.success
    else -> FF.colors.warning
}

/** High, Medium or Low. Always carries a text label so it never relies on colour alone. */
@Composable
fun PriorityChip(priority: Int, modifier: Modifier = Modifier) {
    val bg = when (priority) {
        Priority.HIGH -> FF.colors.errorContainer
        Priority.LOW -> FF.colors.successContainer
        else -> FF.colors.warningContainer
    }
    Row(
        modifier.clip(FFShapes.chip).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(priorityColor(priority))
        Spacer(Modifier.width(6.dp))
        Text(Priority.label(priority), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun categoryColor(category: String): Color = when (category) {
    "Work" -> FF.colors.catWork
    "Study" -> FF.colors.catStudy
    "Personal" -> FF.colors.catPersonal
    "Health" -> FF.colors.catHealth
    else -> FF.colors.catGeneral
}

/** A small tag showing a task's category with its colour. */
@Composable
fun CategoryChip(category: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(FFShapes.chip).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(categoryColor(category))
        Spacer(Modifier.width(6.dp))
        Text(category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
