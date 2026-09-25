package cz.autoskola.data
import androidx.room.withTransaction
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.db.entity.*
import cz.autoskola.domain.*
import kotlinx.coroutines.flow.combine
import java.util.UUID

class LearningRepository(private val db: AutoSkolaDatabase) {
    private val dao = db.learning()
    val snapshot = combine(
        combine(dao.attempts(), dao.favorites(), dao.reviews()) { a,f,r -> Triple(a,f,r) },
        combine(dao.progress(), dao.scores(), dao.completedExams()) { p,s,e -> Triple(p,s,e) }
    ) { state,extra ->
        LearningSnapshot(
            attempts=state.first.map { AttemptRecord(it.id,it.revisionId,it.correct,ErrorReason.entries.find { e -> e.name==it.errorReason },it.createdAt,it.sample,it.category,it.answerCode,it.examLicenceGroup?.let(::persistedLicenceGroup)) },
            favorites=state.second.map { it.questionId }.toSet(),
            reviews=state.third.associate { it.questionId to ReviewState(it.lastRevisionId,it.correctStreak,it.lastCorrectAt,it.masteredAt) },
            progress=extra.first.map { LessonProgress(it.lessonId,it.blockPosition,it.completedAt,it.updatedAt) },
            examScores=extra.second,
            examHistory=extra.third.map { exam ->
                ExamHistoryItem(
                    id=exam.id,
                    licenceGroup=persistedLicenceGroup(exam.licenceGroup),
                    databaseVersion=exam.versionId,
                    startedAt=exam.startedAt,
                    completedAt=requireNotNull(exam.completedAt),
                    score=requireNotNull(exam.score),
                    maxPoints=exam.maxPoints,
                    passPoints=exam.passPoints,
                    durationSeconds=((requireNotNull(exam.completedAt)-exam.startedAt).coerceAtLeast(0L))/1000L
                )
            }
        )
    }

    suspend fun answer(attemptId: String, revisionId: String, code: String, settings: UserSettings, sessionStart: Long): Boolean = db.withTransaction {
        val prior = dao.attempt(attemptId)
        if (prior != null) {
            require(prior.revisionId==revisionId && prior.answerCode==code)
            return@withTransaction prior.correct
        }
        val q = requireNotNull(dao.revision(revisionId))
        val answers=dao.answers(revisionId)
        val correct = answers.single { it.code==code }.correct
        val now=System.currentTimeMillis()
        dao.insertAttempt(QuestionAttemptEntity(attemptId,revisionId,code,now,correct,null,settings.materialMode.name,settings.level.name))
        val previous=dao.review(q.questionId)?.let { ReviewState(it.lastRevisionId,it.correctStreak,it.lastCorrectAt,it.masteredAt) }
        val next=ReviewPolicy.next(previous,revisionId,correct,now,sessionStart)
        dao.saveReview(QuestionReviewEntity(q.questionId,revisionId,next.streak,next.lastCorrectAt,now+ReviewPolicy.separationMs,next.masteredAt))
        correct
    }

    suspend fun reason(id: String, reason: ErrorReason) = dao.reason(id,reason.name)
    suspend fun favorite(id: String, value: Boolean) {
        if(value) dao.favorite(FavoriteQuestionEntity(id,System.currentTimeMillis())) else dao.unfavorite(id)
    }
    suspend fun progress(id: String, position: Int, complete: Boolean) {
        val now=System.currentTimeMillis()
        dao.saveProgress(LearningProgressEntity(id,position,if(complete) now else null,now))
    }
    suspend fun wordReview(id: String, correct: Boolean) = db.withTransaction {
        requireNotNull(dao.savedWord(id))
        dao.wordReview(WordReviewEntity(UUID.randomUUID().toString(),id,System.currentTimeMillis(),correct))
        dao.wordCounters(id,if(correct) 1 else 0)
    }
    suspend fun removeWord(id: String) = dao.removeWord(id)

    fun lessons(locale: String?) = combine(
        dao.lessons(),
        dao.lessonTitles(locale ?: ""),
        dao.blocks(),
        dao.blockTexts(locale ?: ""),
        dao.lessonQuestions()
    ) { ls,ts,bs,bts,qs ->
        ls.map { l ->
            LessonCard(
                l.id,l.topic,l.titleCs,ts.find { it.lessonId==l.id }?.title,locale,l.source,l.reviewStatus,l.estimatedMinutes,
                bs.filter { it.lessonId==l.id }.map { b -> LessonBlockCard(b.id,b.kind,b.textCs,bts.find { it.blockId==b.id }?.text,b.mediaPath) },
                qs.filter { it.lessonId==l.id }.map { it.questionId }
            )
        }
    }
}
