package cz.autoskola.data.importer
import androidx.room.withTransaction
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.db.entity.*
import cz.autoskola.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** Our documented interchange format, not an invented MD API/export format.
 * Official-source adapters must preserve exact strings and validate provenance upstream.
 * No public network updater is enabled in stage 1. */
class ContentImporter(private val db: AutoSkolaDatabase, private val contentRoot: File) {
    private val importMutex = Mutex()
    suspend fun importPackage(bytes: ByteArray, mediaRoot: File? = null): String = importMutex.withLock { withContext(Dispatchers.IO) {
        require(bytes.size <= 20 * 1024 * 1024) { "Manifest exceeds size limit" }
        val pack = ContentPackageCodec.decode(bytes.decodeToString(throwOnInvalidSequence = true))
        PackageValidator.validate(pack)
        val hash = sha256(bytes)
        val version = pack.manifest.databaseVersion
        val dao = db.content()
        val previous = dao.version(version)
        if (previous != null) {
            require(previous.packageSha256 == hash) { "Version collision: immutable package changed" }
            return@withContext version
        }
        val destination = File(contentRoot, hash)
        val staging = File(contentRoot, "$hash.staging")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()
        try {
            pack.questions.flatMap { it.media }.forEach { media ->
                val root = requireNotNull(mediaRoot) { "Media missing" }.canonicalFile
                val file = File(root, media.path).canonicalFile
                require(file.path.startsWith(root.path + File.separator) && file.isFile)
                require(file.length() <= 100L * 1024 * 1024) { "Media too large" }
                require(file.inputStream().use { stream ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(8192)
                    var n = stream.read(buffer)
                    while (n != -1) { digest.update(buffer, 0, n); n = stream.read(buffer) }
                    digest.digest().hex()
                } == media.sha256) { "Media checksum mismatch" }
                val target = File(staging, media.path)
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
            }
            if (destination.exists()) destination.deleteRecursively()
            require(staging.renameTo(destination)) { "Cannot install media" }
            db.withTransaction {
                val m = pack.manifest
                dao.insertVersion(DatabaseVersionEntity(version, version, m.publicationDate, m.source, m.retrievedAt, System.currentTimeMillis(), m.sample, m.completeForB, hash))
                dao.insertReadiness(GroupReadinessPolicy.claims(m).map { claim ->
                    ContentGroupReadinessEntity(version, claim.licenceGroup, claim.blueprintVersion, claim.eligibilityComplete, claim.contentComplete, claim.mediaComplete, claim.source)
                })
                dao.insertCategories(pack.questions.map { it.category }.distinct().map { QuestionCategoryEntity(it, it) })
                dao.insertQuestions(pack.questions.map { QuestionEntity(it.officialId, it.officialId) })
                pack.questions.forEach { q ->
                    val id = "$version:${q.officialId}"
                    dao.insertRevisions(listOf(QuestionRevisionEntity(id, q.officialId, version, q.category, q.textCs, q.points, q.source)))
                    dao.insertAnswers(q.answers.mapIndexed { index, a -> AnswerEntity(id, a.code, a.textCs, a.correct, index) })
                    dao.insertGroups(q.licenceGroups.map { QuestionLicenceGroupEntity(id, it) })
                    dao.insertTranslations(q.translations.map { QuestionTranslationEntity(id, it.locale, it.text, it.explanation, it.reviewStatus) })
                    dao.insertAnswerTranslations(q.translations.flatMap { t -> t.answers.map { (code, text) -> AnswerTranslationEntity(id, code, t.locale, text) } })
                    dao.insertMedia(q.media.mapIndexed { i, media -> QuestionMediaEntity("$id:$i", id, media.answerCode, "$hash/${media.path}", media.sha256, media.mimeType, i) })
                }
                dao.activate(ActiveContentEntity(1, version))
            }
        } finally { if (staging.exists()) staging.deleteRecursively() }
        version
    } }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
}
