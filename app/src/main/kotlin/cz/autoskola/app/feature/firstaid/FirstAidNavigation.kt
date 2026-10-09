package cz.autoskola.app.feature.firstaid

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import cz.autoskola.app.R
import cz.autoskola.app.ui.text
import cz.autoskola.data.DictionaryLoadState
import cz.autoskola.domain.*
import cz.autoskola.design.*

object AidRoutes {
    const val catalog = "first_aid"
    const val detailPattern = "first_aid/{id}"
    const val questions = "aid_questions"
    const val questionPattern = "aid_question/{id}"
    fun detail(id: String): String { require(id.matches(Regex("C(?:0[1-9]|1[0-6])"))); return "first_aid/$id" }
    fun question(id: String): String { require(id.matches(Regex("RP[0-9]{7}"))); return "aid_question/$id" }
    fun titleResource(route: String): Int? = when(route) {
        catalog, detailPattern -> R.string.first_aid
        questions, questionPattern -> R.string.aid_official_questions
        else -> null
    }
}
data class AidDestinationState(val content: AidLoadState, val settings: UserSettings, val words: List<Lexeme>,
    val dictionaryState: DictionaryLoadState = DictionaryLoadState.READY)

fun NavGraphBuilder.firstAidDestinations(nav: NavHostController, state: State<AidDestinationState>,
    retry: () -> Unit, saveWord: (String) -> Unit, saveUnknownWord: (String) -> Unit,
    saveAidWord: (Lexeme) -> Unit) {
    @Composable fun ready(content: @Composable (AidBundle, AidDestinationState) -> Unit) {
        val current = state.value
        when {
            current.content.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            current.content.failed || current.content.bundle == null -> Column(Modifier.padding(PremiumSpace.lg)) {
                Text(text(R.string.aid_load_error)); PrimaryButton(text(R.string.retry), retry)
            }
            else -> content(requireNotNull(current.content.bundle), current)
        }
    }
    fun openCard(id: String) { nav.navigate(AidRoutes.detail(id)) { launchSingleTop = true } }
    fun nextCard(id: String) {
        nav.navigate(AidRoutes.detail(id)) { popUpTo(AidRoutes.detailPattern) { inclusive = true }; launchSingleTop = true }
    }
    fun openQuestion(id: String) { nav.navigate(AidRoutes.question(id)) { launchSingleTop = true } }
    fun save(bundle: AidBundle, id: String) {
        if(id.startsWith("aid-")) bundle.vocabulary.firstOrNull { it.id == id && it.locale == state.value.settings.policy().translationTag }?.let(saveAidWord)
        else saveWord(id)
    }
    composable(AidRoutes.catalog) { ready { bundle, current ->
        FirstAidCatalogScreen(bundle, current.settings.policy().translationTag, ::openCard) { nav.navigate(AidRoutes.questions) }
    } }
    composable(AidRoutes.detailPattern) { back -> ready { bundle, current ->
        val card = bundle.cards.find { it.id == back.arguments?.getString("id") }
        if(card == null) Text(text(R.string.aid_missing), Modifier.padding(PremiumSpace.lg))
        else FirstAidDetailScreen(card, bundle, current, ::openCard, ::nextCard, ::openQuestion,
            { save(bundle, it) }, saveUnknownWord)
    } }
    composable(AidRoutes.questions) { ready { bundle, current ->
        AidQuestionsScreen(bundle, current.settings.policy().translationTag, ::openQuestion)
    } }
    composable(AidRoutes.questionPattern) { back -> ready { bundle, current ->
        val question = bundle.questions.find { it.id == back.arguments?.getString("id") }
        if(question == null) Text(text(R.string.aid_missing), Modifier.padding(PremiumSpace.lg))
        else AidQuestionScreen(question, bundle, current, ::openCard, { save(bundle, it) }, saveUnknownWord)
    } }
}
