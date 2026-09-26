package cz.autoskola.app.feature.catalog

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SignCatalogTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun catalogAssetsLoadAndSearchByCodeOrCzechName() {
        val signs = SignCatalog.load(context)
        assertEquals(347, signs.size)
        assertEquals(347, signs.map { it.code }.toSet().size)
        assertEquals("P 4", SignCatalog.search(signs, "p 4", null).single().code)
        assertEquals("P 4", SignCatalog.search(signs, "Dej přednost", "priority").first().code)
        assertTrue(SignCatalog.search(signs, "V 7a", "light_signal").isEmpty())
        assertNull(signs.single { it.code == "P 4" }.meaningCs)
        assertTrue(signs.single { it.code == "A 1a" }.ru!!.contains("повороте"))
    }

    @Test fun sourceBackedGuideLoadedSeparatelyFromOfficialExcerpt() {
        val blocks = SignCatalog.loadGuide(context)
        assertEquals(4, blocks.size)
        assertTrue(blocks.any { it.provision.startsWith("§ 3") && it.officialTextCs != null })
    }
}
