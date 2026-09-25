package cz.autoskola.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "ContentGroupReadiness",
    primaryKeys = ["versionId", "licenceGroup"],
    foreignKeys = [ForeignKey(entity = DatabaseVersionEntity::class, parentColumns = ["id"], childColumns = ["versionId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index("versionId")]
)
data class ContentGroupReadinessEntity(
    val versionId: String,
    val licenceGroup: String,
    val blueprintVersion: String,
    val eligibilityComplete: Boolean,
    val contentComplete: Boolean,
    val mediaComplete: Boolean,
    val source: String
)
