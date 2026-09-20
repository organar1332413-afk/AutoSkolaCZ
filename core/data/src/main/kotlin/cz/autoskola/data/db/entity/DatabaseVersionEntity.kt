package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "DatabaseVersion",
    primaryKeys = ["id"]
)
data class DatabaseVersionEntity(
    val id: String,
    val databaseVersion: String,
    val publicationDate: String?,
    val source: String,
    val retrievedAt: String,
    val importDate: Long,
    val sample: Boolean,
    val completeForB: Boolean,
    val packageSha256: String
)
