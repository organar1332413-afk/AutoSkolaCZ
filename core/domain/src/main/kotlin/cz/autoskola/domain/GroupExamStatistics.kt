package cz.autoskola.domain

/** Exam metrics use the stored group of each attempt; general study is tracked separately. */
data class GroupExamStatistics(val history: List<ExamHistoryItem>, val attempts: List<AttemptRecord>) {
    val averagePercent: Double? get() = history.takeIf { it.isNotEmpty() }
        ?.map { it.score * 100.0 / it.maxPoints }?.average()
    val best: ExamHistoryItem? get() = history.maxByOrNull { it.score.toDouble() / it.maxPoints }
    val passRate: Int? get() = if (history.isEmpty()) null else history.count { it.passed } * 100 / history.size
    val correctPercent: Int? get() = if (attempts.isEmpty()) null else attempts.count { it.correct } * 100 / attempts.size
    val weakTopics: List<Pair<String, Int>> get() = attempts.filter { !it.correct }
        .groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }
        .map { it.key to it.value }
    fun errors(reason: ErrorReason): Int = attempts.count { !it.correct && it.reason == reason }
}

fun LearningSnapshot.examStatistics(group: LicenceGroup) = GroupExamStatistics(
    examHistory.filter { it.licenceGroup == group }, attempts.filter { it.examLicenceGroup == group && !it.sample }
)

fun LearningSnapshot.lastExamFor(group: LicenceGroup): ExamHistoryItem? =
    examHistory.filter { it.licenceGroup == group }.maxByOrNull { it.completedAt }
