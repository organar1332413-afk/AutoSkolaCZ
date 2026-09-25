package cz.autoskola.data
import androidx.room.withTransaction
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.db.entity.*
import cz.autoskola.domain.*
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.io.File

/** Local unfinished exams retain immutable revision IDs even after the active bank changes. */
class ExamRepository(private val db:AutoSkolaDatabase,private val mediaRoot:File?=null,private val clock:()->Long=System::currentTimeMillis) {
    private val dao=db.learning()

    suspend fun availability(group:LicenceGroup):ExamAvailability {
        val pack=activePack() ?: return ExamAvailability.CONTENT_INCOMPLETE
        val state=GroupReadinessPolicy.availability(pack,ExamConfigurationProvider.forGroup(group))
        return if(state==ExamAvailability.READY && !mediaAvailable(pack,group)) ExamAvailability.MEDIA_INCOMPLETE else state
    }

    suspend fun start(group:LicenceGroup):Pair<String,ExamSession> = start(group,requireNotNull(activePack()))

    suspend fun start(group:LicenceGroup,pack:QuestionPackage):Pair<String,ExamSession> = db.withTransaction {
        require(dao.unfinishedExam()==null) { "Resume or finish the previous exam" }
        val version=requireNotNull(db.content().activeVersion().first())
        require(version.id==pack.manifest.databaseVersion && !version.sample)
        require(mediaAvailable(pack,group)) { "Required exam media is unavailable" }
        val config=ExamConfigurationProvider.forGroup(group)
        val readiness=requireNotNull(db.content().readiness(version.id,group.code)) { "Group eligibility is unknown" }
        require(readiness.eligibilityComplete && readiness.contentComplete && readiness.mediaComplete)
        val session=ExamEngine.create(pack,config,clock())
        session.items.forEach { item ->
            val revision=requireNotNull(dao.revision(item.revisionId))
            val answers=dao.answers(item.revisionId)
            require(revision.versionId==version.id && revision.questionId==item.officialId && revision.points==item.points && revision.categoryId==item.category)
            require(answers.map { it.code }.toSet()==item.answerCodes.toSet() && answers.single { it.correct }.code==item.correctCode)
        }
        val id=UUID.randomUUID().toString()
        dao.insertExam(ExamAttemptEntity(id,session.version,session.blueprintVersion,session.startedAt,session.deadlineAt,null,null,"REAL",
            session.licenceGroup.code,session.questionCount,session.maxPoints,session.passPoints))
        dao.saveExamAnswers(session.items.mapIndexed { index,q->ExamAnswerEntity(id,index,q.revisionId,null,null) })
        id to session
    }

    suspend fun unfinished():Pair<String,ExamSession>? = db.withTransaction {
        val exam=dao.unfinishedExam() ?: return@withTransaction null
        exam.id to resume(exam.id)
    }

    suspend fun resume(id:String):ExamSession = db.withTransaction {
        val original=load(id)
        val resumed=ExamEngine.resume(original,clock())
        if(original.completedAt==null && resumed.completedAt!=null) persist(id,resumed)
        resumed
    }

    suspend fun answer(id:String,revision:String,code:String):ExamSession = db.withTransaction {
        val session=load(id)
        val now=clock()
        if(session.completedAt==null && now>=session.deadlineAt) {
            val ended=ExamEngine.finish(session,now)
            persist(id,ended)
            return@withTransaction ended
        }
        val updated=ExamEngine.answer(session,revision,code,now)
        persist(id,updated)
        updated
    }

    suspend fun finish(id:String):ExamSession = db.withTransaction {
        val session=ExamEngine.finish(load(id),clock())
        persist(id,session)
        session
    }

    suspend fun cards(session:ExamSession):List<QuestionCard> = db.withTransaction {
        session.items.map { item->
            val q=requireNotNull(dao.revision(item.revisionId))
            val options=dao.answers(q.id).sortedBy { it.position }
            require(q.questionId==item.officialId && q.categoryId==item.category && q.points==item.points)
            require(options.map { it.code }.toSet()==item.answerCodes.toSet())
            val media=db.content().mediaForRevision(q.id).map {
                MediaReference(it.path,it.sha256,it.mimeType,it.answerCode)
            }
            QuestionCard(
                revisionId=q.id,
                officialId=q.questionId,
                category=q.categoryId,
                textCs=q.textCs,
                points=q.points,
                answers=options.map { OfficialAnswer(it.code,it.textCs,it.correct) },
                translation=null,
                media=media,
                licenceGroups=db.content().groupsForRevision(q.id).map { it.licenceGroup },
                source=q.source
            )
        }
    }

