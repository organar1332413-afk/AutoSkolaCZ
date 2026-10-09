package cz.autoskola.app.feature.firstaid

import android.app.Application
import cz.autoskola.app.ui.findLearningWord
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FirstAidContentTest {
    private fun bundle() = FirstAidContent.load(RuntimeEnvironment.getApplication())
    @Test fun bundledContentAndEveryOfflineImageAreAvailable() {
        val b = bundle()
        assertEquals(16, b.cards.size); assertEquals(35, b.questions.size)
        assertEquals(b.questions.map { it.id }.toSet(), b.cards.flatMap { it.questionIds }.toSet())
        b.questions.forEach { q -> assertEquals(1, q.options.count { it.correct }); assertTrue(b.cardsFor(q.id).isNotEmpty()) }
    }
    @Test fun languageHelpersDoNotDependOnInterfaceAndCsOnlyHidesThem() {
        val card = bundle().cards.first()
        assertNull(card.title.helper(null)); assertEquals("Обязанность помочь и безопасность",card.title.helper("ru"))
        assertEquals("Обов’язок допомогти та безпека",card.title.helper("uk"))
        assertEquals("C05",filterAid(bundle().cards,"масса","all","ru").single().id)
        assertTrue(filterAid(bundle().cards,"масса","all",null).isEmpty())
        assertEquals("C13",filterAid(bundle().cards,"RP1102024","trauma",null).single().id)
    }
    @Test fun dictionaryInflectedFormsResolveInBothLanguagesWithoutChangingSignsDictionary() {
        val words=bundle().vocabulary
        assertEquals("дыхание",findLearningWord("dýchání",words,"ru")?.translation)
        assertEquals("шолом",findLearningWord("přilbu",words,"uk")?.translation)
        assertNull(findLearningWord("невідоме",words,"ru"))
    }
    @Test fun clinicalAndExamRemainSeparateAndPracticeCardsInventNoOfficialQuestion() {
        val b=bundle();val cpr=b.cards.single { it.id=="C05" }
        assertTrue(cpr.examSummary.cs.contains("4–5"));assertTrue(cpr.clinical.cs.contains("5–6"))
        assertEquals("B",b.questions.single { it.id=="RP1102024" }.correct.label)
        assertEquals(listOf("C06","C12"),b.cards.filter { it.questionIds.isEmpty() }.map { it.id })
    }
}
