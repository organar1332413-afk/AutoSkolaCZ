package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "Answer",
    primaryKeys = ["revisionId", "code"],
    foreignKeys = [
    ForeignKey(entity = QuestionRevisionEntity::class, parentColumns = ["id"], childColumns = ["revisionId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["revisionId"], unique = false)]
)
data class AnswerEntity(
    val revisionId: String,
    val code: String,
    val textCs: String,
    val correct: Boolean,
    val position: Int
)
