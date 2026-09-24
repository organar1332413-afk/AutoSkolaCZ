package cz.autoskola.domain

data class LessonProgress(val lessonId: String, val position: Int, val completedAt: Long?, val updatedAt: Long)
data class LearningSnapshot(val attempts: List<AttemptRecord> = emptyList(), val favorites: Set<String> = emptySet(), val reviews: Map<String,ReviewState> = emptyMap(), val progress: List<LessonProgress> = emptyList(), val examScores: List<Int> = emptyList())
data class LessonBlockCard(val id: String, val kind: String, val textCs: String, val translation: String?, val mediaPath: String?)
data class LessonCard(val id: String, val topic: String, val titleCs: String, val title: String?, val locale: String?, val source: String, val reviewStatus: String, val minutes: Int, val blocks: List<LessonBlockCard>, val questions: List<String>)

fun LearningSnapshot.isMistake(q: QuestionCard) =
    attempts.any { it.revisionId.substringAfterLast(':') == q.officialId && !it.correct } &&
        (reviews[q.officialId]?.takeIf { it.revisionId == q.revisionId }?.masteredAt == null)

fun LearningSnapshot.isKnown(q: QuestionCard) =
    reviews[q.officialId]?.takeIf { it.revisionId == q.revisionId }?.masteredAt != null

fun LearningSnapshot.isDoubtful(q: QuestionCard):Boolean {
    val attemptedCurrent=attempts.any { it.revisionId==q.revisionId }
    return attemptedCurrent && !isMistake(q) && !isKnown(q)
}

fun LearningSnapshot.matches(q: QuestionCard, filter: QuestionFilter) = when (filter) {
    QuestionFilter.ALL -> true
    QuestionFilter.MISTAKES -> isMistake(q)
    QuestionFilter.DOUBTFUL -> isDoubtful(q)
    QuestionFilter.KNOWN -> isKnown(q)
    QuestionFilter.FAVORITES -> q.officialId in favorites
    QuestionFilter.UNSEEN -> attempts.none { it.revisionId == q.revisionId }
}
