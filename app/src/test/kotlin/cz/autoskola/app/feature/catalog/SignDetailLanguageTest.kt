package cz.autoskola.app.feature.catalog

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import cz.autoskola.design.AutoSkolaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignDetailLanguageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun allThreeBlocksAndAdditionalTextFollowGlobalLanguageAndEverySectionIsTappable() {
        val sign = SignCatalog.load(RuntimeEnvironment.getApplication()).single { it.code == "A 12a" }
        val tag = mutableStateOf<String?>("ru")
        compose.setContent { AutoSkolaTheme {
            SignDetailScreen(sign, tag.value, emptyList(), tag.value != null, false, {}, {}, emptySet(), {}, lookupTipSeen = true)
        } }
        for(language in listOf("ru", "uk")) {
            compose.runOnIdle { tag.value = language }
            for((section, value) in listOf("meaning" to sign.meaningCs!!, "action" to sign.driverActionsCs!!, "memory" to sign.memoryCs!!)) {
                val helper = sign.helperFor(value, language)
                assertFalse(helper.isNullOrBlank())
                compose.onNodeWithTag("sign-detail-A 12a").performScrollToNode(hasTestTag("$section-helper"))
                compose.onNodeWithTag("$section-helper").assertTextEquals(helper!!)
                tapFirstWord("$section-cs")
                compose.onNodeWithTag("learning-word-popup").assertIsDisplayed()
                compose.onNodeWithTag("translation-close").performClick()
            }
        }
        compose.onNodeWithText("Další informace").performScrollTo().performClick()
        compose.onNodeWithTag("additional-0-helper").performScrollTo().assertTextEquals(sign.helperFor(sign.mistakeCs!!, "uk")!!)
        tapFirstWord("additional-0-cs")
        compose.onNodeWithTag("learning-word-popup").assertIsDisplayed()
        compose.onNodeWithTag("translation-close").performClick()
        compose.onNodeWithTag("sign-title-cs").performScrollTo()
        tapFirstWord("sign-title-cs", "Chodci")
        compose.onNodeWithTag("translation-token").assertTextEquals("Chodci")
        compose.onNodeWithTag("translation-close").performClick()
        compose.runOnIdle { tag.value = null }
        for(section in listOf("meaning", "action", "memory", "additional-0")) {
            compose.onNodeWithTag("$section-cs").performScrollTo()
            compose.onNodeWithTag("$section-helper").assertDoesNotExist()
        }
        compose.onNodeWithText("Пішоходи").assertDoesNotExist()
    }
    @Test fun everyRenderedTeachingTextHasBothHelperLanguagesForAll408Signs() {
        val signs = SignCatalog.load(RuntimeEnvironment.getApplication())
        assertEquals(408, signs.size)
        signs.forEach { sign ->
            (listOfNotNull(sign.meaningCs, sign.driverActionsCs, sign.memoryCs, sign.mistakeCs, sign.simpleCs) + sign.exceptionsCs)
                .distinct().forEach { cs ->
                    for(locale in listOf("ru", "uk")) assertFalse("${sign.code}: missing $locale for $cs", sign.helperFor(cs, locale).isNullOrBlank())
                    assertNull(sign.helperFor(cs, null))
                }
        }
    }
    private fun tapFirstWord(tag: String, token: String? = null) {
        val node = compose.onNodeWithTag(tag)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val result = layouts.single()
        val index = token?.let { result.layoutInput.text.text.indexOf(it) } ?: 0
        val box = result.getBoundingBox(index)
        assertTrue("Laid-out word has nonzero width: $box", box.width > 0)
        node.performTouchInput { click(Offset(box.center.x, box.center.y)) }
    }
}
