package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "AnswerTranslation",
    primaryKeys = ["revisionId", "answerCode", "locale"],
    foreignKeys = [
    ForeignKey(entity = AnswerEntity::class, parentColumns = ["revisionId", "code"], childColumns = ["revisionId", "answerCode"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["revisionId", "answerCode"], unique = false)]
)
data class AnswerTranslationEntity(
    val revisionId: String,
    val answerCode: String,
    val locale: String,
    val text: String
)
