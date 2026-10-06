package cz.autoskola.app.feature.catalog

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import cz.autoskola.app.ui.InterfaceLanguage
import cz.autoskola.app.ui.text
import cz.autoskola.app.R
import cz.autoskola.design.AutoSkolaTheme
import cz.autoskola.design.AppTopBar
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
class SignsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController
    private val entries get() = SignCatalog.load(RuntimeEnvironment.getApplication())
    private lateinit var catalogState: MutableState<SignCatalogLoadState>
    private lateinit var settingsState: MutableState<UserSettings>
    private fun launch(loading: Boolean = false) {
        catalogState = mutableStateOf(if(loading) SignCatalogLoadState() else SignCatalogLoadState(entries, loading = false))
        settingsState = mutableStateOf(UserSettings(materialMode = MaterialMode.CS_RU))
        compose.setContent {
            InterfaceLanguage(settingsState.value.uiLanguage) { AutoSkolaTheme {
                nav = rememberNavController()
                var progress by remember { mutableStateOf(SignProgress()) }
                val destinationState = rememberUpdatedState(SignDestinationState(
                    catalogState.value, progress, settingsState.value, emptyList(), emptySet()))
                val back by nav.currentBackStackEntryAsState()
                Scaffold(topBar = {
                    AppTopBar(text(requireNotNull(SignRoutes.titleResource(back?.destination?.route ?: SignRoutes.catalog))),
                        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, text(R.string.back)) } },
                        actions = { IconButton(onClick = {}) { Icon(Icons.Default.Settings, text(R.string.profile)) } })
                }) { padding ->
                    Box(Modifier.padding(padding)) {
                        NavHost(nav, SignRoutes.catalog) {
                            signDestinations(nav, destinationState,
                                { progress = progress.copy(viewed = progress.viewed + it) },
                                { code, selected -> progress = progress.copy(favorites = if(selected) progress.favorites + code else progress.favorites - code) },
                                {}, openQuestion = {})
                        }
                    }
                }
            }
        } }
    }
    @Test fun cachedGraphObservesLoadedCatalogAndLanguageChangesOnTheOpenDetail() {
        launch(loading = true)
        compose.onNodeWithTag("sign-grid").assertDoesNotExist()
        compose.runOnIdle { catalogState.value = SignCatalogLoadState(entries, loading = false) }
        compose.onNodeWithTag("sign-grid").assertIsDisplayed()
        compose.onNodeWithTag("sign-search").performTextInput("A 10")
        compose.onNodeWithTag("sign-A 10").performClick()
        compose.onNodeWithText("Светофоры").assertIsDisplayed()
        compose.runOnIdle { settingsState.value = UserSettings(materialMode = MaterialMode.CS_UK) }
        compose.onNodeWithText("Світлофори").assertIsDisplayed()
        compose.onNodeWithText("Светофоры").assertDoesNotExist()
        compose.runOnIdle { settingsState.value = UserSettings(materialMode = MaterialMode.CS_UK, level = LearningLevel.EXAM) }
        compose.onNodeWithText("Світлофори").assertDoesNotExist()
    }
    @Test fun catalogSearchAndCategorySurviveDetailAndBack() {
        launch()
        compose.onNodeWithTag("category-warning").performClick()
        compose.onNodeWithTag("sign-search").performTextInput("A 10")
        compose.onNodeWithTag("sign-A 10").performClick()
        compose.onNodeWithTag("sign-detail-A 10").assertIsDisplayed()
        compose.runOnIdle { assertEquals("A 10", nav.currentBackStackEntry?.arguments?.getString("code")); nav.popBackStack() }
        compose.onNodeWithTag("sign-catalog").assertIsDisplayed()
        compose.onNodeWithTag("sign-search").assertTextContains("A 10")
        compose.onNodeWithTag("category-warning").assertIsSelected()
        compose.onNodeWithTag("sign-A 10").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Prohlédnuto"))
    }
    @Test fun deepScrollRestoresAndFavoriteCoexistsWithViewed() {
        launch()
        compose.onNodeWithTag("category-warning").performClick()
        compose.onNodeWithTag("sign-grid").performScrollToIndex(18)
        val before = compose.onNodeWithTag("sign-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val code = entries.filter { it.category == "warning" }[18].code
        compose.onNodeWithTag("sign-$code").performClick()
        compose.onNodeWithTag("detail-favorite").performClick()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithTag("sign-$code").assertIsDisplayed()
        val after = compose.onNodeWithTag("sign-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 0.001f)
        compose.onNodeWithTag("sign-$code").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Prohlédnuto"))
        compose.onNodeWithTag("favorite-$code").assertIsOn()
    }
    @Test fun interfaceAndMaterialChangesReachCachedDestinationsWithoutResettingCatalog() {
        launch()
        compose.runOnIdle { settingsState.value = UserSettings(uiLanguage = UiLanguage.RU, materialMode = MaterialMode.CS_UK) }
        compose.onNodeWithText("Дорожные знаки").assertIsDisplayed()
        compose.onNodeWithTag("category-warning").performClick()
        compose.onNodeWithTag("sign-grid").performScrollToIndex(18)
        val before = compose.onNodeWithTag("sign-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val sign = entries.filter { it.category == "warning" }[18]
        compose.onNodeWithTag("sign-${sign.code}").performClick()
        compose.onNodeWithText("Дорожный знак").assertIsDisplayed()
        compose.onNodeWithTag("sign-title-helper").assertTextEquals(sign.titleUk!!)
        compose.runOnIdle { settingsState.value = settingsState.value.copy(uiLanguage = UiLanguage.UK) }
        compose.onNodeWithText("Дорожній знак").assertIsDisplayed()
        compose.onNodeWithTag("sign-title-helper").assertTextEquals(sign.titleUk!!)
        compose.runOnIdle { settingsState.value = settingsState.value.copy(materialMode = MaterialMode.CS_RU) }
        compose.onNodeWithText("Дорожній знак").assertIsDisplayed()
        compose.onNodeWithTag("sign-title-helper").assertTextEquals(sign.titleRu!!)
        compose.runOnIdle { settingsState.value = settingsState.value.copy(uiLanguage = UiLanguage.RU, materialMode = MaterialMode.CS_UK); nav.popBackStack() }
        compose.onNodeWithText("Дорожные знаки").assertIsDisplayed()
        compose.onNodeWithTag("category-warning").assertIsSelected()
        compose.onNodeWithTag("sign-${sign.code}").assertIsDisplayed()
        val after = compose.onNodeWithTag("sign-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 0.001f)
        compose.onNodeWithTag("sign-${sign.code}").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Просмотрено"))
    }
    @Test fun popupBackDismissesBeforeDetailAndThenRestoresDeepCatalogPosition() {
        launch()
        compose.onNodeWithTag("category-warning").performClick()
        compose.onNodeWithTag("sign-grid").performScrollToIndex(18)
        val before = compose.onNodeWithTag("sign-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val sign = entries.filter { it.category == "warning" }[18]
        compose.onNodeWithTag("sign-${sign.code}").performClick()
        val title = compose.onNodeWithTag("sign-title-cs").performScrollTo()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        title.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val box = layouts.single().getBoundingBox("${sign.code} · ".length)
        title.performTouchInput { click(androidx.compose.ui.geometry.Offset(box.center.x, box.center.y)) }
        compose.onNodeWithTag("learning-word-popup").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("learning-word-popup").assertDoesNotExist()
        compose.onNodeWithTag("sign-detail-${sign.code}").assertIsDisplayed()
        compose.runOnIdle { assertEquals(SignRoutes.detailPattern, nav.currentDestination?.route); compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("sign-catalog").assertIsDisplayed()
        val after = compose.onNodeWithTag("sign-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 0.001f)
        compose.onNodeWithTag("sign-${sign.code}").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Prohlédnuto"))
    }
    @Test fun renderCatalogAndSeparateDetailScreenshots() {
        launch()
        screenshot("signs-catalog.png")
        compose.onNodeWithTag("sign-search").performTextInput("A 10")
        compose.onNodeWithTag("sign-A 10").performClick()
        compose.onNodeWithTag("sign-detail-A 10").assertIsDisplayed()
        screenshot("signs-detail.png")
        compose.onNodeWithTag("detail-favorite").performClick()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithTag("sign-search").performTextClearance()
        compose.onNodeWithTag("favorite-A 10").assertIsOn()
        screenshot("signs-viewed-favorite.png")
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            // PixelCopy needs a real surface; native Robolectric renders the same view into Canvas.
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = File("build/outputs/premium-ui/$name").apply { parentFile.mkdirs() }
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
