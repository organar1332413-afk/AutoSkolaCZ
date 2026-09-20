package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "TrafficSign",
    primaryKeys = ["id"]
)
data class TrafficSignEntity(
    val id: String,
    val officialCode: String,
    val nameCs: String,
    val descriptionCs: String,
    val imagePath: String,
    val source: String,
    val publicationDate: String?,
    val reviewStatus: String
)
