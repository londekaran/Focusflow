package com.example.focusflow.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focusflow.data.Task
import com.example.focusflow.data.TaskMove
import com.example.focusflow.data.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TasksViewModel(private val repo: TaskRepository) : ViewModel() {
    val tasks: StateFlow<List<Task>> =
        repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The latest reschedules across all tasks, newest first. */
    val recentMoves: StateFlow<List<TaskMove>> =
        repo.recentMoves.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun quickAdd(title: String) {
        viewModelScope.launch { repo.save(Task(title = title)) }
    }

    fun addForDate(title: String, date: LocalDate, category: String = "General") {
        viewModelScope.launch { repo.save(Task(title = title, date = date.toString(), category = category)) }
    }

    /** Creates a task, or edits the existing one with this id. A changed planned day is added to its history. */
    fun save(id: Long, edit: (Task?) -> Task) {
        viewModelScope.launch {
            val base = if (id != 0L) repo.get(id) else null
            repo.saveFromEditor(base, edit(base))
        }
    }

    fun setStatus(t: Task, status: Int) {
        viewModelScope.launch { repo.save(t.copy(status = status, updatedAt = System.currentTimeMillis())) }
    }

    fun toggle(t: Task) {
        viewModelScope.launch { repo.setDone(t, !t.done) }
    }

    fun reschedule(list: List<Task>, to: LocalDate, start: Int? = null, applyStart: Boolean = false) {
        viewModelScope.launch { repo.reschedule(list, to, start, applyStart) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.get(id)?.let { repo.delete(it) } }
    }

    fun setStarts(starts: Map<Long, Int>) {
        viewModelScope.launch { repo.setStarts(starts) }
    }

    fun movesFor(id: Long): Flow<List<TaskMove>> = repo.movesFor(id)

    suspend fun load(id: Long): Task? = repo.get(id)
}
