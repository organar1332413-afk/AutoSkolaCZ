package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "QuestionMedia",
    primaryKeys = ["id"],
    foreignKeys = [
    ForeignKey(entity = QuestionRevisionEntity::class, parentColumns = ["id"], childColumns = ["revisionId"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = AnswerEntity::class, parentColumns = ["revisionId", "code"], childColumns = ["revisionId", "answerCode"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["revisionId"], unique = false), Index(value = ["revisionId", "answerCode"], unique = false)]
)
data class QuestionMediaEntity(
    val id: String,
    val revisionId: String,
    val answerCode: String?,
    val path: String,
    val sha256: String,
    val mimeType: String,
    val position: Int
)
