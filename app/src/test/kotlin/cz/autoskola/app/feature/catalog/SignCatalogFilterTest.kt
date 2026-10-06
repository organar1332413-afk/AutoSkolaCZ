package cz.autoskola.app.feature.catalog

import android.app.Application
import cz.autoskola.domain.SignProgress
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SignCatalogFilterTest {
    private val signs get() = SignCatalog.load(RuntimeEnvironment.getApplication())
    @Test fun everyCategoryIsDistinctAndMatchesExistingKeys() {
        assertEquals(signs.map { it.category }.toSet(), SignCategoryLabels.keys)
        assertEquals(SignCategoryLabels.size, SignCategoryLabels.values.toSet().size)
        SignCategoryLabels.keys.forEach { category ->
            val expected = signs.filter { it.category == category }
            assertEquals(expected, filterSigns(signs, SignCatalogFilter(category = category), SignProgress()))
        }
    }
    @Test fun searchCategoryFavoriteAndViewedFiltersCompose() {
        val progress = SignProgress(viewed = setOf("A 10", "P 4"), favorites = setOf("A 10", "A 11"))
        assertEquals(listOf("A 10"), filterSigns(signs,
            SignCatalogFilter(query = "světelné", category = "warning", favoritesOnly = true, view = SignViewFilter.VIEWED), progress).map { it.code })
        assertEquals(listOf("A 11"), filterSigns(signs,
            SignCatalogFilter(favoritesOnly = true, view = SignViewFilter.UNVIEWED), progress).map { it.code })
        assertTrue(filterSigns(signs, SignCatalogFilter(query = "P 4", category = "warning"), progress).isEmpty())
        assertEquals(listOf("P 4"), filterSigns(signs, SignCatalogFilter(query = "p 4"), progress).map { it.code })
    }
    @Test fun all408GraphicsAndActionsRemainAvailableAndUnique() {
        assertEquals(408, signs.size)
        assertEquals(408, signs.map { it.code }.toSet().size)
        signs.forEach {
            assertEquals("VERIFIED", it.graphicStatus)
            assertTrue(it.graphicPaths.isNotEmpty())
            it.graphicPaths.forEach { path -> RuntimeEnvironment.getApplication().assets.open("signs/$path").use { asset -> assertTrue(asset.read() != -1) } }
            assertFalse(it.driverActionsCs.isNullOrBlank())
            assertFalse(it.meaningCs.isNullOrBlank())
            assertFalse(it.sourceUrl.isBlank())
        }
    }
}
