package cz.autoskola.app.feature.firstaid

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import cz.autoskola.app.ui.InterfaceLanguage
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirstAidNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController
    private lateinit var settings: MutableState<UserSettings>
    private val bundle get() = FirstAidContent.load(RuntimeEnvironment.getApplication())
    private fun launch() {
        val b = bundle
        settings = mutableStateOf(UserSettings(uiLanguage = UiLanguage.RU, materialMode = MaterialMode.CS_UK))
        compose.setContent {
            InterfaceLanguage(settings.value.uiLanguage) { AutoSkolaTheme {
                nav = rememberNavController()
                val state = rememberUpdatedState(AidDestinationState(AidLoadState(b, loading = false), settings.value, emptyList()))
                NavHost(nav, AidRoutes.catalog) { firstAidDestinations(nav, state, {}, {}, {}, {}) }
            } }
        }
    }
    @Test fun separateDetailBackRestoresSearchCategoryAndScroll() {
        launch()
        compose.onNodeWithTag("aid-category-trauma").performClick()
        compose.onNodeWithTag("aid-grid").performScrollToIndex(5)
        val before=compose.onNodeWithTag("aid-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithTag("aid-C13").performClick()
        compose.onNodeWithTag("aid-detail-C13").assertIsDisplayed()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithTag("aid-C13").assertIsDisplayed()
        val after=compose.onNodeWithTag("aid-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before,after,0.001f)
        compose.onNodeWithTag("aid-category-trauma").assertIsSelected()
        compose.onNodeWithTag("aid-search").performTextInput("RP1102024")
        compose.onNodeWithTag("aid-C13").performClick()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithTag("aid-search").assertTextContains("RP1102024")
    }
    @Test fun independentUiLanguageAndMaterialUpdateInsideCachedDetail() {
        launch()
        compose.onNodeWithTag("aid-search").performTextInput("C05")
        compose.onNodeWithTag("aid-C05").performClick()
        compose.onNodeWithText("Якісний масаж серця").assertIsDisplayed()
        compose.onNodeWithText("К экзамену").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { settings.value=settings.value.copy(uiLanguage=UiLanguage.CS) }
        compose.onNodeWithText("Ke zkoušce").assertIsDisplayed()
        compose.onNodeWithText("Якісний масаж серця").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { settings.value=settings.value.copy(materialMode=MaterialMode.CS_RU) }
        compose.onNodeWithText("Качественный массаж сердца").assertIsDisplayed()
        compose.onNodeWithText("Якісний масаж серця").assertDoesNotExist()
        compose.runOnIdle { settings.value=settings.value.copy(level=LearningLevel.EXAM) }
        compose.onNodeWithText("Качественный массаж сердца").assertDoesNotExist()
    }
    @Test fun officialQuestionLinksBackToTheCorrectTeachingCard() {
        launch()
        compose.onNodeWithTag("aid-all-questions").performClick()
        compose.onNodeWithTag("aid-question-search").performTextInput("RP1309001")
        compose.onNodeWithTag("aid-rp-RP1309001").performClick()
        compose.onNodeWithTag("aid-question-detail-RP1309001").assertIsDisplayed()
        compose.onNodeWithTag("aid-question-card-C15").performScrollTo().performClick()
        compose.onNodeWithTag("aid-detail-C15").assertIsDisplayed()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithTag("aid-question-detail-RP1309001").assertIsDisplayed()
    }
    @Test fun nextCardReplacesDetailSoBackReturnsDirectlyToCatalog() {
        launch()
        compose.onNodeWithTag("aid-search").performTextInput("C05")
        compose.onNodeWithTag("aid-C05").performClick()
        compose.onNodeWithTag("aid-detail-C05").performScrollToNode(hasTestTag("aid-next"))
        compose.onNodeWithTag("aid-next").assertIsDisplayed().performClick()
        compose.onNodeWithTag("aid-detail-C06").assertIsDisplayed()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithTag("aid-catalog").assertIsDisplayed()
        compose.onNodeWithTag("aid-search").assertTextContains("C05")
    }
    @Test fun wordPopupUsesRealHelperAndBackDismissesItBeforeNavigation() {
        launch()
        compose.onNodeWithTag("aid-search").performTextInput("C05")
        compose.onNodeWithTag("aid-C05").performClick()
        val title = compose.onAllNodesWithTag("aid-learning-cs")[0]
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        title.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val pos=layouts.single().getBoundingBox("Kvalitní ".length)
        title.performTouchInput { click(pos.center) }
        compose.onNodeWithTag("learning-word-popup").assertIsDisplayed()
        compose.onNodeWithTag("word-translation").assertTextEquals("серцевий")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("learning-word-popup").assertDoesNotExist()
        compose.onNodeWithTag("aid-detail-C05").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("aid-catalog").assertIsDisplayed()
    }
    @Test fun allSixteenDetailsRenderWithAccessibleIllustrationsAndScreenshots() {
        launch()
        screenshot("catalog.png")
        bundle.cards.forEach { c ->
            compose.runOnIdle { nav.navigate(AidRoutes.detail(c.id)) }
            compose.onNodeWithTag("aid-detail-${c.id}").assertIsDisplayed()
            compose.onNodeWithTag("aid-image-${c.id}").assertContentDescriptionEquals(c.title.ru)
            (0..2).forEach { compose.onNodeWithTag("aid-badge-$it").assertExists() }
            compose.onNodeWithTag("aid-progress").assertExists()
            compose.onNodeWithTag("aid-clinical-toggle").assertDoesNotExist()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("aid-image-ready-${c.id}").fetchSemanticsNodes().isNotEmpty() }
            screenshot("${c.id}.png")
            compose.runOnIdle { nav.popBackStack() }
        }
    }
    @Test fun nextCardBackAlsoPreservesCategoryAndCatalogScroll() {
        launch()
        compose.onNodeWithTag("aid-category-all").performClick()
        compose.onNodeWithTag("aid-grid").performScrollToIndex(4)
        val before = compose.onNodeWithTag("aid-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithTag("aid-C05").performClick()
        compose.onNodeWithTag("aid-detail-C05").performScrollToNode(hasTestTag("aid-next"))
        compose.onNodeWithTag("aid-next").performClick()
        compose.onNodeWithTag("aid-detail-C06").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("aid-category-all").assertIsSelected()
        val after = compose.onNodeWithTag("aid-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 0.001f)
    }
    @Test @Config(qualifiers = "w320dp-h740dp")
    fun narrowScreensKeepAllThreeHintsAndIllustrationsAvailable() {
        launch()
        bundle.cards.forEach { c ->
            compose.runOnIdle { nav.navigate(AidRoutes.detail(c.id)) }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("aid-image-ready-${c.id}").fetchSemanticsNodes().isNotEmpty() }
            (0..2).forEach { compose.onNodeWithTag("aid-badge-$it").assertIsDisplayed() }
            screenshot("narrow-${c.id}.png")
            compose.runOnIdle { nav.popBackStack() }
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val file=File("build/outputs/first-aid/$name").apply { parentFile.mkdirs() }
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
