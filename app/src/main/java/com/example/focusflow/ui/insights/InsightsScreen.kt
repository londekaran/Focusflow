package com.example.focusflow.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.UiTick
import com.example.focusflow.domain.Analytics
import com.example.focusflow.domain.TaskRules
import com.example.focusflow.domain.UsageAnalyzer
import com.example.focusflow.ui.Chip
import com.example.focusflow.ui.Header
import com.example.focusflow.ui.SectionTitle
import com.example.focusflow.ui.StatTile
import com.example.focusflow.ui.card
import com.example.focusflow.ui.distraction.DistractionViewModel
import com.example.focusflow.ui.focus.FocusViewModel
import com.example.focusflow.ui.tasks.TaskCard
import com.example.focusflow.ui.tasks.TasksViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Analytics plus the daily and weekly review, built only from data recorded on this phone. */
@Composable
fun InsightsScreen(
    vm: TasksViewModel, focusVm: FocusViewModel, distractionVm: DistractionViewModel,
    onOpen: (Long) -> Unit, onPlanNextWeek: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tasks by vm.tasks.collectAsState()
    val sessions by focusVm.sessions.collectAsState()
    val interruptions by distractionVm.interruptions.collectAsState()
    val tick = UiTick.resume.intValue
    val today = remember(tick) { LocalDate.now() }
    var period by rememberSaveable { mutableIntStateOf(0) }   // 0 = today, 1 = last 7 days
    val dates = remember(today, period) {
        if (period == 0) listOf(today) else (6L downTo 0L).map { today.minusDays(it) }
    }
    val label = if (period == 0) "today" else "this week"
    val from = dates.first()
    val to = dates.last()

    val days = remember(tasks, sessions, interruptions, dates) {
        dates.map { Analytics.dayStats(tasks, sessions, interruptions, it) }
    }
    val completed = days.sumOf { it.completed }
    val focusSec = days.sumOf { it.focusSec }
    val sessionCount = days.sumOf { it.sessions }
    val interruptionCount = days.sumOf { it.interruptions }

    val stats = remember(tasks, today) { TaskRules.stats(tasks, today) }
    val planned = remember(tasks, from, to) { Analytics.plannedInRange(tasks, from, to) }
    val overdue = remember(tasks, today) { TaskRules.overdueList(tasks, today) }
    val todayLeft = remember(tasks, today) { TaskRules.todayList(tasks, today) }
    val unfinished = remember(overdue, todayLeft) { TaskRules.sortForDisplay(overdue + todayLeft) }
    val samples = remember(tasks, sessions, from, to) { Analytics.estimateSamples(tasks, sessions, from, to) }
    val monitored = remember(sessions, from, to) { Analytics.monitoredSessions(sessions, from, to) }
    val inRange = remember(interruptions, from, to) { Analytics.interruptionsInRange(interruptions, from, to) }
    val appSummary = remember(inRange) { UsageAnalyzer.summarize(inRange) }
    val insights = remember(planned, overdue, samples, focusSec, sessionCount, monitored, interruptionCount, label) {
        Analytics.insights(planned, overdue.size, samples, focusSec, sessionCount, monitored, interruptionCount, label)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header("Insights", "Your day and week, from your own records")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Today", selected = period == 0) { period = 0 }
            Chip("This week", selected = period == 1) { period = 1 }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Completed", completed, Modifier.weight(1f))
            StatTile("Focus min", focusSec / 60, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Sessions", sessionCount, Modifier.weight(1f))
            StatTile("Interruptions", interruptionCount, Modifier.weight(1f))
        }
        Text("Right now: ${stats.pending} pending  ·  ${stats.overdue} overdue", fontSize = 14.sp, color = cs.onSurfaceVariant)

        if (period == 1) {
            val names = days.map { it.date.format(DateTimeFormatter.ofPattern("EEE")) }
            SectionTitle("Tasks completed per day")
            BarChart(days.map { it.completed.toFloat() }, names)
            SectionTitle("Focus minutes per day")
            BarChart(days.map { (it.focusSec / 60).toFloat() }, names)
        }

        SectionTitle("What the numbers say")
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            insights.forEach { Text(it, fontSize = 15.sp) }
        }

        if (appSummary.isNotEmpty()) {
            SectionTitle("Time in distracting apps")
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                appSummary.forEach {
                    Text("${it.label}: ${it.count}×  ·  ${Analytics.minutesText(maxOf(1, it.totalSec / 60))}", fontSize = 15.sp)
                }
            }
        }

        if (samples.isNotEmpty()) {
            SectionTitle("Estimated vs actual time")
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                samples.take(5).forEach {
                    Text("${it.title}: estimated ${it.estMin} min  ·  actual ${it.actualMin} min", fontSize = 14.sp)
                }
            }
        }

        SectionTitle("Review: unfinished tasks")
        if (unfinished.isEmpty()) {
            Text("Everything for today is done.", fontSize = 15.sp, color = cs.onSurfaceVariant)
        } else {
            unfinished.forEach { t -> TaskCard(t, today, onToggle = { vm.toggle(t) }, onOpen = { onOpen(t.id) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unfinished.isNotEmpty()) Chip("Move unfinished to tomorrow") { vm.reschedule(unfinished, today.plusDays(1)) }
            if (period == 1) Chip("Plan next week") { onPlanNextWeek() }
        }

        Text(
            "Task counts and app usage time are indicators, not a complete measure of how productive you are. " +
                "Everything here is calculated on your phone from the data you recorded.",
            fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun BarChart(values: List<Float>, labels: List<String>) {
    val cs = MaterialTheme.colorScheme
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Row(
        Modifier.fillMaxWidth().height(130.dp).card().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (v > 0f) Text("${v.toInt()}", fontSize = 11.sp, color = cs.onSurfaceVariant)
                Box(
                    Modifier.fillMaxWidth().height((64.dp * (v / max)).coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (i == values.lastIndex) cs.primary else cs.primary.copy(alpha = 0.35f))
                )
                Spacer(Modifier.height(4.dp))
                Text(labels[i], fontSize = 11.sp, color = cs.onSurfaceVariant)
            }
        }
    }
}
