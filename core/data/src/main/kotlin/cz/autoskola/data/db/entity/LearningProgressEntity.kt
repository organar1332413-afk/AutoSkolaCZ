package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "LearningProgress",
    primaryKeys = ["lessonId"],
    foreignKeys = [
    ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lessonId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["lessonId"], unique = false)]
)
data class LearningProgressEntity(
    val lessonId: String,
    val blockPosition: Int,
    val completedAt: Long?,
    val updatedAt: Long
)
