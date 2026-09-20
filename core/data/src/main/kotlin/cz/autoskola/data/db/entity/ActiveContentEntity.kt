package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "ActiveContent",
    primaryKeys = ["slot"],
    foreignKeys = [
    ForeignKey(entity = DatabaseVersionEntity::class, parentColumns = ["id"], childColumns = ["versionId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["versionId"], unique = false)]
)
data class ActiveContentEntity(
    val slot: Int,
    val versionId: String
)
