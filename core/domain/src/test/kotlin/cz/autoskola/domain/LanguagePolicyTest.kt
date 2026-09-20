package cz.autoskola.domain
import org.junit.Assert.*
import org.junit.Test
class LanguagePolicyTest {
    @Test fun actualExamNeverLeaksLanguageHelp() {
        for (ui in UiLanguage.entries) for (mode in MaterialMode.entries) for (level in LearningLevel.entries) {
            val p = UserSettings(ui, mode, level).policy(realExam = true, revealed = true)
            assertNull(p.translationTag); assertFalse(p.showTranslation); assertFalse(p.canReveal); assertFalse(p.canLookup)
        }
    }
    @Test fun czechOnlyWinsOverBeginnerAndReveal() {
        val p = UserSettings(materialMode = MaterialMode.CS_ONLY).policy(revealed = true)
        assertNull(p.translationTag); assertFalse(p.showTranslation); assertFalse(p.canLookup)
    }
    @Test fun interfaceLanguageDoesNotChangeTranslationLanguage() {
        val p = UserSettings(UiLanguage.UK, MaterialMode.CS_RU, LearningLevel.BEGINNER).policy()
        assertEquals("ru", p.translationTag); assertTrue(p.showTranslation)
    }
    @Test fun intermediateNeedsExplicitReveal() {
        val settings = UserSettings(materialMode = MaterialMode.CS_RU, level = LearningLevel.INTERMEDIATE)
        assertFalse(settings.policy().showTranslation)
        assertTrue(settings.policy().canReveal)
        assertTrue(settings.policy(revealed = true).showTranslation)
    }
}
