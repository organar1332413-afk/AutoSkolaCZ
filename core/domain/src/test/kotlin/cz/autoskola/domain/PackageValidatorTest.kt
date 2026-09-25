package cz.autoskola.domain
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
class PackageValidatorTest {
    private fun sample(): QuestionPackage = javaClass.getResourceAsStream("/sample-v1.json")!!.bufferedReader().use { Json.decodeFromString(it.readText()) }
    @Test fun verifiedSampleParsesButCannotBecomeAnExam() {
        val pack = sample(); PackageValidator.validate(pack)
        assertFalse(GroupReadinessPolicy.availability(pack, ExamConfigurationProvider.forGroup(LicenceGroup.B)) == ExamAvailability.READY)
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
    @Test fun imageOnlyOfficialAnswerRequiresItsImage() {
        val p = sample(); val q = p.questions.first()
        val imageAnswer = q.answers.first().copy(textCs = "")
        val withImage = q.copy(answers = listOf(imageAnswer) + q.answers.drop(1),
            media = listOf(MediaReference("media/A_W_1408_27623.jpg", "a".repeat(64), "image/jpeg", imageAnswer.code)))
        PackageValidator.validate(p.copy(questions = listOf(withImage)))
        assertThrows(IllegalArgumentException::class.java) {
            PackageValidator.validate(p.copy(questions = listOf(withImage.copy(media = emptyList()))))
        }
    }
}
