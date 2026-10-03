package cz.autoskola.app.ui

import cz.autoskola.domain.Lexeme
import org.junit.Assert.*
import org.junit.Test

class CzechLearningTextTest {
    private val word = Lexeme("1", "řidič", "водитель", null, "", null, false, "ru", listOf("řidiče"))
    @Test fun punctuationDiacriticsAndCaseAreNormalizedWithoutChangingDisplay() {
        assertEquals("připraveni", normalizeLearningWord("„PŘIPRAVENI,“"))
        assertEquals("řidiče", normalizeLearningWord("ŘIDIC\u030cE."))
        assertEquals(listOf("Zpomalte", "a", "buďte", "připraveni", "zastavit"),
            czechWordRanges("Zpomalte a buďte připraveni zastavit.").map { it.value })
        assertEquals(word, findLearningWord("ŘIDIČE,", listOf(word), "ru"))
    }
    @Test fun strictExamPolicyIsExplicitAndDoesNotAllowLookup() {
        assertFalse(WordTranslationPolicy.StrictExam.allowsLookup)
        assertFalse(WordTranslationPolicy("ru", false).allowsLookup)
        assertFalse(WordTranslationPolicy(null, true).allowsLookup)
        assertTrue(WordTranslationPolicy("uk", true).allowsLookup)
    }
    @Test fun onlyRealTranslationsInSelectedLanguageCanBeOpened() {
        assertEquals(word, findLearningWord("ŘIDIČE", listOf(word), "ru"))
        assertNull(findLearningWord("řidič", listOf(word), "uk"))
        assertNull(findLearningWord("řidič", listOf(word), null))
        assertNull(findLearningWord("vozidlo", listOf(word), "ru"))
        assertNull(findLearningWord("řidič", listOf(word.copy(translation = "")), "ru"))
    }
}
