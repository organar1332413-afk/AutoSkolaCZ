package cz.autoskola.data.db.entity

import androidx.room.*

@Entity(
    tableName = "LessonBlockTranslation",
    primaryKeys = ["blockId", "locale"],
    foreignKeys = [
    ForeignKey(entity = LessonBlockEntity::class, parentColumns = ["id"], childColumns = ["blockId"], onDelete = ForeignKey.NO_ACTION)
],
    indices = [Index(value = ["blockId"], unique = false)]
)
data class LessonBlockTranslationEntity(
    val blockId: String,
    val locale: String,
    val text: String
)
