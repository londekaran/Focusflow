package com.example.focusflow.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** An installed app the user has chosen to treat as distracting. */
@Entity(tableName = "distracting_apps")
data class DistractingApp(
    @PrimaryKey val packageName: String,
    val label: String
)

/** One stretch of time a distracting app was in the foreground during a focus session. */
@Entity(tableName = "interruptions")
data class Interruption(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val packageName: String,
    val label: String,
    val startedAt: Long,
    val durationSec: Int
)

@Dao
interface DistractionDao {
    @Query("SELECT * FROM distracting_apps ORDER BY label COLLATE NOCASE")
    fun observeApps(): Flow<List<DistractingApp>>

    @Query("SELECT * FROM distracting_apps")
    suspend fun getApps(): List<DistractingApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addApp(a: DistractingApp)

    @Query("DELETE FROM distracting_apps WHERE packageName = :pkg")
    suspend fun removeApp(pkg: String)

    @Insert
    suspend fun addInterruptions(list: List<Interruption>)

    @Query("SELECT * FROM interruptions ORDER BY startedAt DESC")
    fun observeInterruptions(): Flow<List<Interruption>>

    @Query("DELETE FROM interruptions")
    suspend fun deleteAllInterruptions()
}

class DistractionRepository(private val dao: DistractionDao) {
    val apps: Flow<List<DistractingApp>> = dao.observeApps()
    val interruptions: Flow<List<Interruption>> = dao.observeInterruptions()

    suspend fun add(a: DistractingApp) = dao.addApp(a)
    suspend fun remove(pkg: String) = dao.removeApp(pkg)
    suspend fun deleteInterruptions() = dao.deleteAllInterruptions()
}
