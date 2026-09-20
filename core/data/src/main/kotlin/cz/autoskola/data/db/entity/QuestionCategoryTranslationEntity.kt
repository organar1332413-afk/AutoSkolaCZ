package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "QuestionCategoryTranslation",
    primaryKeys = ["categoryId", "locale"],
    foreignKeys = [
    ForeignKey(entity = QuestionCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["categoryId"], unique = false)]
)
data class QuestionCategoryTranslationEntity(
    val categoryId: String,
    val locale: String,
    val name: String
)
