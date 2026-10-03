package cz.autoskola.app.ui

import android.app.Application
import android.content.ContextWrapper
import android.content.res.AssetManager
import cz.autoskola.app.AppContainer
import cz.autoskola.data.BundledDictionary
import cz.autoskola.data.DictionaryLoadState
import cz.autoskola.data.LearningRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Packaged asset -> common initializer -> actual Room repository -> popup lookup.
 * This class runs against both debug and release source sets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BundledDictionaryRuntimeTest {
    private fun withContainer(test: suspend (AppContainer) -> Unit) = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("autoskola.db")
        val container = AppContainer(app)
        try { test(container) }
        finally { container.db.close(); app.deleteDatabase("autoskola.db") }
    }

    @Test fun mainAssetLoadsWithoutDebugSamplesAndSelectsRuAndUa() = withContainer { container ->
        container.dictionary.initialize()
        assertEquals(DictionaryLoadState.READY, container.dictionary.state.value)
        assertNull(container.db.content().activeVersion().first())
        val ru = container.study.words("ru").first()
        val uk = container.study.words("uk").first()
        assertEquals(1178, ru.size)
        assertTrue(ru.all { !it.translation.isNullOrBlank() && !it.meaning.isNullOrBlank() })
        assertTrue(uk.all { !it.translation.isNullOrBlank() && !it.meaning.isNullOrBlank() })
        assertEquals("водитель", findLearningWord("řidič", ru, "ru")?.translation)
        assertEquals("водій", findLearningWord("řidič", uk, "uk")?.translation)
        assertEquals("транспортное средство", findLearningWord("vozidlo", ru, "ru")?.translation)
        assertEquals("транспортний засіб", findLearningWord("vozidlo", uk, "uk")?.translation)
        assertNull(findLearningWord("řidič", ru, "uk"))
    }

    @Test fun assetFormsCasePunctuationAndDiacriticsReachTheSameEntry() = withContainer { container ->
        container.dictionary.initialize()
        val ru = container.study.words("ru").first()
        for(token in listOf("Řidič", "ŘIDIČ", "řidič.", "řidič,", "(řidič)", "„ŘIDIČ:“", "ŘIDIC\u030c")) {
            assertEquals(token, "водитель", findLearningWord(token, ru, "ru")?.translation)
        }
        assertEquals("водитель", findLearningWord("(ŘIDIČE,)", ru, "ru")?.translation)
        assertEquals("транспортное средство", findLearningWord("VOZIDLEM:", ru, "ru")?.translation)
        for(token in listOf("Tvar", "tvar", "tvar.", "tvar,")) {
            assertEquals("tvar", normalizeLearningWord(token))
            assertEquals("форма, очертание", findLearningWord(token, ru, "ru")?.translation)
        }
        assertNull(findLearningWord("naprostoneznámé", ru, "ru"))
    }

    @Test fun repeatedInitializationAndDatabaseReopenPreserveSavedWordsAndCounters() = withContainer { container ->
        container.dictionary.initialize()
        container.study.saveWord("ridic")
        LearningRepository(container.db).wordReview("ridic", true)
        container.dictionary.initialize()
        val word = container.study.words("ru").first().single { it.id == "ridic" }
        assertTrue(word.saved)
        assertEquals(1, word.repetitions)
        assertEquals(1, word.correctCount)
        assertEquals(1178, container.study.words("ru").first().size)
        assertEquals(1, container.db.words().saved().first().size)
        container.db.close()
        val reopened = AppContainer(container.application)
        try {
            reopened.dictionary.initialize()
            val ua = reopened.study.words("uk").first().single { it.id == "ridic" }
            assertEquals("водій", ua.translation)
            assertTrue(ua.saved)
            assertEquals(1, ua.repetitions)
        } finally { reopened.db.close() }
    }

    @Test fun newCorpusWordSavesByLemmaAcrossFormsAndLanguagesWithoutDuplicates() = withContainer { container ->
        container.dictionary.initialize()
        val ru = container.study.words("ru").first()
        val tvar = findLearningWord("Tvar,", ru, "ru")!!
        assertEquals(tvar.id, findLearningWord("tvaru", ru, "ru")!!.id)
        container.study.saveWord(tvar.id)
        container.study.saveWord(tvar.id)
        assertEquals(1, container.db.words().saved().first().size)
        container.dictionary.initialize()
        val ua = container.study.words("uk").first()
        assertTrue(findLearningWord("TVARU:", ua, "uk")!!.saved)
        assertEquals("форма, обрис", findLearningWord("Tvar", ua, "uk")!!.translation)
    }

    @Suppress("DEPRECATION")
    @Test fun missingAssetIsSystemErrorAndNeverBecomesReadyEmptyDictionary() = withContainer { container ->
        val emptyAssets = org.robolectric.util.ReflectionHelpers.callConstructor(AssetManager::class.java)
        val context = object : ContextWrapper(container.application) {
            override fun getAssets() = emptyAssets
        }
        val dictionary = BundledDictionary(context, container.db)
        try {
            var error: Exception? = null
            try { dictionary.initialize() } catch(e: Exception) { error = e }
            assertNotNull(error)
            assertEquals(DictionaryLoadState.ERROR, dictionary.state.value)
            assertTrue(container.study.words("ru").first().isEmpty())
        } finally { emptyAssets.close() }
    }
}
