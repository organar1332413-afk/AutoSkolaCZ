package cz.autoskola.domain

/** Counts observed on the official category B test page; scoring/time from the brief.
 * A production blueprint must be versioned and verified together with each content release. */
object ExamBlueprint {
    val counts = linkedMapOf("rules" to 10, "safe_driving" to 4, "signs" to 3, "situations" to 3, "vehicle" to 2, "related" to 2, "first_aid" to 1)
    const val minutes = 30
    const val maxPoints = 50
    const val passPoints = 43
    fun canAssemble(pack: QuestionPackage): Boolean = !pack.manifest.sample && pack.manifest.completeForB && counts.all { (category, count) ->
        pack.questions.count { it.category == category && "B" in it.licenceGroups && it.points != null } >= count
    }
}
