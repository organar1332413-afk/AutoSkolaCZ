package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "LessonQuestion",
    primaryKeys = ["lessonId", "questionId"],
    foreignKeys = [
    ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lessonId"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["lessonId"], unique = false), Index(value = ["questionId"], unique = false)]
)
data class LessonQuestionEntity(
    val lessonId: String,
    val questionId: String,
    val position: Int
)
