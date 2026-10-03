package cz.autoskola.app.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import cz.autoskola.design.*
import cz.autoskola.domain.Lexeme
import cz.autoskola.data.DictionaryLoadState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LearningWordPopupTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val driver = Lexeme("ridic", "řidič", "водитель", "Человек, управляющий транспортным средством.", "", null, false, "ru", listOf("řidiče"))
    private var saves = 0
    private lateinit var policy: MutableState<WordTranslationPolicy>
    private lateinit var dictionaryStatus: MutableState<DictionaryLoadState>
    private fun launch(strict: Boolean = false) {
        policy = mutableStateOf(if(strict) WordTranslationPolicy.StrictExam else WordTranslationPolicy("ru", true))
        dictionaryStatus = mutableStateOf(DictionaryLoadState.READY)
        compose.setContent { AutoSkolaTheme {
            var selected by remember { mutableStateOf<LearningWordSelection?>(null) }
            var words by remember { mutableStateOf(listOf(driver)) }
            Column(Modifier.fillMaxSize().padding(PremiumSpace.lg)) {
                Text("Detail remains open", Modifier.testTag("screen"))
                CzechLearningText("Řidiče, vozidla. Neznámé!", policy.value, { selected = it }, modifier = Modifier.testTag("czech"))
            }
            LearningWordPopup(selected, policy.value, words, {
                saves++; words = words.map { word -> word.copy(saved = true) }
            }, { token ->
                saves++; words = words + driver.copy(id = "unknown", lemma = token, translation = null, meaning = null, forms = emptyList(), saved = true)
            }, dictionaryStatus.value) { selected = null }
        } }
    }
    private fun tap(token: String) {
        val node = compose.onNodeWithTag("czech")
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val result = layouts.single()
        val index = result.layoutInput.text.text.indexOf(token)
        val box = result.getBoundingBox(index)
        assertTrue("Laid-out word has nonzero width: $box", box.width > 0)
        node.performTouchInput { click(Offset(box.center.x, box.center.y)) }
    }
    @Test fun actualWordTapHandlesPunctuationAndSavingIsIdempotentInUi() {
        launch(); tap("Řidiče")
        compose.onNodeWithTag("translation-token").assertTextEquals("Řidiče")
        compose.onNodeWithTag("word-translation").assertTextEquals("водитель")
        compose.onNodeWithTag("translation-save").performClick().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, saves) }
        compose.onNodeWithTag("translation-close").performClick()
        tap("Řidiče")
        compose.onNodeWithTag("translation-save").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, saves) }
    }
    @Test fun unknownWordShowsFallbackAndCanBeSavedWithoutInventingTranslation() {
        launch(); tap("Neznámé")
        compose.onNodeWithTag("word-translation-unavailable").assertTextEquals("Перевод пока недоступен")
        compose.onNodeWithTag("translation-save").performClick().assertIsNotEnabled()
        compose.onNodeWithTag("word-translation").assertDoesNotExist()
    }
    @Test fun outsideTouchAndBackClosePopupAndKeepScreenOpen() {
        launch(); tap("Řidiče")
        compose.onNodeWithTag("learning-word-popup").assertIsDisplayed()
        compose.runOnIdle {
            // Compose touch injection targets its owner, bypassing WindowManager. Send the
            // actual outside-window event through PopupLayout's Android dispatch instead.
            val global = org.robolectric.util.ReflectionHelpers.callStaticMethod<Any>(
                Class.forName("android.view.WindowManagerGlobal"), "getInstance")
            val views = org.robolectric.util.ReflectionHelpers.getField<ArrayList<android.view.View>>(global, "mViews")
            val popup = views.single { it.javaClass.name.contains("PopupLayout") }
            val now = android.os.SystemClock.uptimeMillis()
            val outside = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
            try { popup.dispatchTouchEvent(outside) } finally { outside.recycle() }
        }
        compose.onNodeWithTag("learning-word-popup").assertDoesNotExist()
        compose.onNodeWithTag("screen").assertIsDisplayed()
        tap("Neznámé")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("learning-word-popup").assertDoesNotExist()
        compose.onNodeWithTag("screen").assertIsDisplayed()
    }
    @Test fun strictPolicyDisablesTapAndClosesExistingPopup() {
        launch(); tap("Řidiče")
        compose.onNodeWithTag("learning-word-popup").assertIsDisplayed()
        compose.runOnIdle { policy.value = WordTranslationPolicy.StrictExam }
        compose.onNodeWithTag("learning-word-popup").assertDoesNotExist()
        tap("Řidiče")
        compose.onNodeWithTag("learning-word-popup").assertDoesNotExist()
    }
    @Test fun loadingAndSystemFailureAreDistinctFromMissingWord() {
        launch(); tap("Neznámé")
        compose.runOnIdle { dictionaryStatus.value = DictionaryLoadState.LOADING }
        compose.onNodeWithTag("word-dictionary-status").assertIsDisplayed()
        compose.onNodeWithTag("word-translation-unavailable").assertDoesNotExist()
        compose.onNodeWithTag("translation-save").assertIsNotEnabled()
        compose.runOnIdle { dictionaryStatus.value = DictionaryLoadState.ERROR }
        compose.onNodeWithTag("word-dictionary-status").assertIsDisplayed()
        compose.onNodeWithTag("word-translation-unavailable").assertDoesNotExist()
        compose.onNodeWithTag("translation-save").assertIsNotEnabled()
        compose.runOnIdle { dictionaryStatus.value = DictionaryLoadState.READY }
        compose.onNodeWithTag("word-dictionary-status").assertDoesNotExist()
        compose.onNodeWithTag("word-translation-unavailable").assertIsDisplayed()
        compose.onNodeWithTag("translation-save").assertIsEnabled()
    }
}
