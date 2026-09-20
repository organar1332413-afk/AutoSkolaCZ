package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "QuestionReview",
    primaryKeys = ["questionId"],
    foreignKeys = [
    ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = QuestionRevisionEntity::class, parentColumns = ["id"], childColumns = ["lastRevisionId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["questionId"], unique = false), Index(value = ["lastRevisionId"], unique = false)]
)
data class QuestionReviewEntity(
    val questionId: String,
    val lastRevisionId: String,
    val correctStreak: Int,
    val lastCorrectAt: Long?,
    val nextReviewAt: Long,
    val masteredAt: Long?
)
