package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "QuestionCategory",
    primaryKeys = ["id"]
)
data class QuestionCategoryEntity(
    val id: String,
    val nameCs: String
)
