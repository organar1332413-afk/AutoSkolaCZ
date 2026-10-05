package cz.autoskola.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.autoskola.app.navigation.AppNavigation
import cz.autoskola.design.AutoSkolaTheme
import cz.autoskola.app.ui.InterfaceLanguage
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModel.Factory((application as AutoSkolaApplication).container))
            val settings by vm.settings.collectAsStateWithLifecycle()
            InterfaceLanguage(settings.uiLanguage) {
                AutoSkolaTheme { AppNavigation(vm, settings) }
            }
        }
    }
}
