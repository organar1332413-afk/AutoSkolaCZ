package cz.autoskola.data.db
import androidx.room.Database
import androidx.room.RoomDatabase
import cz.autoskola.data.db.entity.*
@Database(entities = [
    DatabaseVersionEntity::class,
    ActiveContentEntity::class,
    QuestionCategoryEntity::class,
    QuestionCategoryTranslationEntity::class,
    QuestionEntity::class,
    QuestionRevisionEntity::class,
    QuestionLicenceGroupEntity::class,
    ContentGroupReadinessEntity::class,
    AnswerEntity::class,
    QuestionTranslationEntity::class,
    AnswerTranslationEntity::class,
    QuestionMediaEntity::class,
    TrafficSignEntity::class,
    TrafficSignTranslationEntity::class,
    LessonEntity::class,
    LessonTranslationEntity::class,
    LessonBlockEntity::class,
    LessonBlockTranslationEntity::class,
    LessonQuestionEntity::class,
    DictionaryWordEntity::class,
    DictionaryFormEntity::class,
    DictionaryTranslationEntity::class,
    SavedWordEntity::class,
    FavoriteQuestionEntity::class,
    QuestionAttemptEntity::class,
    ExamAttemptEntity::class,
    ExamAnswerEntity::class,
    LearningProgressEntity::class,
    QuestionReviewEntity::class,
    WordReviewEntity::class
], version = 2, exportSchema = true)
abstract class AutoSkolaDatabase : RoomDatabase() {
    abstract fun content(): ContentDao
    abstract fun words(): WordsDao
    abstract fun learning(): LearningDao
}
