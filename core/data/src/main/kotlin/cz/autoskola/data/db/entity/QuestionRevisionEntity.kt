package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "QuestionRevision",
    primaryKeys = ["id"],
    foreignKeys = [
    ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = DatabaseVersionEntity::class, parentColumns = ["id"], childColumns = ["versionId"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = QuestionCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["versionId", "questionId"], unique = true), Index(value = ["questionId"], unique = false), Index(value = ["versionId"], unique = false), Index(value = ["categoryId"], unique = false)]
)
data class QuestionRevisionEntity(
    val id: String,
    val questionId: String,
    val versionId: String,
    val categoryId: String,
    val textCs: String,
    val points: Int?,
    val source: String
)
