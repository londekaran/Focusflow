package com.example.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.focusflow.ui.theme.FFShapes

/** A row of equal-width options where one is selected, such as High, Medium and Low. */
@Composable
fun SegmentedSelector(
    options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, dots: List<Color>? = null
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(FFShapes.small).background(cs.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            Row(
                Modifier.weight(1f).height(44.dp).clip(FFShapes.chip)
                    .background(if (sel) cs.surface else Color.Transparent)
                    .then(if (sel) Modifier.border(1.dp, cs.outlineVariant, FFShapes.chip) else Modifier)
                    .selectable(selected = sel, role = Role.RadioButton, onClick = { onSelect(i) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dots != null) {
                    Spacer(Modifier.size(8.dp).clip(CircleShape).background(dots[i]))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    label,
                    style = if (sel) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    color = if (sel) cs.onSurface else cs.onSurfaceVariant
                )
            }
        }
    }
}
