package cz.autoskola.domain
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable data class ExamItem(val revisionId: String, val officialId: String, val category: String, val points: Int, val correctCode: String, val answerCodes: List<String>)
@Serializable data class ExamSession(val version: String, val startedAt: Long, val deadlineAt: Long, val items: List<ExamItem>, val answers: Map<String,String> = emptyMap(), val completedAt: Long? = null)
data class ExamResult(val score: Int, val passed: Boolean, val lostPoints: Int, val wrongByCategory: Map<String,Int>)
/** Immutable revision snapshot, serializable for persistence. Fixtures belong exclusively to tests. */
object ExamEngine {
    fun create(pack: QuestionPackage, now: Long, random: Random = Random.Default): ExamSession {
        PackageValidator.validate(pack)
        require(ExamBlueprint.canAssemble(pack)) { "Verified complete category B bank required" }
        val items = ExamBlueprint.counts.flatMap { (category,count) ->
            pack.questions.filter { it.category == category && "B" in it.licenceGroups && it.points != null }.shuffled(random).take(count).map { q ->
                ExamItem(pack.manifest.databaseVersion + ":" + q.officialId, q.officialId, q.category, q.points!!, q.answers.single { it.correct }.code, q.answers.map { it.code })
            }
        }
        require(items.size == 25 && items.map { it.officialId }.distinct().size == 25)
        require(items.sumOf { it.points } == ExamBlueprint.maxPoints) { "Invalid verified score distribution" }
        return ExamSession(pack.manifest.databaseVersion, now, now + ExamBlueprint.minutes * 60_000L, items)
    }
    fun answer(session: ExamSession, revisionId: String, code: String, now: Long): ExamSession {
        require(session.completedAt == null && now < session.deadlineAt && now >= session.startedAt)
        require(code in session.items.single { it.revisionId == revisionId }.answerCodes)
        return session.copy(answers = session.answers + (revisionId to code))
    }
    fun remainingSeconds(session: ExamSession, now: Long) = ((session.deadlineAt - now).coerceAtLeast(0) + 999) / 1000
    fun finish(session: ExamSession, now: Long) = session.copy(completedAt = session.completedAt ?: now.coerceAtLeast(session.startedAt).coerceAtMost(session.deadlineAt))
    fun result(session: ExamSession): ExamResult {
        require(session.completedAt != null) { "Results are unavailable during an exam" }
        val wrong = session.items.filter { session.answers[it.revisionId] != it.correctCode }
        val lost = wrong.sumOf { it.points }; val score = ExamBlueprint.maxPoints - lost
        return ExamResult(score, score >= ExamBlueprint.passPoints, lost, wrong.groupingBy { it.category }.eachCount())
    }
    fun resume(session: ExamSession, now: Long) = if (session.completedAt == null && now >= session.deadlineAt) finish(session, now) else session
}
