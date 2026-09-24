package cz.autoskola.domain

import kotlinx.serialization.Serializable

@Serializable
data class ContentManifest(val formatVersion: Int = 1, val databaseVersion: String, val publicationDate: String?, val source: String, val retrievedAt: String, val sample: Boolean, val completeForB: Boolean = false)
@Serializable
data class OfficialAnswer(val code: String, val textCs: String, val correct: Boolean)
@Serializable
data class QuestionText(val locale: String, val text: String, val explanation: String, val answers: Map<String,String>, val reviewStatus: String = "draft")
@Serializable
data class MediaReference(val path: String, val sha256: String, val mimeType: String, val answerCode: String? = null)
@Serializable
data class OfficialQuestion(val officialId: String, val category: String, val textCs: String, val points: Int?, val licenceGroups: List<String>, val answers: List<OfficialAnswer>, val media: List<MediaReference> = emptyList(), val translations: List<QuestionText> = emptyList(), val source: String)
@Serializable
data class QuestionPackage(val manifest: ContentManifest, val questions: List<OfficialQuestion>)
data class QuestionCard(val revisionId: String, val officialId: String, val category: String, val textCs: String, val points: Int?, val answers: List<OfficialAnswer>, val translation: QuestionText?, val media: List<MediaReference> = emptyList(), val licenceGroups: List<String> = emptyList(), val source: String? = null)
data class ContentStatus(val databaseVersion: String, val publicationDate: String?, val source: String, val sample: Boolean, val count: Int, val completeForB: Boolean = false)
data class Lexeme(val id: String, val lemma: String, val translation: String?, val meaning: String?, val exampleCs: String, val exampleTranslation: String?, val saved: Boolean, val locale: String, val forms: List<String>, val repetitions: Int = 0, val correctCount: Int = 0)
