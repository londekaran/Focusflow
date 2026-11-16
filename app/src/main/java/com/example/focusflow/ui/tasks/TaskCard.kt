package com.example.focusflow.ui.tasks

import androidx.compose.runtime.Composable
import com.example.focusflow.data.Task
import com.example.focusflow.ui.components.TaskRow
import java.time.LocalDate

/** Compact task row used by Home, Plan and Insights. It uses the shared TaskRow component. */
@Composable
fun TaskCard(t: Task, today: LocalDate, onToggle: () -> Unit, onOpen: () -> Unit) {
    TaskRow(t, today, onToggle = onToggle, onOpen = onOpen, compact = true)
}
