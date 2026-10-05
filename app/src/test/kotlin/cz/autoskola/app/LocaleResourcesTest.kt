package cz.autoskola.app
import android.app.Application
import android.content.res.Configuration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import cz.autoskola.app.feature.catalog.SignCategoryLabels
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class LocaleResourcesTest {
    private fun context(tag:String)=RuntimeEnvironment.getApplication().let { app->app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }) }
    @Test fun czechNavigationUsesCzechResources() { val c=context("cs");assertEquals("Domů",c.getString(R.string.home));assertEquals("Profil",c.getString(R.string.profile));assertEquals("Zkouška",c.getString(R.string.exam)) }
    @Test fun russianNavigationUsesRussianResources() { val c=context("ru");assertEquals("Главная",c.getString(R.string.home));assertEquals("Профиль",c.getString(R.string.profile));assertEquals("Экзамен",c.getString(R.string.exam)) }
    @Test fun ukrainianNavigationUsesUkrainianResources() { val c=context("uk");assertEquals("Головна",c.getString(R.string.home));assertEquals("Профіль",c.getString(R.string.profile));assertEquals("Іспит",c.getString(R.string.exam)) }
    @Test fun switchingContextDoesNotModifyOtherLocaleResources() { val ru=context("ru");val cs=context("cs");val uk=context("uk");assertEquals("Главная",ru.getString(R.string.home));assertEquals("Domů",cs.getString(R.string.home));assertEquals("Головна",uk.getString(R.string.home)) }
    @Test fun translatedFormatArgumentsRenderWithoutCrashing() { listOf("cs","ru","uk").forEach { tag->val c=context(tag);assertTrue(c.getString(R.string.mastery,2).contains("2"));assertTrue(c.getString(R.string.stats_sample,3,1).contains("3"));assertTrue(c.getString(R.string.stats_reason_count,"TEST",4).contains("4"));assertTrue(c.getString(R.string.lesson_step,1,10).contains("10")) } }
    @Test fun groupAwareExamAndStoredScoresFormatInEveryLocale() { listOf("cs","ru","uk").forEach { tag ->
        val c=context(tag)
        assertTrue(c.getString(R.string.exam_ready,"CE").contains("CE"))
        assertTrue(c.getString(R.string.exam_unavailable,"A").contains("A"))
        assertTrue(c.getString(R.string.exam_content_incomplete,"D").contains("D"))
        assertTrue(c.getString(R.string.exam_media_incomplete,"BE").contains("BE"))
        assertTrue(c.getString(R.string.exam_result_score,42,60).contains("60"))
        assertTrue(c.getString(R.string.stats_history_score,42,60).contains("60"))
        assertTrue(c.getString(R.string.home_last_exam_score,42,60).contains("60"))
    } }
    @Test fun signCountsUseRealCzechRussianAndUkrainianPlurals() {
        val counts = listOf(0, 1, 2, 5, 11, 21, 22, 25, 408)
        val expected = mapOf(
            "cs" to listOf("značek", "značka", "značky", "značek", "značek", "značek", "značek", "značek", "značek"),
            "ru" to listOf("знаков", "знак", "знака", "знаков", "знаков", "знак", "знака", "знаков", "знаков"),
            "uk" to listOf("знаків", "знак", "знаки", "знаків", "знаків", "знак", "знаки", "знаків", "знаків")
        )
        expected.forEach { (locale, nouns) -> counts.forEachIndexed { i, count ->
            assertEquals("$locale/$count", "$count ${nouns[i]}", context(locale).resources.getQuantityString(R.plurals.sign_count, count, count))
        } }
    }
    @Test fun allLegalCategoryKeysHaveDistinctLabelsInEveryInterfaceLanguage() {
        assertEquals(11, SignCategoryLabels.size)
        listOf("cs", "ru", "uk").forEach { tag ->
            val labels = SignCategoryLabels.values.map { context(tag).getString(it) }
            assertEquals(tag, 11, labels.toSet().size)
            assertTrue(labels.all { it.isNotBlank() })
        }
    }
}
