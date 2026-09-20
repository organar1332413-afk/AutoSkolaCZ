package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "FavoriteQuestion",
    primaryKeys = ["questionId"],
    foreignKeys = [
    ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["questionId"], unique = false)]
)
data class FavoriteQuestionEntity(
    val questionId: String,
    val addedAt: Long
)
