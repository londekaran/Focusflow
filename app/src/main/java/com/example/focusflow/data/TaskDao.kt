package com.example.focusflow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status != 3 ORDER BY id")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status != 3 ORDER BY id")
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Insert suspend fun insert(t: Task): Long
    @Update suspend fun update(t: Task)
    @Delete suspend fun delete(t: Task)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
