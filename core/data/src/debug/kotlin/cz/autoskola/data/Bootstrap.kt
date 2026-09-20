package cz.autoskola.data
import android.content.Context
import androidx.room.withTransaction
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.db.entity.*
import cz.autoskola.data.importer.ContentImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
@Serializable private data class SeedWord(val id: String, val lemma: String, val context: String, val exampleCs: String, val forms: List<String>, val translations: List<SeedWordText>)
@Serializable private data class SeedWordText(val locale: String, val translation: String, val meaning: String, val exampleTranslation: String)
class Bootstrap(private val context: Context, private val db: AutoSkolaDatabase, private val importer: ContentImporter, private val allowSamples: Boolean = false) {
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (!allowSamples) return@withContext
        if (db.content().activeVersion().first() == null) {
            importer.importPackage(context.assets.open("content/sample-v1.json").use { it.readBytes() })
        }
        val words = context.assets.open("content/dictionary-v1.json").bufferedReader().use { Json.decodeFromString<List<SeedWord>>(it.readText()) }
        db.withTransaction {
            db.words().insertWords(words.map { DictionaryWordEntity(it.id, it.lemma, it.context, it.exampleCs, "draft") })
            db.words().insertForms(words.flatMap { w -> w.forms.map { DictionaryFormEntity(w.id, it) } })
            db.words().insertTranslations(words.flatMap { w -> w.translations.map { DictionaryTranslationEntity(w.id, it.locale, it.translation, it.meaning, it.exampleTranslation) } })
        }
    }
}
