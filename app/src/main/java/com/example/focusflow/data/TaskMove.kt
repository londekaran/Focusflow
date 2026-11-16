package com.example.focusflow.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One reschedule of a task. Kept so a task's history is never lost when it is moved. */
@Entity(tableName = "task_moves")
data class TaskMove(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val fromDate: String,
    val toDate: String,
    val fromDue: String?,
    val toDue: String?,
    val movedAt: Long
)

@Dao
interface TaskMoveDao {
    @Insert suspend fun insertAll(list: List<TaskMove>)

    @Query("SELECT * FROM task_moves WHERE taskId = :taskId ORDER BY movedAt DESC")
    fun observeFor(taskId: Long): Flow<List<TaskMove>>

    @Query("SELECT * FROM task_moves ORDER BY movedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<TaskMove>>

    @Query("DELETE FROM task_moves WHERE taskId = :taskId")
    suspend fun deleteFor(taskId: Long)

    @Query("DELETE FROM task_moves")
    suspend fun deleteAll()
}
