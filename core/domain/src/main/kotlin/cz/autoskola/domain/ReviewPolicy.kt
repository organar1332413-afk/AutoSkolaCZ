package cz.autoskola.domain

data class ReviewState(val revisionId: String, val streak: Int = 0, val lastCorrectAt: Long? = null, val masteredAt: Long? = null)
object ReviewPolicy {
    // An explicit time separation complements the session boundary, without a Room migration.
    const val separationMs = 6 * 60 * 60 * 1000L
    fun next(previous: ReviewState?, revision: String, correct: Boolean, at: Long, sessionStartedAt: Long): ReviewState {
        val old = previous?.takeIf { it.revisionId == revision } ?: ReviewState(revision)
        if (!correct) return ReviewState(revision)
        val last = old.lastCorrectAt
        if (last != null && (last >= sessionStartedAt || at - last < separationMs)) return old
        val streak = (old.streak + 1).coerceAtMost(3)
        return ReviewState(revision, streak, at, if (streak >= 3) old.masteredAt ?: at else null)
    }
}
enum class QuestionFilter { ALL, MISTAKES, DOUBTFUL, KNOWN, FAVORITES, UNSEEN }
data class QuestionProgress(val questionId: String, val attempted: Boolean, val mistake: Boolean, val streak: Int)
data class AttemptRecord(val id: String, val revisionId: String, val correct: Boolean, val reason: ErrorReason?, val createdAt: Long, val sample: Boolean, val category: String, val answerCode: String? = null)
data class Statistics(val attempted: Int, val total: Int, val correctPercent: Int?, val attempts: Int, val sampleAttempts: Int, val exams: Int, val averageScore: Double?, val recentScores: List<Int>, val savedWords: Int, val reviewedWords: Int, val reasons: Map<ErrorReason,Int>, val topics: Map<String,Pair<Int,Int>>)
