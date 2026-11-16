package com.example.focusflow.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.focusflow.UiTick
import com.example.focusflow.data.CATEGORIES
import com.example.focusflow.data.TaskStatus
import com.example.focusflow.data.Task
import com.example.focusflow.domain.TaskRules
import com.example.focusflow.ui.IconAction
import com.example.focusflow.ui.PillField
import com.example.focusflow.ui.components.EmptyState
import com.example.focusflow.ui.components.FFChip
import com.example.focusflow.ui.theme.FFShapes
import java.time.LocalDate

@Composable
fun TasksScreen(vm: TasksViewModel, onOpen: (Long) -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit, showTitle: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val tasks by vm.tasks.collectAsState()
    val today = remember(UiTick.resume.intValue) { LocalDate.now() }
    var tab by rememberSaveable { mutableIntStateOf(0) }          // 0 All, 1 Today, 2 Upcoming, 3 Completed, 4 Overdue
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) }         // 0 priority, 1 date, 2 A to Z
    var cat by rememberSaveable { mutableStateOf("All") }
    var menuOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Task?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }   // only one task card is open at a time
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val routineNames = remember {
        com.example.focusflow.domain.Routine.sortedByStart(
            com.example.focusflow.domain.Routine.decode(com.example.focusflow.data.ServiceLocator.settings(ctx).routine)
        ).map { it.name }
    }

    val visible = remember(tasks, cat, query) {
        tasks.filter { t ->
            (cat == "All" || t.category == cat) &&
                (query.isBlank() || t.title.contains(query, ignoreCase = true) || t.description.contains(query, ignoreCase = true))
        }
    }
    val lists = remember(visible, today, sort) {
        fun sorted(l: List<Task>): List<Task> = when (sort) {
            1 -> l.sortedWith(compareBy<Task>({ it.date }, { it.startMin ?: Int.MAX_VALUE }))
            2 -> l.sortedBy { it.title.lowercase() }
            else -> TaskRules.sortForDisplay(l)
        }
        listOf(
            sorted(visible.filter { !it.done }) + TaskRules.doneList(visible),
            sorted(TaskRules.todayList(visible, today)),
            sorted(TaskRules.upcomingList(visible, today)),
            TaskRules.doneList(visible),
            sorted(TaskRules.overdueList(visible, today)),
            sorted(visible.filter { !it.done && it.status == TaskStatus.PENDING }),
            sorted(visible.filter { !it.done && it.status == TaskStatus.ONGOING })
        )
    }
    val labels = listOf("All", "Today", "Upcoming", "Completed", "Overdue", "Pending", "Ongoing")

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(top = if (showTitle) 16.dp else 0.dp, bottom = 8.dp).heightIn(min = if (showTitle) 52.dp else 44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (searching) {
                PillField(query, { query = it }, "Search tasks", {}, Modifier.weight(1f))
                IconAction(Icons.Rounded.Close, "Close search") { searching = false; query = "" }
            } else {
                if (showTitle) Text("Tasks", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium) else Spacer(Modifier.weight(1f))
                IconAction(Icons.Rounded.Search, "Search tasks") { searching = true }
                Box {
                    IconAction(Icons.Rounded.MoreVert, "Sort and filter") { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        listOf("Sort by priority", "Sort by date", "Sort A to Z").forEachIndexed { i, label ->
                            DropdownMenuItem(
                                text = { Text((if (sort == i) "✓  " else "") + label) },
                                onClick = { sort = i; menuOpen = false }
                            )
                        }
                        HorizontalDivider()
                        (listOf("All") + (routineNames + tasks.map { it.category } + CATEGORIES).distinct()).forEach { c ->
                            DropdownMenuItem(
                                text = { Text((if (cat == c) "✓  " else "") + "Category: $c") },
                                onClick = { cat = c; menuOpen = false }
                            )
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l ->
                FFChip(if (i == 0) l else "$l  ${lists[i].size}", selected = tab == i, onClick = { tab = i })
            }
        }
        Spacer(Modifier.height(12.dp))

        val shown = lists[tab]
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
                val (title, sub) = when {
                    tasks.isEmpty() -> "Nothing here yet." to "Add a task to get started."
                    query.isNotBlank() -> "No matching tasks." to "Try a different word."
                    else -> when (tab) {
                        1 -> "Nothing due today." to "Enjoy the space, or plan something."
                        2 -> "No upcoming tasks." to "Add one with the button below."
                        3 -> "No completed tasks yet." to "Finish a task and it will show up here."
                        4 -> "Nothing overdue." to "Nicely done."
                        else -> "No tasks match." to "Change the category in the menu."
                    }
                }
                EmptyState(
                    Icons.Rounded.TaskAlt, title, sub, Modifier.align(Alignment.Center),
                    actionLabel = if (tasks.isEmpty()) "Create Task" else null, onAction = onAdd
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { t ->
                        ExpandableTaskCard(
                            task = t, today = today, expanded = expandedId == t.id,
                            onToggleExpand = { expandedId = if (expandedId == t.id) null else t.id },
                            onToggleDone = { vm.toggle(t) },
                            onDetails = { onOpen(t.id) },
                            onEdit = { onEdit(t.id) },
                            onSetStatus = { vm.setStatus(t, it) },
                            onDelete = { deleting = t },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp),
                containerColor = cs.primary, contentColor = cs.onPrimary, shape = FFShapes.card
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Task", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = cs.surface,
            title = { Text("Delete this task?") },
            text = { Text("“${t.title}” will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton({
                    vm.delete(t.id)
                    if (expandedId == t.id) expandedId = null
                    deleting = null
                }) { Text("Delete", color = cs.error) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } }
        )
    }
}
