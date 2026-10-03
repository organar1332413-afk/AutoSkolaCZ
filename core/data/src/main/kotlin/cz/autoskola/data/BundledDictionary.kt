package cz.autoskola.data

import android.content.Context
import androidx.room.withTransaction
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.db.entity.DictionaryFormEntity
import cz.autoskola.data.db.entity.DictionaryTranslationEntity
import cz.autoskola.data.db.entity.DictionaryWordEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class DictionaryLoadState { LOADING, READY, ERROR }

@Serializable private data class BundledWord(val id: String, val lemma: String, val context: String,
    val exampleCs: String, val forms: List<String>, val translations: List<BundledWordText>)
@Serializable private data class BundledWordText(val locale: String, val translation: String,
    val meaning: String, val exampleTranslation: String)

/** Learning dictionary, independent of debug question/lesson samples and exam content.
 * Existing Room IDs, imported entries and SavedWord history are preserved by IGNORE inserts. */
class BundledDictionary(private val context: Context, private val db: AutoSkolaDatabase) {
    private val current = MutableStateFlow(DictionaryLoadState.LOADING)
    val state = current.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        current.value = DictionaryLoadState.LOADING
        try {
            val words = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use {
                Json.decodeFromString<List<BundledWord>>(it.readText())
            }
            require(words.isNotEmpty()) { "Bundled dictionary is empty" }
            require(words.map { it.id }.distinct().size == words.size) { "Duplicate dictionary IDs" }
            require(words.map { it.lemma }.distinct().size == words.size) { "Duplicate dictionary lemmas" }
            val formOwners = mutableMapOf<String, String>()
            words.forEach { word ->
                require(word.forms.isNotEmpty() && word.forms.distinct().size == word.forms.size) {
                    "Empty/duplicate dictionary forms: ${word.id}"
                }
                (word.forms + word.lemma).distinct().forEach { form ->
                    require(form.isNotBlank() && form == java.text.Normalizer.normalize(form, java.text.Normalizer.Form.NFC)
                        .lowercase(java.util.Locale.forLanguageTag("cs"))) { "Non-normalized dictionary form: $form" }
                    val previous = formOwners.put(form, word.id)
                    require(previous == null || previous == word.id) { "Conflicting dictionary form: $form" }
                }
                require(word.id.isNotBlank() && word.lemma.isNotBlank()) { "Empty dictionary ID/lemma" }
                require(word.translations.map { it.locale }.toSet() == setOf("ru", "uk") &&
                    word.translations.size == 2 && word.translations.all { it.translation.isNotBlank() && it.meaning.isNotBlank() }) {
                    "Missing RU/UA dictionary translation/explanation: ${word.id}"
                }
            }
            db.withTransaction {
                db.words().insertWords(words.map { DictionaryWordEntity(it.id, it.lemma, it.context, it.exampleCs, "draft") })
                db.words().insertForms(words.flatMap { word -> word.forms.map { DictionaryFormEntity(word.id, it) } })
                db.words().insertTranslations(words.flatMap { word -> word.translations.map {
                    DictionaryTranslationEntity(word.id, it.locale, it.translation, it.meaning, it.exampleTranslation)
                } })
            }
            current.value = DictionaryLoadState.READY
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { current.value = DictionaryLoadState.ERROR; throw e }
    }

    companion object { const val ASSET_PATH = "content/dictionary-v1.json" }
}
