package com.example.focusflow.ui.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.data.AppDb
import com.example.focusflow.data.DistractingApp
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.domain.DayBlock
import com.example.focusflow.domain.Routine
import com.example.focusflow.ui.Chip
import com.example.focusflow.ui.SwitchCard
import com.example.focusflow.ui.TimeDialog
import com.example.focusflow.ui.card
import com.example.focusflow.ui.clockText
import com.example.focusflow.ui.components.PrimaryButton
import com.example.focusflow.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
private fun StepPage(title: String, sub: String, button: String, onNext: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(cs.background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
        Text(sub, fontSize = 14.sp, color = cs.onSurfaceVariant)
        content()
        PrimaryButton(button, onClick = onNext, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }
}

/** Step 3: what are you? */
@Composable
fun PersonaStep(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { ServiceLocator.settings(ctx) }
    var persona by rememberSaveable { mutableStateOf(s.persona) }
    StepPage("What describes you best?", "We'll build a daily timetable around it.", "Continue", {
        if (persona.isNotEmpty()) {
            if (persona != s.persona || s.routine.isBlank()) s.routine = Routine.encode(Routine.defaults(persona))
            s.persona = persona
            onDone()
        }
    }) {
        Routine.PERSONAS.forEach { p ->
            Row(
                Modifier.fillMaxWidth().card().clickable { persona = p }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(persona == p, { persona = p })
                Text(p, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** Step 4: set the time of each part of the day (work/school, exercise, personal, leisure...). */
@Composable
fun RoutineTimesStep(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { ServiceLocator.settings(ctx) }
    val blocks = remember { mutableStateListOf<DayBlock>().apply { addAll(Routine.decode(s.routine)) } }
    var edit by remember { mutableStateOf<Triple<Int, Boolean, Int>?>(null) }   // index, isStart, minutes
    StepPage("Plan your day", "Set a time for each part of your day. Tap a time to change it.", "Continue", {
        s.routine = Routine.encode(blocks.toList()); onDone()
    }) {
        Routine.displayOrder(blocks).forEach { i ->
            val b = blocks[i]
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(b.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ edit = Triple(i, true, b.start) }) { Text(clockText(b.start)) }
                    Text("to")
                    TextButton({ edit = Triple(i, false, b.end) }) { Text(clockText(b.end)) }
                    Spacer(Modifier.weight(1f))
                    if (blocks.size > 1) TextButton({ blocks.removeAt(i) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        val missing = listOf("Leisure", "Personal work", "Exercise", "Study").filter { n -> blocks.none { it.name == n } }
        if (missing.isNotEmpty()) {
            Text("Add a block", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                missing.forEach { n -> Chip(n) { blocks.add(DayBlock(n, 12 * 60, 13 * 60, 25, n != "Leisure" && n != "Exercise", focusOn = n != "Leisure" && n != "Exercise")) } }
            }
        }
    }
    edit?.let { (i, isStart, m) ->
        TimeDialog(if (isStart) "Start time" else "End time", m, 15, { v ->
            blocks[i] = if (isStart) blocks[i].copy(start = v) else blocks[i].copy(end = v); edit = null
        }, { edit = null })
    }
}

/** Step 5: how focus works in each routine (focus mode, blocking and whole-routine focus). */
@Composable
fun RoutineFocusStep(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { ServiceLocator.settings(ctx) }
    val blocks = remember { mutableStateListOf<DayBlock>().apply { addAll(Routine.decode(s.routine)) } }
    StepPage("Focus & apps", "Choose how focus works in each block. Social apps get blocked while it runs.", "Finish setup", {
        s.routine = Routine.encode(blocks.toList())
        s.monitoringEnabled = true
        addDefaultBlockedApps(ctx)
        com.example.focusflow.system.ReminderScheduler.reschedule(ctx)
        onDone()
    }) {
        Routine.displayOrder(blocks).forEach { i ->
            val b = blocks[i]
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${b.name} · ${clockText(b.start)}–${clockText(b.end)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                SwitchCard("Focus mode", "Off: no focus sessions and no app blocking during this routine.", b.focusOn) { blocks[i] = b.copy(focusOn = it) }
                if (b.focusOn) {
                    SwitchCard("Block distracting apps", "Off means every app stays open.", b.blockOn) { blocks[i] = b.copy(blockOn = it) }
                    SwitchCard("Focus for the whole routine", "Focus and blocking run from start to end, with break suggestions.", b.autoFocus) { blocks[i] = b.copy(autoFocus = it) }
                }
            }
        }
    }
}

/** Puts the installed social apps on the distracting list (the per-block allowed apps are skipped while blocking). */
private fun addDefaultBlockedApps(ctx: Context) {
    val app = ctx.applicationContext
    CoroutineScope(Dispatchers.IO).launch { com.example.focusflow.system.DefaultBlockList.addInstalled(app) }
}
