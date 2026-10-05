package cz.autoskola.app.feature.catalog

import android.app.Application
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.data.DictionaryLoadState
import cz.autoskola.design.*
import cz.autoskola.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/** Production locale provider and real sign copy; all 3 UI x 3 material combinations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignsLocalizationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = RuntimeEnvironment.getApplication()
    private fun context(language: UiLanguage) = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
        setLocale(Locale.forLanguageTag(language.tag))
    })
    private fun label(language: UiLanguage, id: Int) = context(language).getString(id)
    private val words = listOf(
        Lexeme("chodec", "chodec", "пешеход", "Участник движения, передвигающийся пешком.", "", null, false, "ru", listOf("chodci")),
        Lexeme("chodec", "chodec", "пішохід", "Учасник руху, який пересувається пішки.", "", null, false, "uk", listOf("chodci"))
    )

    @Test fun catalogChromeFiltersSearchAndStatesFollowUiAcrossAllNineMaterialCombinations() {
        val ui = mutableStateOf(UiLanguage.CS)
        val mode = mutableStateOf(MaterialMode.CS_ONLY)
        val signs = SignCatalog.load(app)
        compose.setContent { InterfaceLanguage(ui.value) { AutoSkolaTheme {
            Scaffold(topBar = { AppTopBar(text(requireNotNull(SignRoutes.titleResource(SignRoutes.catalog)))) }) { padding ->
                Box(Modifier.padding(padding)) {
                    SignsScreen(SignCatalogLoadState(signs, false), mode.value.translationTag,
                        SignProgress(viewed = setOf("A 12a")), {}, { _, _ -> })
                }
            }
        } } }
        compose.onNodeWithTag("category-warning").performClick()
        compose.onNodeWithTag("sign-search").performTextInput("A 12a")
        for(language in UiLanguage.entries) for(material in MaterialMode.entries) {
            compose.runOnIdle { ui.value = language; mode.value = material }
            compose.onNodeWithText(label(language, R.string.signs)).assertIsDisplayed()
            compose.onNodeWithTag("sign-count").assertTextEquals(context(language).resources.getQuantityString(R.plurals.sign_count, 408, 408))
            compose.onNodeWithTag("sign-search").assertTextContains("A 12a").assertTextContains(label(language, R.string.signs_search))
            compose.onNodeWithTag("category-warning").assertIsSelected().assertTextContains(label(language, R.string.sign_warning), substring = true)
            compose.onNodeWithContentDescription(label(language, R.string.sign_clear_search)).assertIsDisplayed()
            compose.onNodeWithTag("sign-A 12a").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label(language, R.string.sign_viewed)))
            compose.onNodeWithContentDescription(label(language, R.string.favorite_add)).assertIsDisplayed()
            compose.onNodeWithTag("sign-A 12a-title-cs", useUnmergedTree = true).assertTextEquals("Chodci")
            val helper = when(material) { MaterialMode.CS_ONLY -> null; MaterialMode.CS_RU -> "Пешеходы"; MaterialMode.CS_UK -> "Пішоходи" }
            if(helper == null) compose.onNodeWithTag("sign-A 12a-helper", useUnmergedTree = true).assertDoesNotExist()
            else compose.onNodeWithTag("sign-A 12a-helper", useUnmergedTree = true).assertTextEquals(helper)
            compose.onNodeWithTag("sign-filters").performClick()
            compose.onNodeWithText(label(language, R.string.sign_favorites_only)).assertIsDisplayed()
            compose.onNodeWithTag("sign-favorites-filter").assertContentDescriptionEquals(label(language, R.string.sign_favorites_only))
            for((filter, id) in listOf("ALL" to R.string.sign_state_all, "VIEWED" to R.string.sign_viewed, "UNVIEWED" to R.string.sign_unviewed))
                compose.onNodeWithTag("filter-$filter").assertTextContains(label(language, id), substring = true)
            compose.onNodeWithText(label(language, R.string.close)).performClick()
        }
    }

    @Test fun detailAndPopupChromeRemainIndependentFromLearningCopyInAllNineCombinations() {
        val sign = SignCatalog.load(app).single { it.code == "A 12a" }
        val ui = mutableStateOf(UiLanguage.CS)
        val mode = mutableStateOf(MaterialMode.CS_RU)
        compose.setContent { InterfaceLanguage(ui.value) { AutoSkolaTheme {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { error("No browser available in this test") }
            }) {
                SignDetailScreen(sign, mode.value.translationTag, words, mode.value != MaterialMode.CS_ONLY,
                    false, {}, {}, emptySet(), {}, lookupTipSeen = true)
            }
        } } }
        scroll(sign.code, "sign-additional"); compose.onNodeWithTag("sign-additional").performClick()
        scroll(sign.code, "sign-source"); compose.onNodeWithTag("sign-source").performClick()
        for(language in UiLanguage.entries) for(material in MaterialMode.entries) {
            compose.runOnIdle { ui.value = language; mode.value = material }
            for((section, cs, heading) in listOf(Triple("meaning", sign.meaningCs!!, R.string.sign_meaning_title),
                Triple("action", sign.driverActionsCs!!, R.string.sign_action_title),
                Triple("memory", sign.memoryAdvice!!, R.string.sign_memory_title),
                Triple("additional-0", sign.additionalLearningTexts.first(), R.string.sign_more_information))) {
                scroll(sign.code, "$section-cs"); compose.onNodeWithTag("$section-cs").assertTextEquals(cs)
                compose.onNodeWithText(label(language, heading)).assertExists()
                if(material == MaterialMode.CS_ONLY) compose.onNodeWithTag("$section-helper").assertDoesNotExist()
                else compose.onNodeWithTag("$section-helper").assertTextEquals(sign.helperFor(cs, material.translationTag)!!)
            }
            scroll(sign.code, "sign-source"); compose.onNodeWithTag("sign-source").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label(language, R.string.sign_section_expanded)))
            compose.onNodeWithText(label(language, R.string.sign_source_title)).assertIsDisplayed()
            compose.onNodeWithText(sign.sourceProvision!!).assertExists() // Official citation stays verbatim.
            scroll(sign.code, "sign-source-open"); compose.onNodeWithText(label(language, R.string.sign_source_open)).performClick()
            scroll(sign.code, "sign-source-error"); compose.onNodeWithText(label(language, R.string.sign_source_unavailable)).assertIsDisplayed()
            scroll(sign.code, "sign-title-cs"); compose.onNodeWithTag("sign-title-cs").assertTextEquals("A 12a · Chodci")
            if(material == MaterialMode.CS_ONLY) {
                compose.onNodeWithTag("sign-title-helper").assertDoesNotExist()
                tap("sign-title-cs", "Chodci")
                compose.onNodeWithTag("learning-word-popup").assertDoesNotExist()
            } else {
                compose.onNodeWithTag("sign-title-helper").assertTextEquals(if(material == MaterialMode.CS_RU) sign.titleRu!! else sign.titleUk!!)
                val action = compose.onNodeWithTag("sign-title-cs").fetchSemanticsNode().config[SemanticsActions.CustomActions]
                assertTrue(action.any { it.label == context(language).getString(R.string.word_translation_action, "Chodci") })
                tap("sign-title-cs", "Chodci")
                val entry = words.single { it.locale == material.translationTag }
                compose.onNodeWithTag("word-translation").assertTextEquals(entry.translation!!)
                compose.onNodeWithTag("word-explanation").assertTextEquals(entry.meaning!!)
                compose.onNodeWithText(if(material == MaterialMode.CS_RU) "RU" else "UA").assertIsDisplayed()
                compose.onNodeWithTag("translation-save").assertTextContains(label(language, R.string.save_word), substring = true)
                compose.onNodeWithContentDescription(label(language, R.string.close)).performClick()
            }
        }
    }

    @Test fun fallbackLoadingErrorAndTipUseInterfaceLanguageEvenWithTheOtherHelperLanguage() {
        val ui = mutableStateOf(UiLanguage.CS)
        val mode = mutableStateOf(MaterialMode.CS_RU)
        val status = mutableStateOf(DictionaryLoadState.READY)
        val sign = SignCatalog.load(app).single { it.code == "P 1" }
        compose.setContent { InterfaceLanguage(ui.value) { AutoSkolaTheme {
            SignDetailScreen(sign, mode.value.translationTag, emptyList(), true, false, {}, {}, emptySet(), {},
                dictionaryState = status.value)
        } } }
        scroll(sign.code, "word-tip")
        for(language in UiLanguage.entries) {
            compose.runOnIdle { ui.value = language }
            compose.onNodeWithText(label(language, R.string.sign_word_hint)).assertIsDisplayed()
        }
        for(language in UiLanguage.entries) for(material in listOf(MaterialMode.CS_RU, MaterialMode.CS_UK)) {
            compose.runOnIdle { ui.value = language; mode.value = material; status.value = DictionaryLoadState.READY }
            scroll(sign.code, "memory-cs")
            tap("memory-cs", "Tvar")
            compose.onNodeWithTag("word-tip").assertDoesNotExist()
            compose.onNodeWithTag("word-translation-unavailable").assertTextEquals(label(language, R.string.word_translation_unavailable))
            compose.onNodeWithTag("translation-save").assertTextContains(label(language, R.string.save_word), substring = true)
            for(state in listOf(DictionaryLoadState.LOADING, DictionaryLoadState.ERROR)) {
                compose.runOnIdle { status.value = state }
                compose.onNodeWithTag("word-dictionary-status").assertTextEquals(label(language,
                    if(state == DictionaryLoadState.LOADING) R.string.word_dictionary_loading else R.string.word_dictionary_error))
            }
            compose.onNodeWithTag("translation-close").performClick()
        }
    }

    private fun scroll(code: String, tag: String) {
        compose.onNodeWithTag("sign-detail-$code").performScrollToNode(hasTestTag(tag))
    }

    private fun tap(tag: String, word: String) {
        val node = compose.onNodeWithTag(tag)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val result = layouts.single()
        val box = result.getBoundingBox(result.layoutInput.text.text.indexOf(word))
        node.performTouchInput { click(Offset(box.center.x, box.center.y)) }
    }
}
