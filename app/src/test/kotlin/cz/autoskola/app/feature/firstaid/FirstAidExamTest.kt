package cz.autoskola.app.feature.firstaid

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import cz.autoskola.app.ui.*
import cz.autoskola.design.AutoSkolaTheme
import cz.autoskola.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirstAidExamTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val bundle get() = FirstAidContent.load(RuntimeEnvironment.getApplication())
    private var saved: String? = null
    private fun launch(tag: String) {
        val b = bundle
        val q = b.questions.single { it.id == "RP1102018" }
        val settings = UserSettings(uiLanguage = UiLanguage.RU,
            materialMode = if(tag == "ru") MaterialMode.CS_RU else MaterialMode.CS_UK)
        compose.setContent { InterfaceLanguage(settings.uiLanguage) { AutoSkolaTheme {
            AidQuestionScreen(q, b, AidDestinationState(AidLoadState(b, false), settings, aidTestWords(b)), {}, { saved = it }, {})
        } } }
    }
    private fun exercise(tag: String) {
        launch(tag)
        compose.onNodeWithTag("aid-check").assertIsNotEnabled()
        compose.onNodeWithTag("aid-correct-A").assertDoesNotExist()
        compose.onNodeWithTag("aid-result").assertDoesNotExist()
        // An actual text tap opens the popup and must not select the answer.
        val node = compose.onNodeWithTag("aid-option-text-A")
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val box = layouts.single().getBoundingBox(0)
        node.performTouchInput { click(Offset(box.center.x, box.center.y)) }
        compose.onNodeWithTag("learning-word-popup").assertIsDisplayed()
        compose.onNodeWithTag("word-translation").assertTextEquals(if(tag == "ru") "да" else "так")
        compose.onNodeWithTag("word-translation-unavailable").assertDoesNotExist()
        compose.onNodeWithTag("translation-save").performClick()
        compose.runOnIdle { assertNotNull(saved) }
        compose.onNodeWithTag("translation-close").performClick()
        compose.onNodeWithTag("aid-select-A").assertIsNotSelected()
        compose.onNodeWithTag("aid-select-B").performClick().assertIsSelected()
        compose.onNodeWithTag("aid-correct-A").assertDoesNotExist()
        compose.onNodeWithTag("aid-check").performClick()
        compose.onNodeWithTag("aid-result").assertTextEquals("Неверно. Правильный ответ выделен.")
        compose.onNodeWithTag("aid-correct-A").assertIsDisplayed()
        compose.onNodeWithTag("aid-select-A").assertIsNotEnabled()
        compose.onNodeWithTag("aid-retry").performClick()
        compose.onNodeWithTag("aid-select-A").performClick()
        compose.onNodeWithTag("aid-check").performClick()
        compose.onNodeWithTag("aid-result").assertTextEquals("Верно")
    }
    @Test fun all35QuestionsHideKeysUntilCheckAndUseTheirOriginalCorrectOption() {
        val b = bundle
        val active = mutableStateOf(b.questions.first())
        compose.setContent { InterfaceLanguage(UiLanguage.CS) { AutoSkolaTheme {
            AidQuestionScreen(active.value, b,
                AidDestinationState(AidLoadState(b, false), UserSettings(materialMode = MaterialMode.CS_ONLY), emptyList()), {}, {}, {})
        } } }
        b.questions.forEach { q ->
            compose.runOnIdle { active.value = q }
            val list = compose.onNodeWithTag("aid-question-detail-${q.id}")
            compose.onNodeWithTag("aid-result").assertDoesNotExist()
            q.options.forEach { o ->
                list.performScrollToNode(hasTestTag("aid-option-text-${o.label}"))
                compose.onNodeWithTag("aid-option-text-${o.label}").assertTextEquals(o.cs)
                compose.onNodeWithTag("aid-option-helper-${o.label}").assertDoesNotExist()
                compose.onNodeWithTag("aid-correct-${o.label}").assertDoesNotExist()
            }
            list.performScrollToNode(hasTestTag("aid-select-${q.correct.label}"))
            compose.onNodeWithTag("aid-select-${q.correct.label}").performClick()
            list.performScrollToNode(hasTestTag("aid-check"))
            compose.onNodeWithTag("aid-check").performClick()
            compose.onNodeWithTag("aid-result").assertTextEquals("Správně")
            list.performScrollToNode(hasTestTag("aid-correct-${q.correct.label}"))
            compose.onNodeWithTag("aid-correct-${q.correct.label}").assertIsDisplayed()
        }
    }
    @Test fun russianWordTapAndAnswerChoiceAreIndependent() = exercise("ru")
    @Test fun ukrainianWordTapAndAnswerChoiceAreIndependent() = exercise("uk")

    private fun allHelpers(tag: String) {
        val b = bundle
        val words = aidTestWords(b)
        val active = mutableStateOf(b.questions.first())
        // UI Czech with Russian/Ukrainian material verifies that policies stay independent.
        val mode = if(tag == "ru") MaterialMode.CS_RU else MaterialMode.CS_UK
        compose.setContent { InterfaceLanguage(UiLanguage.CS) { AutoSkolaTheme {
            AidQuestionScreen(active.value, b,
                AidDestinationState(AidLoadState(b, false), UserSettings(materialMode = mode), words), {}, {}, {})
        } } }
        var seen = 0
        b.questions.forEach { q ->
            compose.runOnIdle { active.value = q }
            val list = compose.onNodeWithTag("aid-question-detail-${q.id}")
            list.performScrollToIndex(0)
            compose.onNodeWithTag("aid-learning-cs").assertTextEquals(q.question.cs)
            compose.onNodeWithTag("aid-learning-helper").assertTextEquals(q.question.helper(tag)!!)
            compose.onNodeWithTag("aid-exam-caption").assertTextEquals("Formulace ke zkoušce")
            compose.onNodeWithTag("aid-result").assertDoesNotExist()
            q.options.forEach { o ->
                list.performScrollToNode(hasTestTag("aid-option-helper-${o.label}"))
                compose.onNodeWithTag("aid-option-text-${o.label}").assertTextEquals(o.cs)
                val cs = layout("aid-option-text-${o.label}")
                compose.onNodeWithTag("aid-option-helper-${o.label}").assertTextEquals(o.helper(tag)!!)
                val helper = layout("aid-option-helper-${o.label}")
                assertTrue(helper.layoutInput.style.fontSize < cs.layoutInput.style.fontSize)
                assertFalse(helper.hasVisualOverflow)
                assertFalse(cs.hasVisualOverflow)
                listOf(cs, helper).forEach { result ->
                    val text = result.layoutInput.text
                    Regex("\\d").findAll(text.text).forEach { digit ->
                        assertTrue("${q.id}/${o.label}/$tag", text.spanStyles.any {
                            digit.range.first in it.start until it.end && it.item.fontWeight == FontWeight.Bold
                        })
                    }
                    assertTrue(text.spanStyles.all { it.item.color == androidx.compose.ui.graphics.Color.Unspecified })
                }
                compose.onNodeWithTag("aid-correct-${o.label}").assertDoesNotExist()
                compose.onNodeWithTag("aid-select-${o.label}").assertIsNotSelected()
                if(q.id in listOf("RP1102014", "RP1102016") && o.label == "A") screenshot("${q.id}-$tag.png")
                seen++
            }
            if(q.options.size == 2) compose.onNodeWithTag("aid-option-C").assertDoesNotExist()
        }
        assertEquals(104, seen)
    }

    private fun layout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    @Test fun all35QuestionsDisplayAll104RussianOptionHelpers() = allHelpers("ru")
    @Test @Config(qualifiers = "w320dp-h740dp")
    fun all35QuestionsDisplayAll104UkrainianOptionHelpersOnNarrowScreen() = allHelpers("uk")

    private fun numberedWordTap(tag: String) {
        val b = bundle; val q = b.questions.single { it.id == "RP0904008" }
        val mode = if(tag == "ru") MaterialMode.CS_RU else MaterialMode.CS_UK
        compose.setContent { InterfaceLanguage(UiLanguage.RU) { AutoSkolaTheme {
            AidQuestionScreen(q, b, AidDestinationState(AidLoadState(b, false), UserSettings(materialMode = mode), aidTestWords(b)), {}, { saved = it }, {})
        } } }
        val list = compose.onNodeWithTag("aid-question-detail-${q.id}")
        list.performScrollToNode(hasTestTag("aid-option-text-A"))
        val cs = layout("aid-option-text-A")
        val box = cs.getBoundingBox(q.options.first().cs.indexOf("stlačení"))
        compose.onNodeWithTag("aid-option-text-A").performTouchInput { click(box.center) }
        compose.onNodeWithTag("word-translation").assertTextEquals(findLearningWord("stlačení", aidTestWords(b), tag)!!.translation!!)
        compose.onNodeWithTag("translation-save").performClick()
        compose.runOnIdle { assertNotNull(saved) }
        compose.onNodeWithTag("translation-close").performClick()
        compose.onNodeWithTag("aid-select-A").assertIsNotSelected()
        list.performScrollToNode(hasTestTag("aid-check"))
        compose.onNodeWithTag("aid-check").assertIsNotEnabled()
        list.performScrollToNode(hasTestTag("aid-select-A"))
        compose.onNodeWithTag("aid-select-A").performClick()
        list.performScrollToNode(hasTestTag("aid-check"))
        compose.onNodeWithTag("aid-check").performClick()
        list.performScrollToNode(hasTestTag("aid-correct-B"))
        compose.onNodeWithTag("aid-correct-B").assertIsDisplayed()
    }
    @Test fun russianWordTapAfterBoldNumberStillSavesWithoutSelectingAnswer() = numberedWordTap("ru")
    @Test fun ukrainianWordTapAfterBoldNumberStillSavesWithoutSelectingAnswer() = numberedWordTap("uk")

    private fun screenshot(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/outputs/first-aid/options-$name").apply { parentFile.mkdirs() }
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
