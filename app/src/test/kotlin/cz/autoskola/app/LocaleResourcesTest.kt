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
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class LocaleResourcesTest {
    private fun context(tag:String)=RuntimeEnvironment.getApplication().let { app->app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }) }
    @Test fun czechNavigationUsesCzechResources() { val c=context("cs");assertEquals("Domů",c.getString(R.string.home));assertEquals("Profil",c.getString(R.string.profile));assertEquals("Zkouška",c.getString(R.string.exam)) }
    @Test fun russianNavigationUsesRussianResources() { val c=context("ru");assertEquals("Главная",c.getString(R.string.home));assertEquals("Профиль",c.getString(R.string.profile));assertEquals("Экзамен",c.getString(R.string.exam)) }
    @Test fun ukrainianNavigationUsesUkrainianResources() { val c=context("uk");assertEquals("Головна",c.getString(R.string.home));assertEquals("Профіль",c.getString(R.string.profile));assertEquals("Іспит",c.getString(R.string.exam)) }
    @Test fun switchingContextDoesNotModifyOtherLocaleResources() { val ru=context("ru");val cs=context("cs");val uk=context("uk");assertEquals("Главная",ru.getString(R.string.home));assertEquals("Domů",cs.getString(R.string.home));assertEquals("Головна",uk.getString(R.string.home)) }
    @Test fun translatedFormatArgumentsRenderWithoutCrashing() { listOf("cs","ru","uk").forEach { tag->val c=context(tag);assertTrue(c.getString(R.string.mastery,2).contains("2"));assertTrue(c.getString(R.string.stats_sample,3,1).contains("3"));assertTrue(c.getString(R.string.stats_reason_count,"TEST",4).contains("4"));assertTrue(c.getString(R.string.lesson_step,1,10).contains("10")) } }
}
