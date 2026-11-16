package com.example.focusflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.focusflow.domain.TaskRules
import java.time.LocalDate

@Database(
    entities = [
        Task::class, FocusSession::class, DistractingApp::class,
        Interruption::class, TaskMove::class, BreakLog::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): TaskDao
    abstract fun sessionDao(): FocusSessionDao
    abstract fun distractionDao(): DistractionDao
    abstract fun moveDao(): TaskMoveDao
    abstract fun breakDao(): BreakDao

    companion object {
        // Adds the focus_sessions table without touching existing tasks.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `focus_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`endedAt` INTEGER, " +
                        "`plannedMin` INTEGER NOT NULL, " +
                        "`activeSec` INTEGER NOT NULL, " +
                        "`endReason` TEXT NOT NULL)"
                )
            }
        }

        // Adds distraction tracking tables and a monitoring flag on sessions. Existing data is kept.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `monitorState` TEXT NOT NULL DEFAULT 'off'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `distracting_apps` (" +
                        "`packageName` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "PRIMARY KEY(`packageName`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `interruptions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`durationSec` INTEGER NOT NULL)"
                )
            }
        }

        // Adds the reschedule history table. Existing data is kept.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_moves` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER NOT NULL, " +
                        "`fromDate` TEXT NOT NULL, " +
                        "`toDate` TEXT NOT NULL, " +
                        "`fromDue` TEXT, " +
                        "`toDue` TEXT, " +
                        "`movedAt` INTEGER NOT NULL)"
                )
            }
        }

        // Adds an optional start time to tasks and a table for break records. Existing data is kept.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `startMin` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `break_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`endedAt` INTEGER, " +
                        "`plannedMin` INTEGER NOT NULL, " +
                        "`suggestion` TEXT NOT NULL)"
                )
            }
        }

        // Adds the task status used by the voice workflow (pending, ongoing, discarded). Existing data is kept.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `status` INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "focusflow_v2.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build().also { inst = it }
        }
    }
}

/** Used by the focus service notification. */
suspend fun TaskDao.progressSummary(): String =
    TaskRules.summaryText(getAll(), LocalDate.now())
