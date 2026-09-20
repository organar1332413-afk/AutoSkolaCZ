package cz.autoskola.data.db

import androidx.room.*
import cz.autoskola.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Query("SELECT * FROM DatabaseVersion WHERE id = (SELECT versionId FROM ActiveContent WHERE slot = 1)")
    fun activeVersion(): Flow<DatabaseVersionEntity?>
    @Query("SELECT * FROM DatabaseVersion WHERE id = :id")
    suspend fun version(id: String): DatabaseVersionEntity?
    @Query("SELECT * FROM QuestionRevision WHERE versionId = (SELECT versionId FROM ActiveContent WHERE slot = 1) ORDER BY questionId")
    fun questions(): Flow<List<QuestionRevisionEntity>>
    @Query("SELECT * FROM Answer WHERE revisionId IN (SELECT id FROM QuestionRevision WHERE versionId = (SELECT versionId FROM ActiveContent WHERE slot = 1)) ORDER BY position")
    fun answers(): Flow<List<AnswerEntity>>
    @Query("SELECT * FROM QuestionTranslation WHERE locale = :locale")
    fun translations(locale: String): Flow<List<QuestionTranslationEntity>>
    @Query("SELECT * FROM AnswerTranslation WHERE locale = :locale")
    fun answerTranslations(locale: String): Flow<List<AnswerTranslationEntity>>
    @Query("SELECT * FROM QuestionMedia WHERE revisionId IN (SELECT id FROM QuestionRevision WHERE versionId = (SELECT versionId FROM ActiveContent WHERE slot = 1)) ORDER BY position")
    fun media(): Flow<List<QuestionMediaEntity>>
    @Insert suspend fun insertVersion(item: DatabaseVersionEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(items: List<QuestionCategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertQuestions(items: List<QuestionEntity>)
    @Insert suspend fun insertRevisions(items: List<QuestionRevisionEntity>)
    @Insert suspend fun insertAnswers(items: List<AnswerEntity>)
    @Insert suspend fun insertTranslations(items: List<QuestionTranslationEntity>)
    @Insert suspend fun insertAnswerTranslations(items: List<AnswerTranslationEntity>)
    @Insert suspend fun insertMedia(items: List<QuestionMediaEntity>)
    @Insert suspend fun insertGroups(items: List<QuestionLicenceGroupEntity>)
    @Upsert suspend fun activate(item: ActiveContentEntity)
}