    private suspend fun activePack():QuestionPackage? {
        val content=db.content()
        val version=content.activeVersion().first() ?: return null
        val revisions=content.questions().first()
        val answers=content.answers().first()
        val groups=content.licenceGroups().first()
        val media=content.media().first()
        val readiness=content.activeReadiness().first()
        return QuestionPackage(
            manifest=ContentManifest(
                formatVersion=if(readiness.any { it.blueprintVersion=="B-stage2-v1" }) 1 else 2,
                databaseVersion=version.databaseVersion,
                publicationDate=version.publicationDate,
                source=version.source,
                retrievedAt=version.retrievedAt,
                sample=version.sample,
                groupReadiness=readiness.map {
                    GroupReadiness(it.licenceGroup,it.blueprintVersion,it.eligibilityComplete,it.contentComplete,it.mediaComplete,it.source)
                }
            ),
            questions=revisions.map { q->
                OfficialQuestion(
                    officialId=q.questionId,
                    category=q.categoryId,
                    textCs=q.textCs,
                    points=q.points,
                    licenceGroups=groups.filter { it.revisionId==q.id }.map { it.licenceGroup },
                    answers=answers.filter { it.revisionId==q.id }.sortedBy { it.position }.map {
                        OfficialAnswer(it.code,it.textCs,it.correct)
                    },
                    source=q.source,
                    eligibility=groups.filter { it.revisionId==q.id }.map { QuestionEligibility(it.licenceGroup,it.source.ifBlank { q.source }) },
                    media=media.filter { it.revisionId==q.id }.map { MediaReference(it.path.substringAfter('/'),it.sha256,it.mimeType,it.answerCode) }
                )
            }
        )
    }

    private suspend fun mediaAvailable(pack:QuestionPackage,group:LicenceGroup):Boolean {
        val media=pack.questions.filter { group.code in it.licenceGroups }.flatMap { it.media }
        if(media.isEmpty()) return true
        val root=mediaRoot?.canonicalFile ?: return false
        val hashDir=db.content().version(pack.manifest.databaseVersion)?.packageSha256 ?: return false
        return media.all { ref ->
            if(ref.mimeType !in GroupReadinessPolicy.supportedImageMimeTypes) return@all false
            val file=File(root,"$hashDir/${ref.path}").canonicalFile
            file.path.startsWith(root.path+File.separator) && file.isFile
        }
    }

    private suspend fun load(id:String):ExamSession {
        val exam=requireNotNull(dao.exam(id))
        val group=persistedLicenceGroup(exam.licenceGroup)
        require(ExamConfigurationProvider.supports(group,exam.blueprintVersion)) { "Unsupported persisted exam blueprint" }
        require(exam.questionCount>0 && exam.maxPoints>0 && exam.passPoints in 1..exam.maxPoints) { "Invalid persisted exam snapshot" }
        val answers=dao.examAnswers(id)
        require(answers.size==exam.questionCount && answers.map { it.position }==(0 until exam.questionCount).toList())
        require(answers.map { it.revisionId }.distinct().size==answers.size) { "Duplicate exam revision" }
        val items=answers.map { a->
            val q=requireNotNull(dao.revision(a.revisionId))
            require(q.versionId==exam.versionId)
            val options=dao.answers(q.id)
            require(options.size in 2..3 && options.map { it.code }.sorted()==listOf("A","B","C").take(options.size) && options.count { it.correct }==1)
            require(a.answerCode==null || options.any { it.code==a.answerCode }) { "Invalid persisted answer code" }
            ExamItem(
                q.id,
                q.questionId,
                q.categoryId,
                requireNotNull(q.points),
                options.single { it.correct }.code,
                options.map { it.code }
            )
        }
        require(items.map { it.officialId }.distinct().size==items.size && items.sumOf { it.points }==exam.maxPoints)
        return ExamSession(
            exam.versionId,
            exam.startedAt,
            exam.deadlineAt,
            items,
            answers.mapNotNull { a->a.answerCode?.let { a.revisionId to it } }.toMap(),
            exam.completedAt,
            group,
            exam.blueprintVersion,exam.questionCount,exam.maxPoints,exam.passPoints
        )
    }

    private suspend fun persist(id:String,session:ExamSession) {
        val old=requireNotNull(dao.exam(id))
        if(old.completedAt!=null) return
        val complete=session.completedAt!=null
        dao.saveExamAnswers(session.items.mapIndexed { index,q->
            val code=session.answers[q.revisionId]
            ExamAnswerEntity(id,index,q.revisionId,code,if(complete) (if(code==q.correctCode) q.points else 0) else null)
        })
        dao.updateExam(old.copy(completedAt=session.completedAt,score=if(complete) ExamEngine.result(session).score else null))
        if(complete) session.items.forEach { q->
            val code=session.answers[q.revisionId]
            val correct=code==q.correctCode
            if(code!=null) dao.insertAttempt(
                QuestionAttemptEntity(
                    "exam:" + id + ":" + q.officialId,
                    q.revisionId,
                    code,
                    session.completedAt!!,
                    correct,
                    null,
                    MaterialMode.CS_ONLY.name,
                    LearningLevel.EXAM.name
                )
            )
            val previous=dao.review(q.officialId)?.let {
                ReviewState(it.lastRevisionId,it.correctStreak,it.lastCorrectAt,it.masteredAt)
            }
            val next=ReviewPolicy.next(previous,q.revisionId,correct,session.completedAt!!,session.startedAt)
            dao.saveReview(
                QuestionReviewEntity(
                    q.officialId,
                    q.revisionId,
                    next.streak,
                    next.lastCorrectAt,
                    session.completedAt!!+ReviewPolicy.separationMs,
                    next.masteredAt
                )
            )
        }
    }
}
