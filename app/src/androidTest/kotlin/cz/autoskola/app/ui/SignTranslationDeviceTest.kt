package cz.autoskola.app.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cz.autoskola.app.feature.catalog.SignCatalog
import cz.autoskola.app.feature.catalog.SignDetailScreen
import cz.autoskola.design.*
import cz.autoskola.domain.Lexeme
import cz.autoskola.domain.MaterialMode
import cz.autoskola.app.AppContainer
import cz.autoskola.app.MainViewModel
import cz.autoskola.data.DictionaryLoadState
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native window dispatch complements Robolectric coverage of content/nav/Room. */
@RunWith(AndroidJUnit4::class)
class SignTranslationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val driver = Lexeme("ridic", "řidič", "водитель", "Человек, управляющий транспортным средством.", "", null, false, "ru", listOf("řidiče"))

    @Test fun packagedDictionaryFeedsViewModelAndRealSignPopupInRuAndUa() {
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val container = AppContainer(app)
        val sign = SignCatalog.load(app).single { it.code == "C 7a" }
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity,
            MainViewModel.Factory(container))[MainViewModel::class.java] }
        try {
            compose.setContent { AutoSkolaTheme {
                val settings by vm.settings.collectAsState()
                val words by vm.words.collectAsState()
                val dictionary by vm.dictionaryState.collectAsState()
                SignDetailScreen(sign, settings.materialMode.translationTag, words, true,
                    false, {}, vm::saveWord, emptySet(), {}, vm::saveUnknownWord,
                    lookupTipSeen = true, dictionaryState = dictionary)
            } }
            vm.material(MaterialMode.CS_RU)
            compose.waitUntil(30_000) { vm.dictionaryState.value == DictionaryLoadState.READY &&
                findLearningWord("řidič", vm.words.value, "ru") != null }
            compose.onNodeWithTag("sign-detail-C 7a").performScrollToNode(hasTestTag("action-cs"))
            tapWord("action-cs", "řidič")
            compose.onNodeWithTag("word-translation").assertTextEquals("водитель")
            compose.onNodeWithTag("word-explanation").assertTextEquals("Человек, управляющий транспортным средством.")
            compose.onNodeWithTag("translation-save").performClick()
            compose.waitUntil(10_000) { findLearningWord("řidič", vm.words.value, "ru")?.saved == true }
            compose.onNodeWithTag("translation-save").assertIsNotEnabled()
            screenshot("dictionary-runtime-sign-ru.png")
            compose.onNodeWithTag("translation-close").performClick()
            tapWord("action-cs", "vozidla")
            compose.onNodeWithTag("word-translation").assertTextEquals("транспортное средство")
            vm.material(MaterialMode.CS_UK)
            compose.waitUntil(10_000) { findLearningWord("vozidla", vm.words.value, "uk") != null }
            compose.onNodeWithTag("learning-word-popup").assertDoesNotExist()
            tapWord("action-cs", "vozidla")
            compose.onNodeWithTag("word-translation").assertTextEquals("транспортний засіб")
            screenshot("dictionary-runtime-sign-ua.png")
            compose.onNodeWithTag("translation-close").performClick()
            tapWord("action-cs", "řidič")
            compose.onNodeWithTag("word-translation").assertTextEquals("водій")
            compose.onNodeWithTag("translation-save").assertIsNotEnabled()
            compose.onNodeWithTag("translation-close").performClick()
            tapWord("action-cs", "respektujte")
            compose.onNodeWithTag("word-translation").assertTextEquals("дотримуватися, враховувати")
            compose.onNodeWithTag("word-explanation").assertTextEquals("Дотримуватися вказаного правила чи враховувати іншого учасника.")
            compose.onNodeWithText("Озвучить по-чешски").assertDoesNotExist()
            compose.onNodeWithText("Přehrát česky").assertDoesNotExist()
        } finally {
            compose.runOnIdle { compose.activity.viewModelStore.clear() }
            container.db.close()
        }
    }

    @Test fun realTvarPopupShowsContextInBothLanguagesWithoutAudio() {
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val container = AppContainer(app)
        val sign = SignCatalog.load(app).single { it.code == "P 1" }
        val tag = mutableStateOf("ru")
        var words by mutableStateOf<List<Lexeme>>(emptyList())
        kotlinx.coroutines.runBlocking { container.dictionary.initialize() }
        try {
            compose.setContent { AutoSkolaTheme { SpeechProvider {
                val entries by container.study.words(tag.value).collectAsState(initial = emptyList())
                words = entries
                SignDetailScreen(sign, tag.value, entries, true, false, {}, {}, emptySet(), {}, lookupTipSeen = true)
            } } }
            for(language in listOf("ru", "uk")) {
                compose.runOnIdle { tag.value = language }
                compose.waitUntil(30_000) { findLearningWord("Tvar", words, language) != null }
                compose.onNodeWithTag("sign-detail-P 1").performScrollToNode(hasTestTag("memory-cs"))
                tapWord("memory-cs", "Tvar")
                compose.onNodeWithTag("translation-token").assertTextEquals("Tvar")
                compose.onNodeWithTag("word-translation").assertTextEquals(
                    if(language == "ru") "форма, очертание" else "форма, обрис")
                compose.onNodeWithTag("word-explanation").assertTextEquals(
                    if(language == "ru") "Очертание знака, дороги или её направления на схеме."
                    else "Обрис знака, дороги чи її напрямку на схемі.")
                compose.onNodeWithText("Озвучить по-чешски").assertDoesNotExist()
                compose.onNodeWithText("Přehrát česky").assertDoesNotExist()
                compose.onNodeWithText("Озвучити чеською").assertDoesNotExist()
                compose.onNodeWithTag("translation-save").assertIsEnabled()
                screenshot("tvar-context-$language.png")
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("learning-word-popup").assertDoesNotExist()
                compose.onNodeWithTag("sign-detail-P 1").assertIsDisplayed()
            }
        } finally { container.db.close() }
    }

    @Test fun existingSpeechButtonsRemainAvailableOutsideTheWordPopup() {
        compose.setContent { AutoSkolaTheme { SpeechProvider { SpeechButtons("řidič") } } }
        compose.onNodeWithText("Přehrát česky").assertIsDisplayed()
        compose.onNodeWithText("Přehrát pomalu").assertIsDisplayed()
    }

    @Test fun nativeOutsideTapAndBackCloseAnchoredPopupWithoutChangingScroll() {
        lateinit var state: androidx.compose.foundation.lazy.LazyListState
        compose.setContent { AutoSkolaTheme {
            state = rememberLazyListState()
            var selected by remember { mutableStateOf<LearningWordSelection?>(null) }
            LazyColumn(Modifier.fillMaxSize().testTag("learning-screen"), state = state, contentPadding = PaddingValues(PremiumSpace.lg)) {
                items(30) { index ->
                    CzechLearningText("Řidiče, vozidla. Neznámé! Řádek $index.", WordTranslationPolicy("ru", true),
                        { selected = it }, modifier = Modifier.fillMaxWidth().padding(vertical = PremiumSpace.md).testTag("line-$index"))
                }
            }
            LearningWordPopup(selected, WordTranslationPolicy("ru", true), listOf(driver), {}, {}) { selected = null }
        } }
        compose.onNodeWithTag("learning-screen").performScrollToIndex(8)
        val before = compose.runOnIdle { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
        tapWord("line-8", "Řidiče")
        compose.onNodeWithTag("word-translation").assertTextEquals("водитель")
        screenshot("word-popup-known-ru.png")
        // Real screen coordinates outside the anchored surface; dispatch through WindowManager.
        val location = IntArray(2)
        var height = 0
        compose.runOnIdle { compose.activity.window.decorView.getLocationOnScreen(location); height = compose.activity.window.decorView.height }
        val now = SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, now + if(action == MotionEvent.ACTION_UP) 40 else 0,
                action, location[0] + 24f, location[1] + height - 100f, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        compose.onNodeWithTag("learning-word-popup").assertDoesNotExist()
        assertEquals(before, compose.runOnIdle { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset })
        tapWord("line-8", "Neznámé")
        compose.onNodeWithTag("word-translation-unavailable").assertIsDisplayed()
        screenshot("word-popup-unknown-ru.png")
        // Instrumentation supplies current event times; zero-time synthetic keys are rejected by Android.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("learning-word-popup").assertDoesNotExist()
        compose.onNodeWithTag("learning-screen").assertIsDisplayed()
        assertEquals(before, compose.runOnIdle { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset })
    }

    @Test fun realA12aShowsBothHelperLanguagesInAllThreeBlocksAndCzHidesThem() {
        val sign = SignCatalog.load(instrumentation.targetContext).single { it.code == "A 12a" }
        val tag = mutableStateOf<String?>("ru")
        compose.setContent { AutoSkolaTheme {
            SignDetailScreen(sign, tag.value, emptyList(), tag.value != null, false, {}, {}, emptySet(), {}, lookupTipSeen = true)
        } }
        screenshot("a12a-detail-ru-top.png")
        for(language in listOf("ru", "uk")) {
            compose.runOnIdle { tag.value = language }
            for((section, value) in listOf("meaning" to sign.meaningCs!!, "action" to sign.driverActionsCs!!, "memory" to sign.memoryCs!!)) {
                compose.onNodeWithTag("sign-detail-A 12a").performScrollToNode(hasTestTag("$section-helper"))
                compose.onNodeWithTag("$section-helper").assertTextEquals(sign.helperFor(value, language)!!)
                tapWord("$section-cs", value.takeWhile { it.isLetter() })
                compose.onNodeWithTag("learning-word-popup").assertIsDisplayed()
                compose.onNodeWithTag("translation-close").performClick()
            }
            screenshot("a12a-detail-$language-blocks.png")
        }
        compose.runOnIdle { tag.value = null }
        for(section in listOf("meaning", "action", "memory")) {
            compose.onNodeWithTag("$section-cs").performScrollTo()
            compose.onNodeWithTag("$section-helper").assertDoesNotExist()
        }
    }

    private fun tapWord(tag: String, word: String) {
        val node = compose.onNodeWithTag(tag)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val result = layouts.single()
        val box = result.getBoundingBox(result.layoutInput.text.text.indexOf(word))
        node.performTouchInput { click(Offset(box.center.x, box.center.y)) }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Device screenshot unavailable")
        // AGP supplies and collects this directory before uninstalling the tested APK.
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val parent = output?.let(::File) ?: instrumentation.targetContext.getExternalFilesDir(null)!!
        val folder = File(parent, "sign-detail-verification").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
