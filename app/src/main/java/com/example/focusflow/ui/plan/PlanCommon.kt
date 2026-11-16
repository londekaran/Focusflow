package com.example.focusflow.ui.plan

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.focusflow.domain.DayLoad
import com.example.focusflow.ui.IconAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
}

/** How full a day is, from 0 to 1: remaining work against the time available. */
internal fun fraction(l: DayLoad): Float =
    if (l.capacityMin <= 0) (if (l.remainingMin > 0) 1f else 0f) else l.remainingMin.toFloat() / l.capacityMin

/** Previous and next buttons around a centred label. */
@Composable
internal fun DateNavRow(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconAction(Icons.Rounded.ChevronLeft, "Previous", onPrev)
        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconAction(Icons.Rounded.ChevronRight, "Next", onNext)
    }
}
