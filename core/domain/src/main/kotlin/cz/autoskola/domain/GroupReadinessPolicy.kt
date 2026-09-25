package cz.autoskola.domain

enum class ExamAvailability { READY, SAMPLE_ONLY, BLUEPRINT_UNVERIFIED, ELIGIBILITY_INCOMPLETE, CONTENT_INCOMPLETE, MEDIA_INCOMPLETE }

object GroupReadinessPolicy {
    val supportedImageMimeTypes = setOf("image/png", "image/jpeg", "image/webp")

    fun claims(manifest: ContentManifest): List<GroupReadiness> = if (manifest.formatVersion == 1 && !manifest.sample && manifest.completeForB)
        listOf(GroupReadiness("B", "B-stage2-v1", true, true, true, manifest.source))
    else manifest.groupReadiness

    fun availability(pack: QuestionPackage, config: ExamConfiguration): ExamAvailability {
        if (pack.manifest.sample) return ExamAvailability.SAMPLE_ONLY
        val claim = claims(pack.manifest).find { it.licenceGroup == config.licenceGroup.code }
            ?: return ExamAvailability.ELIGIBILITY_INCOMPLETE
        if (!ExamConfigurationProvider.supports(config.licenceGroup, claim.blueprintVersion) ||
            (claim.blueprintVersion != config.blueprintVersion && claim.blueprintVersion != "B-stage2-v1"))
            return ExamAvailability.BLUEPRINT_UNVERIFIED
        if (!claim.eligibilityComplete) return ExamAvailability.ELIGIBILITY_INCOMPLETE
        if (!claim.contentComplete) return ExamAvailability.CONTENT_INCOMPLETE
        if (!claim.mediaComplete) return ExamAvailability.MEDIA_INCOMPLETE
        val eligible = pack.questions.filter { config.licenceGroup.code in it.licenceGroups }
        if (eligible.any { q -> q.media.any { it.mimeType !in supportedImageMimeTypes } }) return ExamAvailability.MEDIA_INCOMPLETE
        if (config.sections.any { rule ->
                eligible.count { it.category == rule.category && it.points == rule.pointsPerQuestion } < rule.questionCount
            }) return ExamAvailability.CONTENT_INCOMPLETE
        return ExamAvailability.READY
    }
}
