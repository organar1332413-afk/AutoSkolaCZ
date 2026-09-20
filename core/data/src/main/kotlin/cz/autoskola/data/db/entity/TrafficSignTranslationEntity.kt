package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "TrafficSignTranslation",
    primaryKeys = ["signId", "locale"],
    foreignKeys = [
    ForeignKey(entity = TrafficSignEntity::class, parentColumns = ["id"], childColumns = ["signId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["signId"], unique = false)]
)
data class TrafficSignTranslationEntity(
    val signId: String,
    val locale: String,
    val name: String,
    val explanation: String
)
