package cz.autoskola.domain

data class ExamSectionRule(val category: String, val questionCount: Int, val pointsPerQuestion: Int)

data class ExamConfiguration(
    val licenceGroup: LicenceGroup,
    val blueprintVersion: String,
    val durationMinutes: Int,
    val maxPoints: Int,
    val passPoints: Int,
    val sections: List<ExamSectionRule>
) {
    val questionCount: Int get() = sections.sumOf { it.questionCount }
    init {
        require(blueprintVersion.isNotBlank() && durationMinutes > 0 && passPoints in 1..maxPoints)
        require(sections.isNotEmpty() && sections.map { it.category }.distinct().size == sections.size)
        require(sections.all { it.questionCount > 0 && it.pointsPerQuestion > 0 })
        require(sections.sumOf { it.questionCount * it.pointsPerQuestion } == maxPoints)
    }
}

/** Verified common theory-test structure; a version is pinned in every attempt. */
object ExamConfigurationProvider {
    const val CURRENT_VERSION = "etesty-2026-09-v1"
    private val sections = listOf(
        ExamSectionRule("rules", 10, 2),
        ExamSectionRule("safe_driving", 4, 2),
        ExamSectionRule("signs", 3, 1),
        ExamSectionRule("situations", 3, 4),
        ExamSectionRule("vehicle", 2, 1),
        ExamSectionRule("related", 2, 2),
        ExamSectionRule("first_aid", 1, 1)
    )
    val categories: Set<String> = sections.map { it.category }.toSet()
    fun forGroup(group: LicenceGroup): ExamConfiguration =
        ExamConfiguration(group, CURRENT_VERSION, 30, 50, 43, sections)

    /** Legacy version is only for loading persisted B attempts; no new attempt uses it. */
    fun supports(group: LicenceGroup, version: String): Boolean =
        version == CURRENT_VERSION || (group == LicenceGroup.B && version == "B-stage2-v1")
}
