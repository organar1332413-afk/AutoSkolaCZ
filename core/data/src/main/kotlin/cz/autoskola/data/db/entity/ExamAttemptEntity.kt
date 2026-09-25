package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "ExamAttempt",
    primaryKeys = ["id"],
    foreignKeys = [
    ForeignKey(entity = DatabaseVersionEntity::class, parentColumns = ["id"], childColumns = ["versionId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["versionId"], unique = false)]
)
data class ExamAttemptEntity(
    val id: String,
    val versionId: String,
    val blueprintVersion: String,
    val startedAt: Long,
    val deadlineAt: Long,
    val completedAt: Long?,
    val score: Int?,
    val mode: String,
    @ColumnInfo(defaultValue = "'B'") val licenceGroup: String = "B",
    @ColumnInfo(defaultValue = "25") val questionCount: Int = 25,
    @ColumnInfo(defaultValue = "50") val maxPoints: Int = 50,
    @ColumnInfo(defaultValue = "43") val passPoints: Int = 43
)
