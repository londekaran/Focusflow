package com.example.focusflow.data

import android.content.Context
import com.example.focusflow.domain.TaskRules
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TaskRepository(private val dao: TaskDao, private val moveDao: TaskMoveDao) {
    val tasks: Flow<List<Task>> = dao.observeAll()

    suspend fun get(id: Long): Task? = dao.getById(id)

    suspend fun save(t: Task) {
        if (t.id == 0L) dao.insert(t) else dao.update(t.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Saves from the task editor; if the planned day changed, the move is added to the task's history. */
    suspend fun saveFromEditor(base: Task?, updated: Task) {
        if (base == null) {
            dao.insert(updated)
            return
        }
        val now = System.currentTimeMillis()
        dao.update(updated.copy(updatedAt = now))
        if (base.date != updated.date) {
            moveDao.insertAll(
                listOf(
                    TaskMove(
                        taskId = base.id, fromDate = base.date, toDate = updated.date,
                        fromDue = base.dueDate, toDue = updated.dueDate, movedAt = now
                    )
                )
            )
        }
    }

    /** Moves tasks to another day and records each move. */
    /** [applyStart] true also sets the start time to [newStart] (null clears it); false keeps each task's time. */
    suspend fun reschedule(list: List<Task>, to: LocalDate, newStart: Int? = null, applyStart: Boolean = false) {
        val now = System.currentTimeMillis()
        list.forEach { t ->
            val moved = TaskRules.reschedule(t, to).let { if (applyStart) it.copy(startMin = newStart) else it }
            if (moved != t) {
                dao.update(moved.copy(updatedAt = now))
                moveDao.insertAll(
                    listOf(
                        TaskMove(
                            taskId = t.id, fromDate = t.date, toDate = moved.date,
                            fromDue = t.dueDate, toDue = moved.dueDate, movedAt = now
                        )
                    )
                )
            }
        }
    }

    /** Sets the planned start time (minutes after midnight) for several tasks at once. */
    suspend fun setStarts(starts: Map<Long, Int>) {
        val now = System.currentTimeMillis()
        starts.forEach { (id, start) ->
            dao.getById(id)?.let { dao.update(it.copy(startMin = start, updatedAt = now)) }
        }
    }

    fun movesFor(taskId: Long): Flow<List<TaskMove>> = moveDao.observeFor(taskId)

    val recentMoves: Flow<List<TaskMove>> = moveDao.observeRecent()

    suspend fun setDone(t: Task, done: Boolean) {
        val now = System.currentTimeMillis()
        dao.update(t.copy(done = done, completedAt = if (done) now else null, updatedAt = now))
    }

    suspend fun delete(t: Task) {
        moveDao.deleteFor(t.id)
        dao.delete(t)
    }

    suspend fun deleteAll() {
        moveDao.deleteAll()
        dao.deleteAll()
    }
}

/** Simple manual wiring (no dependency-injection library needed yet). */
object ServiceLocator {
    @Volatile private var taskRepo: TaskRepository? = null
    @Volatile private var focusRepo: FocusRepository? = null
    @Volatile private var distractionRepo: DistractionRepository? = null
    @Volatile private var settingsStore: SettingsStore? = null

    fun repository(ctx: Context): TaskRepository = taskRepo ?: synchronized(this) {
        taskRepo ?: AppDb.get(ctx).let { TaskRepository(it.dao(), it.moveDao()) }.also { taskRepo = it }
    }

    fun sessions(ctx: Context): FocusRepository = focusRepo ?: synchronized(this) {
        focusRepo ?: FocusRepository(AppDb.get(ctx).sessionDao()).also { focusRepo = it }
    }

    fun distractions(ctx: Context): DistractionRepository = distractionRepo ?: synchronized(this) {
        distractionRepo ?: DistractionRepository(AppDb.get(ctx).distractionDao()).also { distractionRepo = it }
    }

    fun settings(ctx: Context): SettingsStore = settingsStore ?: synchronized(this) {
        settingsStore ?: SettingsStore(ctx).also { settingsStore = it }
    }
}
