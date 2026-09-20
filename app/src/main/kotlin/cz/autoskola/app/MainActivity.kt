package cz.autoskola.app
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.autoskola.app.navigation.AppNavigation
import cz.autoskola.design.AutoSkolaTheme
import java.util.Locale
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModel.Factory((application as AutoSkolaApplication).container))
            val settings by vm.settings.collectAsStateWithLifecycle()
            val base = LocalContext.current
            val configuration = LocalConfiguration.current
            val localized = remember(base, configuration, settings.uiLanguage) {
                val config = Configuration(configuration)
                config.setLocale(Locale.forLanguageTag(settings.uiLanguage.tag))
                base.createConfigurationContext(config)
            }
            CompositionLocalProvider(LocalContext provides localized) {
                AutoSkolaTheme { AppNavigation(vm, settings) }
            }
        }
    }
}
