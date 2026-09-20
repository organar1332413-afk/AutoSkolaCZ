package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "WordReview",
    primaryKeys = ["id"],
    foreignKeys = [
    ForeignKey(entity = DictionaryWordEntity::class, parentColumns = ["id"], childColumns = ["wordId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["wordId"], unique = false)]
)
data class WordReviewEntity(
    val id: String,
    val wordId: String,
    val createdAt: Long,
    val correct: Boolean
)
