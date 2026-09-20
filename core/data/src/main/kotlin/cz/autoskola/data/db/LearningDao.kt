package cz.autoskola.data.db
import androidx.room.*
import cz.autoskola.data.db.entity.*
import kotlinx.coroutines.flow.Flow

data class AttemptDetails(val id: String, val revisionId: String, val correct: Boolean, val errorReason: String?, val createdAt: Long, val sample: Boolean, val category: String, val answerCode: String?)
@Dao interface LearningDao {
    @Query("SELECT * FROM ExamAttempt WHERE id=:id") suspend fun exam(id:String):ExamAttemptEntity?
    @Query("SELECT * FROM ExamAttempt WHERE completedAt IS NULL ORDER BY startedAt DESC LIMIT 1") suspend fun unfinishedExam():ExamAttemptEntity?
    @Query("SELECT * FROM ExamAnswer WHERE examId=:id ORDER BY position") suspend fun examAnswers(id:String):List<ExamAnswerEntity>
    @Insert suspend fun insertExam(item:ExamAttemptEntity)
    @Update suspend fun updateExam(item:ExamAttemptEntity)
    @Upsert suspend fun saveExamAnswers(items:List<ExamAnswerEntity>)

    @Query("SELECT a.id,a.revisionId,a.correct,a.errorReason,a.createdAt,v.sample,q.categoryId AS category,a.answerCode FROM QuestionAttempt a JOIN QuestionRevision q ON q.id=a.revisionId JOIN DatabaseVersion v ON v.id=q.versionId UNION ALL SELECT ('unanswered:' || e.id || ':' || q.questionId) AS id,q.id AS revisionId,0 AS correct,NULL AS errorReason,e.completedAt AS createdAt,v.sample,q.categoryId AS category,NULL AS answerCode FROM ExamAnswer ea JOIN ExamAttempt e ON e.id=ea.examId JOIN QuestionRevision q ON q.id=ea.revisionId JOIN DatabaseVersion v ON v.id=q.versionId WHERE e.completedAt IS NOT NULL AND ea.answerCode IS NULL ORDER BY createdAt DESC") fun attempts(): Flow<List<AttemptDetails>>
    @Query("SELECT * FROM QuestionAttempt WHERE id=:id") suspend fun attempt(id: String): QuestionAttemptEntity?
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertAttempt(item: QuestionAttemptEntity): Long
    @Query("UPDATE QuestionAttempt SET errorReason=:reason WHERE id=:id AND correct=0") suspend fun reason(id: String, reason: String)
    @Query("SELECT * FROM QuestionReview") fun reviews(): Flow<List<QuestionReviewEntity>>
    @Query("SELECT * FROM QuestionReview WHERE questionId=:id") suspend fun review(id: String): QuestionReviewEntity?
    @Upsert suspend fun saveReview(item: QuestionReviewEntity)
    @Query("SELECT * FROM FavoriteQuestion") fun favorites(): Flow<List<FavoriteQuestionEntity>>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun favorite(item: FavoriteQuestionEntity)
    @Query("DELETE FROM FavoriteQuestion WHERE questionId=:id") suspend fun unfavorite(id: String)
    @Query("SELECT * FROM LearningProgress ORDER BY updatedAt DESC") fun progress(): Flow<List<LearningProgressEntity>>
    @Upsert suspend fun saveProgress(item: LearningProgressEntity)
    @Query("SELECT score FROM ExamAttempt WHERE completedAt IS NOT NULL AND score IS NOT NULL ORDER BY completedAt DESC") fun scores(): Flow<List<Int>>
    @Query("SELECT * FROM QuestionRevision WHERE id=:id") suspend fun revision(id: String): QuestionRevisionEntity?
    @Query("SELECT * FROM Answer WHERE revisionId=:id") suspend fun answers(id: String): List<AnswerEntity>
    @Query("SELECT * FROM SavedWord WHERE wordId=:id") suspend fun savedWord(id: String): SavedWordEntity?
    @Insert suspend fun wordReview(item: WordReviewEntity)
    @Query("UPDATE SavedWord SET repetitions=repetitions+1,correctCount=correctCount+:correct WHERE wordId=:id") suspend fun wordCounters(id: String, correct: Int)
    @Query("DELETE FROM SavedWord WHERE wordId=:id") suspend fun removeWord(id: String)
    @Query("SELECT * FROM Lesson ORDER BY id") fun lessons(): Flow<List<LessonEntity>>
    @Query("SELECT * FROM LessonTranslation WHERE locale=:locale") fun lessonTitles(locale: String): Flow<List<LessonTranslationEntity>>
    @Query("SELECT * FROM LessonBlock ORDER BY position") fun blocks(): Flow<List<LessonBlockEntity>>
    @Query("SELECT * FROM LessonBlockTranslation WHERE locale=:locale") fun blockTexts(locale: String): Flow<List<LessonBlockTranslationEntity>>
    @Query("SELECT * FROM LessonQuestion ORDER BY position") fun lessonQuestions(): Flow<List<LessonQuestionEntity>>
}
