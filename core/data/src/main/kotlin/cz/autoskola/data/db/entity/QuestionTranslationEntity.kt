package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "QuestionTranslation",
    primaryKeys = ["revisionId", "locale"],
    foreignKeys = [
    ForeignKey(entity = QuestionRevisionEntity::class, parentColumns = ["id"], childColumns = ["revisionId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["revisionId"], unique = false)]
)
data class QuestionTranslationEntity(
    val revisionId: String,
    val locale: String,
    val text: String,
    val explanation: String,
    val reviewStatus: String
)
