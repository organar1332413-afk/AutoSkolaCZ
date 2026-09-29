package cz.autoskola.app.feature.catalog

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        assertEquals(408, signs.size)
        assertEquals(signs.size, signs.map { it.code }.toSet().size)
        assertEquals("C 5b", SignCatalog.search(signs, "c 5b", "mandatory").single().code)
        assertEquals("P 4", SignCatalog.search(signs, "p 4", null).single().code)
        assertEquals("P 4", SignCatalog.search(signs, "Dej přednost", "priority").first().code)
        assertTrue(SignCatalog.search(signs, "V 7a", "light_signal").isEmpty())
        assertNotNull(signs.single { it.code == "P 4" }.meaningCs)
        assertTrue(signs.single { it.code == "A 1a" }.ru!!.contains("повороте"))
        assertEquals("A 31b", SignCatalog.search(signs, "a 31b", null).single().code)
        assertEquals("A 31c", SignCatalog.search(signs, "80 м", "warning").single().code)
        assertEquals("A 6b", SignCatalog.search(signs, "Сужение дороги с одной стороны", null).single().code)
        assertEquals("Пішоходи", signs.single { it.code == "A 12a" }.titleUk)
        assertEquals(listOf("RP2202014"), signs.single { it.code == "B 20a" }.relatedOfficialIds)
    }

    @Test fun sourceBackedGuideLoadedSeparatelyFromOfficialExcerpt() {
        val blocks = SignCatalog.loadGuide(context)
        assertEquals(4, blocks.size)
        assertTrue(blocks.any { it.provision.startsWith("§ 3") && it.officialTextCs != null })
    }
}
