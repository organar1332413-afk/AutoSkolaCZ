package cz.autoskola.domain
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
/** Synthetic scoring fixtures are test-only, never app assets or alleged official questions. */
class ExamEngineTest {
    private fun bankFor(group: LicenceGroup): QuestionPackage {
        val base = bank()
        val source = base.manifest.source
        return base.copy(
            manifest = base.manifest.copy(formatVersion = 2, completeForB = false,
                groupReadiness = listOf(GroupReadiness(group.code, ExamConfigurationProvider.CURRENT_VERSION, true, true, true, source))),
            questions = base.questions.map { it.copy(licenceGroups = listOf(group.code), eligibility = listOf(QuestionEligibility(group.code, source))) }
        )
    }
    @Test fun selectedGroupsOnlyUsePositiveVerifiedMappings() {
        listOf(LicenceGroup.A, LicenceGroup.B, LicenceGroup.C).forEach { group ->
            val pack = bankFor(group)
            val session = ExamEngine.create(pack, ExamConfigurationProvider.forGroup(group), 100)
            assertEquals(group, session.licenceGroup)
            assertEquals(25, session.questionCount)
            val other = if (group == LicenceGroup.A) LicenceGroup.B else LicenceGroup.A
            assertThrows(IllegalArgumentException::class.java) { ExamEngine.create(pack, ExamConfigurationProvider.forGroup(other), 100) }
        }
    }
    @Test fun resultUsesPersistedThresholdAndMaximum() {
        val session = ExamEngine.finish(ExamEngine.create(bankFor(LicenceGroup.C), ExamConfigurationProvider.forGroup(LicenceGroup.C), 0), 10)
        val perfect = session.copy(answers = session.items.associate { it.revisionId to it.correctCode })
        assertFalse(ExamEngine.result(perfect.copy(passPoints = 51)).passed)
        assertEquals(60, ExamEngine.result(perfect.copy(maxPoints = 60)).score)
    }
    private fun bank():QuestionPackage {
        val points=mapOf("rules" to 2,"safe_driving" to 2,"signs" to 1,"situations" to 4,"vehicle" to 1,"related" to 2,"first_aid" to 1)
        return QuestionPackage(ContentManifest(databaseVersion="TEST_ONLY",publicationDate="2025-01-01",source="https://etesty.md.gov.cz/TEST_ONLY",retrievedAt="2025-01-01T00:00:00Z",sample=false,completeForB=true),ExamConfigurationProvider.forGroup(LicenceGroup.B).sections.associate { it.category to it.questionCount }.flatMap { (category,n)->(0..n).map { index->OfficialQuestion("TEST_${category}_$index",category,"TEST ONLY",points[category],listOf("B"),listOf(OfficialAnswer("A","TEST A",true),OfficialAnswer("B","TEST B",false)),source="https://etesty.md.gov.cz/TEST_ONLY") } })
    }
    @Test fun blueprintHas25UniqueBQuestionsAnd50Points() {
        val s=ExamEngine.create(bank(), ExamConfigurationProvider.forGroup(LicenceGroup.B),100,Random(1));assertEquals(25,s.items.size);assertEquals(25,s.items.map { it.officialId }.distinct().size);assertEquals(50,s.items.sumOf { it.points });assertEquals(ExamConfigurationProvider.forGroup(LicenceGroup.B).sections.associate { it.category to it.questionCount },s.items.groupingBy { it.category }.eachCount());assertEquals(1_800_100L,s.deadlineAt)
    }
    @Test fun sampleCannotStartExamEvenWithEnoughQuestions() { val p=bank();assertThrows(IllegalArgumentException::class.java) { ExamEngine.create(p.copy(manifest=p.manifest.copy(sample=true,completeForB=false)), ExamConfigurationProvider.forGroup(LicenceGroup.B),0) } }
    @Test fun incompleteBankCannotStartExam() { val p=bank();assertThrows(IllegalArgumentException::class.java) { ExamEngine.create(p.copy(questions=p.questions.filter { it.category!="first_aid" }), ExamConfigurationProvider.forGroup(LicenceGroup.B),0) } }
    @Test fun unverifiedCompleteFlagBlocksExam() { val p=bank();assertThrows(IllegalArgumentException::class.java) { ExamEngine.create(p.copy(manifest=p.manifest.copy(completeForB=false)), ExamConfigurationProvider.forGroup(LicenceGroup.B),0) } }
    @Test fun nonBQuestionsCannotFillQuota() { val p=bank();assertThrows(IllegalArgumentException::class.java) { ExamEngine.create(p.copy(questions=p.questions.map { it.copy(licenceGroups=listOf("A")) }), ExamConfigurationProvider.forGroup(LicenceGroup.B),0) } }
    @Test fun wrongScoreDistributionIsRejected() { val p=bank();assertThrows(IllegalArgumentException::class.java) { ExamEngine.create(p.copy(questions=p.questions.map { it.copy(points=1) }), ExamConfigurationProvider.forGroup(LicenceGroup.B),0) } }
    @Test fun resultsStayHiddenUntilFinish() { assertThrows(IllegalArgumentException::class.java) { ExamEngine.result(ExamEngine.create(bank(), ExamConfigurationProvider.forGroup(LicenceGroup.B),0)) } }
    @Test fun passBoundary43AndFail42() {
        val base=ExamEngine.create(bank(), ExamConfigurationProvider.forGroup(LicenceGroup.B),0)
        val all=base.copy(answers=base.items.associate { it.revisionId to it.correctCode })
        val seven=base.items.filter { it.points==4 }.take(1)+base.items.filter { it.points==2 }.take(1)+base.items.filter { it.points==1 }.take(1)
        val pass=ExamEngine.result(ExamEngine.finish(all.copy(answers=all.answers-seven.map { it.revisionId }.toSet()),1))
        assertEquals(43,pass.score);assertTrue(pass.passed);assertEquals(7,pass.lostPoints)
        val eight=base.items.filter { it.points==4 }.take(2)
        val fail=ExamEngine.result(ExamEngine.finish(all.copy(answers=all.answers-eight.map { it.revisionId }.toSet()),1))
        assertEquals(42,fail.score);assertFalse(fail.passed);assertEquals(mapOf("situations" to 2),fail.wrongByCategory)
    }
    @Test fun answerCanBeChangedButMustBelongToPinnedQuestion() {
        val s=ExamEngine.create(bank(), ExamConfigurationProvider.forGroup(LicenceGroup.B),0);val id=s.items.first().revisionId
        val a=ExamEngine.answer(ExamEngine.answer(s,id,"B",1),id,"A",2)
        assertEquals("A",a.answers[id]);assertThrows(IllegalArgumentException::class.java) { ExamEngine.answer(a,id,"D",3) }
    }
    @Test fun expiredExamStopsAndResumesAsFinished() {
        val s=ExamEngine.create(bank(), ExamConfigurationProvider.forGroup(LicenceGroup.B),0);assertEquals(0L,ExamEngine.remainingSeconds(s,s.deadlineAt+1))
        assertThrows(IllegalArgumentException::class.java) { ExamEngine.answer(s,s.items.first().revisionId,"A",s.deadlineAt) }
        val resumed=ExamEngine.resume(s,s.deadlineAt+20);assertEquals(s.deadlineAt,resumed.completedAt);assertEquals(0,ExamEngine.result(resumed).score)
    }
    @Test fun serializedSessionPreservesPinnedRevisionsAndAnswers() {
        val s=ExamEngine.create(bank(), ExamConfigurationProvider.forGroup(LicenceGroup.B),100);val answered=ExamEngine.answer(s,s.items.last().revisionId,"B",101)
        val restored=Json.decodeFromString<ExamSession>(Json.encodeToString(ExamSession.serializer(),answered))
        assertEquals(answered,ExamEngine.resume(restored,200));assertTrue(restored.items.all { it.revisionId.startsWith("TEST_ONLY:") })
    }
    @Test fun finishedExamIsImmutable() {
        val s=ExamEngine.finish(ExamEngine.create(bank(), ExamConfigurationProvider.forGroup(LicenceGroup.B),0),100)
        assertEquals(s,ExamEngine.finish(s,200));assertThrows(IllegalArgumentException::class.java) { ExamEngine.answer(s,s.items.first().revisionId,"A",200) }
    }
}
