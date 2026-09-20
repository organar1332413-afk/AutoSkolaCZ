package cz.autoskola.domain
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
class PackageValidatorTest {
    private fun sample(): QuestionPackage = javaClass.getResourceAsStream("/sample-v1.json")!!.bufferedReader().use { Json.decodeFromString(it.readText()) }
    @Test fun verifiedSampleParsesButCannotBecomeAnExam() {
        val pack = sample(); PackageValidator.validate(pack)
        assertFalse(ExamBlueprint.canAssemble(pack))
    }
    @Test fun duplicateIdsAreRejected() {
        val p = sample()
        assertThrows(IllegalArgumentException::class.java) { PackageValidator.validate(p.copy(questions = p.questions + p.questions.first())) }
    }
    @Test fun twoCorrectAnswersAreRejected() {
        val p = sample(); val q = p.questions.first()
        val broken = q.copy(answers = q.answers.map { it.copy(correct = true) })
        assertThrows(IllegalArgumentException::class.java) { PackageValidator.validate(p.copy(questions = listOf(broken))) }
    }
    @Test fun incompleteProductionMetadataIsRejected() {
        val p = sample()
        assertThrows(IllegalArgumentException::class.java) { PackageValidator.validate(p.copy(manifest = p.manifest.copy(sample = false))) }
    }
    @Test fun pathTraversalIsRejected() {
        val p = sample(); val q = p.questions.first().copy(media = listOf(MediaReference("media/../../settings.db", "a".repeat(64), "image/png")))
        assertThrows(IllegalArgumentException::class.java) { PackageValidator.validate(p.copy(questions = listOf(q))) }
    }
    @Test fun translationsCannotUseCsAsAnOverride() {
        val p = sample(); val q = p.questions.first()
        assertThrows(IllegalArgumentException::class.java) { PackageValidator.validate(p.copy(questions = listOf(q.copy(translations = listOf(q.translations.first().copy(locale = "cs")))))) }
    }
}
