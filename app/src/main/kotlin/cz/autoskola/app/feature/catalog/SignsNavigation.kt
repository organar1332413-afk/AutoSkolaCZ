package cz.autoskola.app.feature.catalog

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import cz.autoskola.app.R
import cz.autoskola.app.ui.text
import cz.autoskola.design.PremiumSpace
import cz.autoskola.domain.*

object SignRoutes {
    const val catalog = "signs"
    const val detailPattern = "signs/{code}"
    fun detail(code: String) = "signs/${Uri.encode(code)}"
}

/** Two real destinations. The catalog entry is left on the back stack. */
fun NavGraphBuilder.signDestinations(
    nav: NavHostController, catalog: SignCatalogLoadState, progress: SignProgress,
    settings: UserSettings, words: List<Lexeme>, markViewed: (String) -> Unit,
    favorite: (String, Boolean) -> Unit, saveWord: (String) -> Unit,
    availableQuestionIds: Set<String>, openQuestion: (String) -> Unit
) {
    composable(SignRoutes.catalog) {
        SignsScreen(catalog, settings.materialMode.translationTag, progress,
            { nav.navigate(SignRoutes.detail(it)) { launchSingleTop = true } }, favorite)
    }
    composable(SignRoutes.detailPattern) { back ->
        val code = back.arguments?.getString("code")
        val sign = catalog.entries.find { it.code == code }
        when {
            catalog.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            sign == null -> Box(Modifier.fillMaxSize().padding(PremiumSpace.lg), contentAlignment = Alignment.Center) { Text(text(R.string.sign_detail_missing)) }
            else -> {
                LaunchedEffect(sign.code) { markViewed(sign.code) }
                SignDetailScreen(sign, settings.materialMode.translationTag, words,
                    settings.policy().canLookup, sign.code in progress.favorites,
                    { favorite(sign.code, it) }, saveWord, availableQuestionIds, openQuestion)
            }
        }
    }
}
