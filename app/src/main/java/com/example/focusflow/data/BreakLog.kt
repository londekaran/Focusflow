package com.example.focusflow.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "break_logs")
data class BreakLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val plannedMin: Int,
    val suggestion: String
)

@Dao
interface BreakDao {
    @Insert suspend fun insert(b: BreakLog): Long

    @Query("UPDATE break_logs SET endedAt = :endedAt WHERE id = :id")
    suspend fun close(id: Long, endedAt: Long)

    @Query("SELECT * FROM break_logs ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<BreakLog>>
}
