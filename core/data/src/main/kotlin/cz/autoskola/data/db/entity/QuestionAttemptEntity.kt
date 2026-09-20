package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "QuestionAttempt",
    primaryKeys = ["id"],
    foreignKeys = [
    ForeignKey(entity = AnswerEntity::class, parentColumns = ["revisionId", "code"], childColumns = ["revisionId", "answerCode"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["revisionId", "answerCode"], unique = false)]
)
data class QuestionAttemptEntity(
    val id: String,
    val revisionId: String,
    val answerCode: String,
    val createdAt: Long,
    val correct: Boolean,
    val errorReason: String?,
    val materialMode: String,
    val level: String
)
