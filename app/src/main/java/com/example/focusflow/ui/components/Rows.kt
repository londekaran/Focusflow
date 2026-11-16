package com.example.focusflow.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focusflow.ui.IconAction
import com.example.focusflow.ui.rowSurface

/** A labelled value with minus and plus buttons, for numbers such as minutes. */
@Composable
fun StepperRow(
    label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().rowSurface().padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            color = if (enabled) cs.onSurface else cs.onSurfaceVariant
        )
        if (enabled) IconAction(Icons.Rounded.Remove, "Less", onMinus)
        Text(
            value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
            color = if (enabled) cs.onSurface else cs.onSurfaceVariant, modifier = Modifier.widthIn(min = 72.dp)
        )
        if (enabled) IconAction(Icons.Rounded.Add, "More", onPlus)
    }
}
