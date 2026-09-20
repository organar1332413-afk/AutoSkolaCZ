package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "Question",
    primaryKeys = ["id"],
    indices = [Index(value = ["officialId"], unique = true)]
)
data class QuestionEntity(
    val id: String,
    val officialId: String
)
