package cz.autoskola.app.feature.catalog

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
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
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController
    private val entries get() = SignCatalog.load(RuntimeEnvironment.getApplication())
    private fun launch() {
        val catalog = SignCatalogLoadState(entries, loading = false)
        compose.setContent {
            AutoSkolaTheme {
                nav = rememberNavController()
                var progress by remember { mutableStateOf(SignProgress()) }
                val back by nav.currentBackStackEntryAsState()
                Scaffold(topBar = {
                    AppTopBar(if(back?.destination?.route == SignRoutes.catalog) "Dopravní značky" else "Dopravní značka",
                        navigationIcon = { if(back?.destination?.route != SignRoutes.catalog) TextButton(onClick = { nav.popBackStack() }) { Text("Zpět") } })
                }) { padding ->
                    Box(Modifier.padding(padding)) {
                        NavHost(nav, SignRoutes.catalog) {
                            signDestinations(nav, catalog, progress, UserSettings(materialMode = MaterialMode.CS_RU), emptyList(),
                                { progress = progress.copy(viewed = progress.viewed + it) },
                                { code, selected -> progress = progress.copy(favorites = if(selected) progress.favorites + code else progress.favorites - code) },
                                {}, emptySet(), {})
                        }
                    }
                }
            }
        }
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
    @Test fun renderCatalogAndSeparateDetailScreenshots() {
        launch()
        screenshot("signs-catalog.png")
        compose.onNodeWithTag("sign-search").performTextInput("A 10")
        compose.onNodeWithTag("sign-A 10").performClick()
        compose.onNodeWithTag("sign-detail-A 10").assertIsDisplayed()
        screenshot("signs-detail.png")
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File("build/outputs/premium-ui/$name").apply { parentFile.mkdirs() }
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
