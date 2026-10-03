package cz.autoskola.app.feature.catalog

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import cz.autoskola.app.R
import cz.autoskola.app.ui.text
import cz.autoskola.design.PremiumSpace
import cz.autoskola.domain.*
import cz.autoskola.data.DictionaryLoadState

object SignRoutes {
    const val catalog = "signs"
    const val detailPattern = "signs/{code}"
    fun detail(code: String) = "signs/${Uri.encode(code)}"
}

data class SignDestinationState(
    val catalog: SignCatalogLoadState,
    val progress: SignProgress,
    val settings: UserSettings,
    val words: List<Lexeme>,
    val availableQuestionIds: Set<String>,
    val lookupTipSeen: Boolean = false,
    val dictionaryState: DictionaryLoadState = DictionaryLoadState.READY
)

/**
 * Two real destinations. Navigation caches graph builders: read observable state INSIDE
 * each destination rather than capturing a one-time snapshot in the graph builder.
 */
fun NavGraphBuilder.signDestinations(
    nav: NavHostController, state: State<SignDestinationState>, markViewed: (String) -> Unit,
    favorite: (String, Boolean) -> Unit, saveWord: (String) -> Unit,
    retry: () -> Unit = {}, saveUnknownWord: (String) -> Unit = {}, dismissLookupTip: () -> Unit = {},
    openQuestion: (String) -> Unit
) {
    composable(SignRoutes.catalog) {
        val current = state.value
        SignsScreen(current.catalog, current.settings.policy().translationTag, current.progress,
            { nav.navigate(SignRoutes.detail(it)) { launchSingleTop = true } }, favorite, retry)
    }
    composable(SignRoutes.detailPattern) { back ->
        val current = state.value
        val code = back.arguments?.getString("code")
        val sign = current.catalog.entries.find { it.code == code }
        when {
            current.catalog.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            current.catalog.failed -> Column(Modifier.fillMaxWidth().padding(PremiumSpace.lg)) {
                Text(text(R.string.sign_catalog_error))
                cz.autoskola.design.PrimaryButton(text(R.string.retry), retry)
            }
            sign == null -> Box(Modifier.fillMaxSize().padding(PremiumSpace.lg), contentAlignment = Alignment.Center) { Text(text(R.string.sign_detail_missing)) }
            else -> {
                LaunchedEffect(sign.code) { markViewed(sign.code) }
                SignDetailScreen(sign, current.settings.policy().translationTag, current.words,
                    current.settings.policy().canLookup, sign.code in current.progress.favorites,
                    { favorite(sign.code, it) }, saveWord, current.availableQuestionIds, openQuestion, saveUnknownWord, current.lookupTipSeen, dismissLookupTip, current.dictionaryState)
            }
        }
    }
}
