package cz.autoskola.data
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.db.entity.*
import androidx.room.withTransaction
import java.util.Locale
import java.security.MessageDigest
import cz.autoskola.domain.*
import kotlinx.coroutines.flow.combine
class RoomStudyRepository(private val db: AutoSkolaDatabase) : StudyRepository {
    override fun status() = combine(db.content().activeVersion(), db.content().questions(), db.content().activeReadiness()) { version, questions, readiness ->
        version?.let { ContentStatus(it.databaseVersion, it.publicationDate, it.source, it.sample, questions.size,
            readiness.map { row -> GroupReadiness(row.licenceGroup, row.blueprintVersion, row.eligibilityComplete, row.contentComplete, row.mediaComplete, row.source) }) }
    }
    override fun questions(locale: String?) = combine(
        combine(db.content().questions(), db.content().answers(), db.content().translations(locale ?: ""), db.content().answerTranslations(locale ?: ""), db.content().media()) { qs, answers, translations, ats, media ->
            qs.map { q ->
                val t = translations.find { it.revisionId == q.id }
                QuestionCard(q.id, q.questionId, q.categoryId, q.textCs, q.points,
                    answers.filter { it.revisionId == q.id }.sortedBy { it.position }.map { OfficialAnswer(it.code, it.textCs, it.correct) },
                    t?.let { QuestionText(it.locale, it.text, it.explanation, ats.filter { a -> a.revisionId == q.id }.associate { a -> a.answerCode to a.text }, it.reviewStatus) },
                    media.filter { it.revisionId==q.id }.map { MediaReference(it.path,it.sha256,it.mimeType,it.answerCode) },
                    source=q.source)
            }
        },
        db.content().licenceGroups()
    ) { cards, groups ->
        cards.map { card -> card.copy(licenceGroups = groups.filter { it.revisionId == card.revisionId }.map { it.licenceGroup }) }
    }
    override fun words(locale: String) = combine(db.words().words(), db.words().translations(locale), db.words().saved(), db.words().forms()) { words, texts, saved, forms ->
        words.map { word ->
            val t = texts.find { it.wordId == word.id }
            Lexeme(word.id, word.lemma, t?.translation, t?.meaning, word.exampleCs, t?.exampleTranslation, saved.any { it.wordId == word.id }, locale, forms.filter { it.wordId == word.id }.map { it.form }, saved.find { it.wordId == word.id }?.repetitions ?: 0, saved.find { it.wordId == word.id }?.correctCount ?: 0)
        }
    }
    suspend fun saveUnknownWord(token:String) = db.withTransaction {
        require(token.length in 1..100 && token.all { it.isLetter() || it in "-’'" })
        val surface=token.lowercase(Locale.forLanguageTag("cs"))
        val id="user-"+MessageDigest.getInstance("SHA-256").digest(surface.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        // Surface form only; lemma and translation await an explicitly verified dictionary entry.
        db.words().insertWords(listOf(DictionaryWordEntity(id,surface,"unverified surface form","","draft")))
        db.words().insertForms(listOf(DictionaryFormEntity(id,surface)))
        saveWord(id)
    }
    override suspend fun saveWord(id: String) { db.words().save(SavedWordEntity(id, System.currentTimeMillis(), 0, 0, null)) }
    /** Only a user-selected first-aid companion entry; never replaces existing dictionary content. */
    suspend fun saveAidWord(word: Lexeme) = db.withTransaction {
        require(word.id.startsWith("aid-") && word.locale in setOf("ru", "uk") && !word.translation.isNullOrBlank())
        db.words().insertWords(listOf(DictionaryWordEntity(word.id, word.lemma, "first aid learning companion", word.exampleCs, "draft")))
        db.words().insertForms((word.forms + word.lemma).distinct().map { DictionaryFormEntity(word.id, it) })
        db.words().insertTranslations(listOf(DictionaryTranslationEntity(word.id, word.locale,
            requireNotNull(word.translation), word.meaning.orEmpty(), word.exampleTranslation.orEmpty())))
        saveWord(word.id)
    }
}
