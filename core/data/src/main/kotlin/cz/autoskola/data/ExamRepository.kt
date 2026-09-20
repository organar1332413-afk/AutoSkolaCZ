package cz.autoskola.data
import androidx.room.withTransaction
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.db.entity.*
import cz.autoskola.domain.*
import kotlinx.coroutines.flow.first
import java.util.UUID
/** Local unfinished exams retain immutable revision IDs even after the active bank changes. */
class ExamRepository(private val db:AutoSkolaDatabase,private val clock:()->Long=System::currentTimeMillis) {
    private val dao=db.learning()
    suspend fun start(pack:QuestionPackage):Pair<String,ExamSession> = db.withTransaction {
        require(dao.unfinishedExam()==null) { "Resume or finish the previous exam" }
        val version=requireNotNull(db.content().activeVersion().first())
        require(version.id==pack.manifest.databaseVersion && !version.sample && version.completeForB)
        val session=ExamEngine.create(pack,clock())
        session.items.forEach { item ->
            val revision=requireNotNull(dao.revision(item.revisionId));val answers=dao.answers(item.revisionId)
            require(revision.versionId==version.id && revision.questionId==item.officialId && revision.points==item.points && revision.categoryId==item.category)
            require(answers.map { it.code }.toSet()==item.answerCodes.toSet() && answers.single { it.correct }.code==item.correctCode)
        }
        val id=UUID.randomUUID().toString()
        dao.insertExam(ExamAttemptEntity(id,session.version,"B-stage2-v1",session.startedAt,session.deadlineAt,null,null,"REAL"))
        dao.saveExamAnswers(session.items.mapIndexed { index,q->ExamAnswerEntity(id,index,q.revisionId,null,null) })
        id to session
    }
    suspend fun unfinished():Pair<String,ExamSession>? = db.withTransaction { val exam=dao.unfinishedExam() ?: return@withTransaction null;exam.id to resume(exam.id) }
    suspend fun resume(id:String):ExamSession = db.withTransaction {
        val original=load(id);val resumed=ExamEngine.resume(original,clock())
        if(original.completedAt==null && resumed.completedAt!=null) persist(id,resumed)
        resumed
    }
    suspend fun answer(id:String,revision:String,code:String):ExamSession = db.withTransaction {
        val session=load(id);val now=clock()
        if(session.completedAt==null && now>=session.deadlineAt) { val ended=ExamEngine.finish(session,now);persist(id,ended);return@withTransaction ended }
        val updated=ExamEngine.answer(session,revision,code,now);persist(id,updated);updated
    }
    suspend fun finish(id:String):ExamSession = db.withTransaction { val session=ExamEngine.finish(load(id),clock());persist(id,session);session }
    private suspend fun load(id:String):ExamSession {
        val exam=requireNotNull(dao.exam(id));require(exam.blueprintVersion=="B-stage2-v1")
        val answers=dao.examAnswers(id)
        require(answers.size==25 && answers.map { it.position }==(0..24).toList())
        val items=answers.map { a->val q=requireNotNull(dao.revision(a.revisionId));require(q.versionId==exam.versionId);val options=dao.answers(q.id);ExamItem(q.id,q.questionId,q.categoryId,requireNotNull(q.points),options.single { it.correct }.code,options.map { it.code }) }
        require(items.sumOf { it.points }==50)
        return ExamSession(exam.versionId,exam.startedAt,exam.deadlineAt,items,answers.mapNotNull { a->a.answerCode?.let { a.revisionId to it } }.toMap(),exam.completedAt)
    }
    private suspend fun persist(id:String,session:ExamSession) {
        val old=requireNotNull(dao.exam(id));if(old.completedAt!=null) return
        val complete=session.completedAt!=null
        dao.saveExamAnswers(session.items.mapIndexed { index,q->val code=session.answers[q.revisionId];ExamAnswerEntity(id,index,q.revisionId,code,if(complete) (if(code==q.correctCode) q.points else 0) else null) })
        dao.updateExam(old.copy(completedAt=session.completedAt,score=if(complete) ExamEngine.result(session).score else null))
        if(complete) session.items.forEach { q->
            val code=session.answers[q.revisionId];val correct=code==q.correctCode
            if(code!=null) dao.insertAttempt(QuestionAttemptEntity("exam:$id:${q.officialId}",q.revisionId,code,session.completedAt!!,correct,null,MaterialMode.CS_ONLY.name,LearningLevel.EXAM.name))
            val previous=dao.review(q.officialId)?.let { ReviewState(it.lastRevisionId,it.correctStreak,it.lastCorrectAt,it.masteredAt) }
            val next=ReviewPolicy.next(previous,q.revisionId,correct,session.completedAt!!,session.startedAt)
            dao.saveReview(QuestionReviewEntity(q.officialId,q.revisionId,next.streak,next.lastCorrectAt,session.completedAt!!+ReviewPolicy.separationMs,next.masteredAt))
        }
    }
}
