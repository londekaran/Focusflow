package com.example.focusflow.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val priority: Int = Priority.MEDIUM,
    val category: String = "General",
    /** Day the task is planned for, as yyyy-MM-dd. */
    val date: String = LocalDate.now().toString(),
    /** Optional deadline, as yyyy-MM-dd. */
    val dueDate: String? = null,
    val estimateMin: Int? = null,
    /** Optional start time of the planned work session, in minutes after midnight. */
    val startMin: Int? = null,
    /** 0 = pending, 1 = ongoing, 3 = discarded (hidden everywhere). Completion is the separate [done] flag. */
    @ColumnInfo(defaultValue = "0") val status: Int = TaskStatus.PENDING,
    val done: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object TaskStatus {
    const val PENDING = 0
    const val ONGOING = 1
    const val DISCARDED = 3
}

object Priority {
    const val LOW = 0
    const val MEDIUM = 1
    const val HIGH = 2
    fun label(p: Int) = when (p) { LOW -> "Low"; HIGH -> "High"; else -> "Medium" }
}

val CATEGORIES = listOf("General", "Work", "Study", "Personal", "Health")
