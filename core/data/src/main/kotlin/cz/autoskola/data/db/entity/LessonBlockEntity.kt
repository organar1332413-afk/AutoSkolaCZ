package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "LessonBlock",
    primaryKeys = ["id"],
    foreignKeys = [
    ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lessonId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["lessonId", "position"], unique = true), Index(value = ["lessonId"], unique = false)]
)
data class LessonBlockEntity(
    val id: String,
    val lessonId: String,
    val position: Int,
    val kind: String,
    val textCs: String,
    val mediaPath: String?
)
