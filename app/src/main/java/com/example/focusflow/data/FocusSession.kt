package com.example.focusflow.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val plannedMin: Int,
    /** Seconds actually spent focusing (paused time excluded). */
    val activeSec: Int = 0,
    /** running, completed, ended (stopped early) or interrupted (app was killed). */
    val endReason: String = "running",
    /** on, off (switched off in settings), no_access (usage access missing) or no_apps (none chosen). */
    @ColumnInfo(defaultValue = "off") val monitorState: String = "off"
)

@Dao
interface FocusSessionDao {
    @Insert suspend fun insert(s: FocusSession): Long
    @Update suspend fun update(s: FocusSession)

    @Query("UPDATE focus_sessions SET activeSec = :sec WHERE id = :id")
    suspend fun saveProgress(id: Long, sec: Int)

    @Query("UPDATE focus_sessions SET endedAt = :endedAt, activeSec = :sec, endReason = :reason WHERE id = :id")
    suspend fun close(id: Long, endedAt: Long, sec: Int, reason: String)

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions")
    suspend fun getAll(): List<FocusSession>

    @Query("SELECT * FROM focus_sessions WHERE endedAt IS NULL")
    suspend fun openSessions(): List<FocusSession>

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAll()
}

class FocusRepository(private val dao: FocusSessionDao) {
    val sessions: Flow<List<FocusSession>> = dao.observeAll()

    /** Sessions left open because the app was killed mid-session are closed with the time recorded so far. */
    suspend fun closeOrphans() {
        dao.openSessions().forEach {
            dao.update(it.copy(endedAt = it.startedAt + it.activeSec * 1000L, endReason = "interrupted"))
        }
    }

    suspend fun deleteAll() = dao.deleteAll()
}
