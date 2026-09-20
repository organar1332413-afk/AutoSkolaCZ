package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "DictionaryTranslation",
    primaryKeys = ["wordId", "locale"],
    foreignKeys = [
    ForeignKey(entity = DictionaryWordEntity::class, parentColumns = ["id"], childColumns = ["wordId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["wordId"], unique = false)]
)
data class DictionaryTranslationEntity(
    val wordId: String,
    val locale: String,
    val translation: String,
    val meaning: String,
    val exampleTranslation: String
)
