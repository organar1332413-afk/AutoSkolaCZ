package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "Lesson",
    primaryKeys = ["id"]
)
data class LessonEntity(
    val id: String,
    val topic: String,
    val titleCs: String,
    val source: String,
    val contentVersion: String,
    val reviewStatus: String,
    val estimatedMinutes: Int
)
