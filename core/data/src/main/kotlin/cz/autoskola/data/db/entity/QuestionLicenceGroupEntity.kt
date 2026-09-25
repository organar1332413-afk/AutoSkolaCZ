package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "QuestionLicenceGroup",
    primaryKeys = ["revisionId", "licenceGroup"],
    foreignKeys = [
    ForeignKey(entity = QuestionRevisionEntity::class, parentColumns = ["id"], childColumns = ["revisionId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["revisionId"], unique = false)]
)
data class QuestionLicenceGroupEntity(
    val revisionId: String,
    val licenceGroup: String,
    @ColumnInfo(defaultValue = "''") val source: String = ""
)
