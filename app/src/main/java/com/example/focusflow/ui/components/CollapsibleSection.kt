package com.example.focusflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.focusflow.ui.theme.FFShapes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.focusflow.ui.LocalFlatRows
import com.example.focusflow.ui.card

/**
 * A titled section whose content folds away. Each section keeps its own expanded state
 * (saved across rotation), so opening or closing one never affects another.
 */
@Composable
fun CollapsibleSection(
    title: String,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
    icon: ImageVector? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var expanded by rememberSaveable(title) { mutableStateOf(defaultExpanded) }
    val angle by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "chevron")

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().card()
                .clickable(
                    onClickLabel = if (expanded) "Collapse $title" else "Expand $title",
                    role = Role.Button
                ) { expanded = !expanded }
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                // Same icon style everywhere: a 36dp rounded tile in the soft brand colour.
                Box(
                    Modifier.size(36.dp).clip(FFShapes.small).background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ExpandMore, null, tint = cs.onSurfaceVariant, modifier = Modifier.rotate(angle))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
        ) {
            // Settings sit directly in the section: no box per setting, just spacing and subtle dividers.
            CompositionLocalProvider(LocalFlatRows provides true) {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), content = content)
            }
        }
    }
}
