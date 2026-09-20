package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "SavedWord",
    primaryKeys = ["wordId"],
    foreignKeys = [
    ForeignKey(entity = DictionaryWordEntity::class, parentColumns = ["id"], childColumns = ["wordId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["wordId"], unique = false)]
)
data class SavedWordEntity(
    val wordId: String,
    val addedAt: Long,
    val repetitions: Int,
    val correctCount: Int,
    val nextReviewAt: Long?
)
