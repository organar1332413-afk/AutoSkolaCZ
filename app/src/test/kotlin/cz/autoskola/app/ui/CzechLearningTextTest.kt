package cz.autoskola.app.ui

import cz.autoskola.domain.Lexeme
import org.junit.Assert.*
import org.junit.Test

class CzechLearningTextTest {
    private val word = Lexeme("1", "řidič", "водитель", null, "", null, false, "ru", listOf("řidiče"))
    @Test fun onlyRealTranslationsInSelectedLanguageCanBeOpened() {
        assertEquals(word, findLearningWord("ŘIDIČE", listOf(word), "ru"))
        assertNull(findLearningWord("řidič", listOf(word), "uk"))
        assertNull(findLearningWord("řidič", listOf(word), null))
        assertNull(findLearningWord("vozidlo", listOf(word), "ru"))
        assertNull(findLearningWord("řidič", listOf(word.copy(translation = "")), "ru"))
    }
}
