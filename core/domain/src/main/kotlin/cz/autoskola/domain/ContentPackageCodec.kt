package cz.autoskola.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

/** App-owned interchange format; it is not a Ministry API response. */
object ContentPackageCodec {
    private val json = Json { ignoreUnknownKeys = false }

    fun decode(text: String): QuestionPackage {
        val root = json.parseToJsonElement(text).jsonObject
        val format = root["manifest"]?.jsonObject?.get("formatVersion")?.jsonPrimitive?.intOrNull ?: 1
        return when (format) {
            1 -> json.decodeFromString<QuestionPackage>(text)
            2 -> {
                val wire = json.decodeFromString<PackageV2>(text)
                QuestionPackage(
                    ContentManifest(2, wire.manifest.databaseVersion, wire.manifest.publicationDate, wire.manifest.source,
                        wire.manifest.retrievedAt, wire.manifest.sample, groupReadiness = wire.manifest.groupReadiness),
                    wire.questions.map { q -> OfficialQuestion(q.officialId, q.category, q.textCs, q.points,
                        q.eligibility.map { it.licenceGroup }, q.answers, q.media, q.translations, q.source, q.eligibility) }
                )
            }
            else -> throw IllegalArgumentException("Unsupported content format: $format")
        }
    }

    fun encodeV2(pack: QuestionPackage): String = json.encodeToString(PackageV2.serializer(), PackageV2(
        ManifestV2(2, pack.manifest.databaseVersion, pack.manifest.publicationDate, pack.manifest.source,
            pack.manifest.retrievedAt, pack.manifest.sample, pack.manifest.groupReadiness),
        pack.questions.map { q -> QuestionV2(q.officialId, q.category, q.textCs, q.points,
            q.eligibility.ifEmpty { q.licenceGroups.map { QuestionEligibility(it, q.source) } }, q.answers, q.media, q.translations, q.source) }
    ))
}

@Serializable private data class PackageV2(val manifest: ManifestV2, val questions: List<QuestionV2>)
@Serializable private data class ManifestV2(val formatVersion: Int, val databaseVersion: String, val publicationDate: String?, val source: String, val retrievedAt: String, val sample: Boolean, val groupReadiness: List<GroupReadiness>)
@Serializable private data class QuestionV2(val officialId: String, val category: String, val textCs: String, val points: Int?, val eligibility: List<QuestionEligibility>, val answers: List<OfficialAnswer>, val media: List<MediaReference> = emptyList(), val translations: List<QuestionText> = emptyList(), val source: String)
