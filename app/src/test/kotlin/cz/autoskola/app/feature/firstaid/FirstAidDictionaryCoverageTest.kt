package cz.autoskola.app.feature.firstaid

import android.app.Application
import cz.autoskola.app.ui.*
import cz.autoskola.domain.Lexeme
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FirstAidDictionaryCoverageTest {
    @Test fun everyClickableWordOfAllCardsAndAllOfficialOptionsHasBothTranslations() {
        val context = RuntimeEnvironment.getApplication()
        val b = FirstAidContent.load(context)
        val words = aidTestWords(b)
        val texts = b.cards.flatMap { listOf(it.title.cs,it.summary.cs,it.examSummary.cs,it.clinical.cs) + it.badges.map { v -> v.cs } } +
            b.questions.flatMap { listOf(it.question.cs,it.answer.cs) + it.options.map { o -> o.cs } }
        val tokens = texts.flatMap { czechWordRanges(it).map { m -> m.value } }.distinct()
        for(tag in listOf("ru","uk")) {
            val missing = tokens.filter { findLearningWord(it, words, tag)?.translation.isNullOrBlank() }
            assertEquals("Missing $tag: $missing", emptyList<String>(), missing)
        }
        assertEquals("дыхание",findLearningWord("DÝCHÁNÍ,", words,"ru")?.translation)
        assertEquals("шолом",findLearningWord("přilbu!", words,"uk")?.translation)
        assertEquals("лишение свободы",findLearningWord("odnětí svobody",words,"ru")?.translation)
        assertEquals("лишение",findLearningWord("odnětí",words,"ru")?.translation)
        assertNull(findLearningWord("несуществующее",words,"uk"))
    }
}

internal fun aidTestWords(b: AidBundle): List<Lexeme> {
    val context = RuntimeEnvironment.getApplication()
        val base = JSONArray(context.assets.open("content/dictionary-v1.json").bufferedReader().use { it.readText() })
        return b.vocabulary + (0 until base.length()).flatMap { i ->
            val w = base.getJSONObject(i); val translations = w.getJSONArray("translations")
            val forms = w.getJSONArray("forms").let { a -> (0 until a.length()).map { a.getString(it) } }
            (0 until translations.length()).map { j -> val t = translations.getJSONObject(j)
                Lexeme(w.getString("id"),w.getString("lemma"),t.getString("translation"),
                    t.getString("meaning"),w.getString("exampleCs"),t.getString("exampleTranslation"),false,t.getString("locale"),forms)
            }
        }
}
