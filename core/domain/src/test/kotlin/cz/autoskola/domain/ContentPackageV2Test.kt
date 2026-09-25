package cz.autoskola.domain

import org.junit.Assert.*
import org.junit.Test

class ContentPackageV2Test {
    private val source = "https://etesty.md.gov.cz/TEST_ONLY"
    private fun question(groups: List<String> = emptyList()) = OfficialQuestion("id", "rules", "Test", 2, groups,
        listOf(OfficialAnswer("A", "Yes", true), OfficialAnswer("B", "No", false)), source = source,
        eligibility = groups.map { QuestionEligibility(it, source) })
    private fun pack(question: OfficialQuestion = question(), readiness: List<GroupReadiness> = emptyList(), sample: Boolean = false) =
        QuestionPackage(ContentManifest(2, "TEST", "2026-09-01", source, "2026-09-01T00:00:00Z", sample, groupReadiness = readiness), listOf(question))

    @Test fun v1SampleStillParses() {
        val text = javaClass.getResourceAsStream("/sample-v1.json")!!.bufferedReader().use { it.readText() }
        val decoded = ContentPackageCodec.decode(text)
        assertEquals(1, decoded.manifest.formatVersion)
        PackageValidator.validate(decoded)
        assertEquals(ExamAvailability.SAMPLE_ONLY, GroupReadinessPolicy.availability(decoded, ExamConfigurationProvider.forGroup(LicenceGroup.B)))
    }
    @Test fun v2ProductionAllowsUnmappedQuestionsWithoutMakingThemUniversal() {
        val decoded = ContentPackageCodec.decode(ContentPackageCodec.encodeV2(pack()))
        PackageValidator.validate(decoded)
        assertTrue(decoded.questions.single().licenceGroups.isEmpty())
        LicenceGroup.entries.forEach { assertEquals(ExamAvailability.ELIGIBILITY_INCOMPLETE,
            GroupReadinessPolicy.availability(decoded, ExamConfigurationProvider.forGroup(it))) }
    }
    @Test fun v2PositiveMappingPreservesProvenance() {
        val decoded = ContentPackageCodec.decode(ContentPackageCodec.encodeV2(pack(question(listOf("A", "C")))))
        assertEquals(listOf("A", "C"), decoded.questions.single().licenceGroups)
        assertEquals(source, decoded.questions.single().eligibility.first().source)
        PackageValidator.validate(decoded)
    }
    @Test fun unsupportedOrDuplicateEligibilityIsRejected() {
        val t = question(listOf("T"))
        assertThrows(IllegalArgumentException::class.java) { PackageValidator.validate(pack(t)) }
        val duplicate = question(listOf("B", "B"))
        assertThrows(IllegalArgumentException::class.java) { PackageValidator.validate(pack(duplicate)) }
    }
    @Test fun sampleMayNeverClaimReadiness() {
        val claim = GroupReadiness("B", ExamConfigurationProvider.CURRENT_VERSION, true, true, true, source)
        assertThrows(IllegalArgumentException::class.java) { PackageValidator.validate(pack(readiness = listOf(claim), sample = true)) }
    }
    @Test fun partialClaimsCannotStartAndFullClaimRequiresRealPool() {
        val base = GroupReadiness("B", ExamConfigurationProvider.CURRENT_VERSION, true, true, true, source)
        val q = question(listOf("B"))
        val config = ExamConfigurationProvider.forGroup(LicenceGroup.B)
        assertEquals(ExamAvailability.ELIGIBILITY_INCOMPLETE, GroupReadinessPolicy.availability(pack(q, listOf(base.copy(eligibilityComplete = false))), config))
        assertEquals(ExamAvailability.CONTENT_INCOMPLETE, GroupReadinessPolicy.availability(pack(q, listOf(base.copy(contentComplete = false))), config))
        assertEquals(ExamAvailability.MEDIA_INCOMPLETE, GroupReadinessPolicy.availability(pack(q, listOf(base.copy(mediaComplete = false))), config))
        assertEquals(ExamAvailability.CONTENT_INCOMPLETE, GroupReadinessPolicy.availability(pack(q, listOf(base)), config))
        assertThrows(IllegalArgumentException::class.java) { PackageValidator.validate(pack(q, listOf(base))) }
    }
}
