package cz.autoskola.app
import androidx.compose.runtime.Composable
import cz.autoskola.domain.*
/** Release has no developer UI, controller, resources or sample bootstrap. */
@Composable internal fun BuildExtras(vm:MainViewModel,settings:UserSettings,status:ContentStatus?,open:(String)->Unit) = Unit
internal suspend fun initializeBuildContent(container:AppContainer) = Unit
