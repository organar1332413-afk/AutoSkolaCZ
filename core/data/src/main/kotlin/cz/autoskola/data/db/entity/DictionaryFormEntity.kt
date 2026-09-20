package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "DictionaryForm",
    primaryKeys = ["wordId", "form"],
    foreignKeys = [
    ForeignKey(entity = DictionaryWordEntity::class, parentColumns = ["id"], childColumns = ["wordId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["wordId"], unique = false)]
)
data class DictionaryFormEntity(
    val wordId: String,
    val form: String
)
