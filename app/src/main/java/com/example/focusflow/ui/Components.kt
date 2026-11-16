package com.example.focusflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.ui.components.FFChip
import com.example.focusflow.ui.theme.FFShapes
import com.example.focusflow.ui.theme.FFType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * Shared widgets used by the existing screens. They now use the design tokens in ui/theme and the
 * components in ui/components. Each screen is moved to the new components as it is redesigned.
 */

/* ------------------------------ Formatting ------------------------------ */

fun shortDate(iso: String): String = LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM"))

fun formatMinutes(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}

/** Compact duration such as "2h 15m", "2h" or "45m". */
fun formatHm(min: Int): String = when {
    min < 60 -> "${min}m"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h ${min % 60}m"
}

/** 12-hour clock text such as "09:15 AM" or "12:00 PM". [min] is minutes after midnight. */
fun clockText(min: Int): String {
    val m = ((min % 1440) + 1440) % 1440
    val h12 = (m / 60) % 12
    return "%02d:%02d %s".format(if (h12 == 0) 12 else h12, m % 60, if (m < 720) "AM" else "PM")
}

/* ------------------------------ Building blocks ------------------------------ */

@Composable
fun Modifier.card(): Modifier {
    val cs = MaterialTheme.colorScheme
    return this.clip(FFShapes.card).background(cs.surface).border(1.dp, cs.outlineVariant, FFShapes.card)
}

/** True inside an expanded settings section: rows are drawn flat with a divider instead of in a box. */
val LocalFlatRows = compositionLocalOf { false }

/** A row background: a bordered card normally, or a plain row with a subtle bottom divider inside a section. */
@Composable
fun Modifier.rowSurface(): Modifier {
    if (!LocalFlatRows.current) return this.card()
    val line = MaterialTheme.colorScheme.outlineVariant
    return this.fillMaxWidth().drawBehind {
        drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }
}

@Composable
fun Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
    )
}

@Composable
fun Chip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    FFChip(label, selected = selected, onClick = onClick)
}

@Composable
fun fieldColors(): TextFieldColors {
    val cs = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = cs.surfaceVariant, unfocusedContainerColor = cs.surfaceVariant,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent, cursorColor = cs.primary
    )
}

@Composable
fun PillField(
    value: String, onChange: (String) -> Unit, placeholder: String, onDone: () -> Unit,
    modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null
) {
    TextField(
        value = value, onValueChange = onChange, modifier = modifier, singleLine = true,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = trailing, shape = FFShapes.small, colors = fieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() })
    )
}

@Composable
fun RoundButton(icon: ImageVector, desc: String, filled: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(52.dp).clip(CircleShape)
            .background(if (filled) cs.primary else cs.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = if (filled) cs.onPrimary else cs.onSurface) }
}

@Composable
fun StatTile(label: String, value: Int, modifier: Modifier = Modifier, accent: Color? = null) {
    Column(modifier.card().padding(16.dp)) {
        Text("$value", style = FFType.metric, color = accent ?: MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ------------------------------ Shared widgets ------------------------------ */

@Composable
fun IconAction(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Icon(
        icon, desc, tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).padding(12.dp)
    )
}

@Composable
fun SwitchCard(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().rowSurface().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) cs.onSurface else cs.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/**
 * Picks a time of day on a scrollable 12-hour wheel with separate AM and PM options.
 * [initial] and the result are minutes after midnight; [step] is the wheel increment in minutes.
 */
@Composable
fun TimeDialog(title: String, initial: Int, step: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val inc = step.coerceIn(1, 60).let { if (60 % it == 0) it else 15 }
    val start = (((initial % 1440) + 1440) % 1440).let { (it / inc) * inc }
    var pm by remember { mutableStateOf(start >= 720) }
    var slot by remember { mutableIntStateOf((start % 720) / inc) }   // 0 = 12:00, then each step up to 11:45
    val value = (if (pm) 720 else 0) + slot * inc
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FFChip("AM", selected = !pm, onClick = { pm = false })
                    FFChip("PM", selected = pm, onClick = { pm = true })
                }
                Spacer(Modifier.height(8.dp))
                WheelPicker(
                    count = 720 / inc, selected = slot, onSelect = { slot = it },
                    label = { i -> clockText(i * inc).substringBefore(' ') }
                )
            }
        },
        confirmButton = { TextButton({ onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

/**
 * A vertical snapping wheel of [count] labelled items; the item in the middle row is the selection.
 * [onSelect] follows the wheel while it moves; [onSettled] fires once it stops. When [selected] is changed
 * from outside, the wheel scrolls to it.
 */
@Composable
internal fun WheelPicker(
    count: Int, selected: Int, onSelect: (Int) -> Unit, label: (Int) -> String,
    rows: Int = 5, description: String = "Time wheel", onSettled: (Int) -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val rowH = 44.dp
    val pad = rows / 2
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, count - 1))
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    val scope = rememberCoroutineScope()
    val current by remember {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0).coerceIn(0, count - 1)
        }
    }
    LaunchedEffect(current) { onSelect(current) }
    // Follow a change made outside the wheel (for example a task with its own length being chosen).
    LaunchedEffect(selected) { if (!state.isScrollInProgress && selected != current) state.scrollToItem(selected.coerceIn(0, count - 1)) }
    LaunchedEffect(state.isScrollInProgress) { if (!state.isScrollInProgress) onSettled(current) }

    Box(Modifier.fillMaxWidth().height(rowH * rows), contentAlignment = Alignment.Center) {
        // Highlight bar behind the middle row
        Box(Modifier.fillMaxWidth().height(rowH).clip(FFShapes.small).background(cs.primaryContainer.copy(alpha = 0.5f)))
        LazyColumn(
            state = state, modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
            flingBehavior = rememberSnapFlingBehavior(state), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Blank rows above and below let the first and last items reach the middle row.
            items(pad) { Spacer(Modifier.height(rowH)) }
            items(count) { i ->
                Box(
                    Modifier.fillMaxWidth().height(rowH).clickable { scope.launch { state.animateScrollToItem(i) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label(i),
                        style = if (i == current) FFType.metricLarge.copy(fontSize = 28.sp) else MaterialTheme.typography.titleLarge,
                        color = if (i == current) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            items(pad) { Spacer(Modifier.height(rowH)) }
        }
    }
}
