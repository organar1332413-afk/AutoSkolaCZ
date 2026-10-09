package cz.autoskola.app.feature.firstaid

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
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
    @Test fun russianWordTapAndAnswerChoiceAreIndependent() = exercise("ru")
    @Test fun ukrainianWordTapAndAnswerChoiceAreIndependent() = exercise("uk")
}
