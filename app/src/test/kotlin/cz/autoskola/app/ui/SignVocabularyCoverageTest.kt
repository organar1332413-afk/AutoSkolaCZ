package cz.autoskola.app.ui

import android.app.Application
import cz.autoskola.app.AppContainer
import cz.autoskola.app.feature.catalog.SignCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real production fields -> real tokenizer -> packaged JSON -> Room -> selected-language popup model. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SignVocabularyCoverageTest {
    @Test fun everyTappableProductionTokenHasBothTranslationsAndExplanations() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("autoskola.db")
        val container = AppContainer(app)
        try {
            container.dictionary.initialize()
            val signs = SignCatalog.load(app)
            assertEquals(408, signs.size)
            val texts = signs.flatMap { sign -> sign.tappableTexts().map { (field, text) -> Triple(sign.code, field, text) } }
            assertEquals(1802, texts.size)
            val occurrences = texts.flatMap { (code, field, text) ->
                czechWordRanges(text).map { Triple(normalizeLearningWord(it.value), "$code / $field", text) }
            }
            assertEquals(11313, occurrences.size)
            assertEquals(2275, occurrences.map { it.first }.toSet().size)
            val errors = mutableListOf<String>()
            for(locale in listOf("ru", "uk")) {
                val words = container.study.words(locale).first()
                val owners = mutableMapOf<String, String>()
                words.forEach { word -> (word.forms + word.lemma).distinct().forEach { form ->
                    val key = normalizeLearningWord(form)
                    val previous = owners.put(key, word.id)
                    assertTrue("Conflict: $key ($previous/${word.id})", previous == null || previous == word.id)
                } }
                val entries = occurrences.map { it.first }.distinct().associateWith { token ->
                    findLearningEntry(token, words, locale)
                }
                // Check every occurrence so failure reports the exact sign/field/context, not only a token count.
                for((token, location, context) in occurrences) {
                    val entry = entries[token]
                    val missing = listOfNotNull(if(entry == null) "lookup" else null,
                        if(entry?.translation.isNullOrBlank()) "$locale translation" else null,
                        if(entry?.meaning.isNullOrBlank()) "$locale explanation" else null)
                    if(missing.isNotEmpty()) errors += "$token / $location / ${missing.joinToString()} / $context"
                }
            }
            assertTrue("Missing vocabulary:\n${errors.joinToString("\n")}", errors.isEmpty())
        } finally { container.db.close(); app.deleteDatabase("autoskola.db") }
    }

    @Test fun finiteFormsKeepLanguageAndPolarityAndRealRoadMeaning() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("autoskola.db")
        val container = AppContainer(app)
        try {
            container.dictionary.initialize()
            val ru = container.study.words("ru").first()
            val ua = container.study.words("uk").first()
            for(token in listOf("Tvar", "tvar", "TVAR.", "(tvar,)", "tvaru")) {
                assertEquals("форма, очертание", findLearningWord(token, ru, "ru")?.translation)
                assertEquals("форма, обрис", findLearningWord(token, ua, "uk")?.translation)
                assertFalse(findLearningWord(token, ru, "ru")?.meaning.isNullOrBlank())
                assertFalse(findLearningWord(token, ua, "uk")?.meaning.isNullOrBlank())
            }
            assertEquals("перекрёсток", findLearningWord("KŘIŽOVATKÁCH:", ru, "ru")?.translation)
            assertEquals("снизить скорость", findLearningWord("Zpomalte,", ru, "ru")?.translation)
            assertEquals("не поворачивать", findLearningWord("neodbočujte", ru, "ru")?.translation)
            assertEquals("повернуть", findLearningWord("odbočte", ru, "ru")?.translation)
            assertEquals("разворот", findLearningWord("obrat", ru, "ru")?.translation)
            assertTrue(findLearningWord("hroty", ru, "ru")!!.meaning!!.contains("шипы"))
            assertTrue(findLearningWord("přestavování", ru, "ru")!!.meaning!!.contains("парковочном диске"))
            assertTrue(findLearningWord("měření", ru, "ru")!!.translation!!.contains("измерение"))
            assertTrue(findLearningWord("bezpečí", ru, "ru")!!.translation!!.contains("безопасность"))
            assertTrue(findLearningWord("měření", ua, "uk")!!.translation!!.contains("вимірювання"))
            assertTrue(findLearningWord("bezpečí", ua, "uk")!!.translation!!.contains("безпека"))
            assertNull(findLearningWord("naprostoneznámé", ru, "ru"))
        } finally { container.db.close(); app.deleteDatabase("autoskola.db") }
    }
}
