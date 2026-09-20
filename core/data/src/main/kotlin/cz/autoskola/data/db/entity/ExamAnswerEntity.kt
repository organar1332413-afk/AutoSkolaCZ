package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "ExamAnswer",
    primaryKeys = ["examId", "position"],
    foreignKeys = [
    ForeignKey(entity = ExamAttemptEntity::class, parentColumns = ["id"], childColumns = ["examId"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = QuestionRevisionEntity::class, parentColumns = ["id"], childColumns = ["revisionId"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = AnswerEntity::class, parentColumns = ["revisionId", "code"], childColumns = ["revisionId", "answerCode"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["examId", "revisionId"], unique = true), Index(value = ["examId"], unique = false), Index(value = ["revisionId"], unique = false), Index(value = ["revisionId", "answerCode"], unique = false)]
)
data class ExamAnswerEntity(
    val examId: String,
    val position: Int,
    val revisionId: String,
    val answerCode: String?,
    val awardedPoints: Int?
)
