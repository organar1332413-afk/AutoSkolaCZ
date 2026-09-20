package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "DictionaryWord",
    primaryKeys = ["id"]
)
data class DictionaryWordEntity(
    val id: String,
    val lemma: String,
    val context: String,
    val exampleCs: String,
    val reviewStatus: String
)
