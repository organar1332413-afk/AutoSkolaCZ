package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "LessonTranslation",
    primaryKeys = ["lessonId", "locale"],
    foreignKeys = [
    ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lessonId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["lessonId"], unique = false)]
)
data class LessonTranslationEntity(
    val lessonId: String,
    val locale: String,
    val title: String
)
