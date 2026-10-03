package cz.autoskola.app.feature.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.design.*
import cz.autoskola.domain.Lexeme

@Composable fun SignDetailScreen(sign: SignEntry, translationTag: String?, words: List<Lexeme>,
    lookupEnabled: Boolean, isFavorite: Boolean, favorite: (Boolean) -> Unit,
    saveWord: (String) -> Unit, availableQuestionIds: Set<String>, openQuestion: (String) -> Unit,
    saveUnknownWord: (String) -> Unit = {}, lookupTipSeen: Boolean = false, dismissLookupTip: () -> Unit = {}) {
    var sourceExpanded by rememberSaveable(sign.code) { mutableStateOf(false) }
    var moreExpanded by rememberSaveable(sign.code) { mutableStateOf(false) }
    var selection by remember(sign.code, translationTag, lookupEnabled) { mutableStateOf<LearningWordSelection?>(null) }
    var tipDismissedLocally by rememberSaveable { mutableStateOf(false) }
    val policy = WordTranslationPolicy(translationTag, lookupEnabled)
    var sourceError by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val helperTitle = when(translationTag) { "ru" -> sign.titleRu; "uk" -> sign.titleUk; else -> null }
    val memoryAdvice = listOfNotNull(sign.memoryCs, sign.mistakeCs)
        .firstOrNull { it != sign.driverActionsCs && it != sign.meaningCs }
    val more = listOfNotNull(sign.simpleCs, sign.memoryCs, sign.mistakeCs)
        .distinct().filter { it != sign.meaningCs && it != sign.driverActionsCs && it != memoryAdvice }
    fun hideTip() { tipDismissedLocally = true; dismissLookupTip() }
    fun onWord(word: LearningWordSelection) { selection = word; hideTip() }
    @Composable fun learningText(value: String, tag: String) {
        Column(verticalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
            CzechLearningText(value, policy, ::onWord, modifier = Modifier.testTag("$tag-cs"))
            if(translationTag != null) Text(sign.helperFor(value, translationTag)
                ?: text(R.string.sign_text_translation_unavailable), Modifier.testTag("$tag-helper"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("sign-detail-${sign.code}"),
        contentPadding = PaddingValues(PremiumSpace.lg), verticalArrangement = Arrangement.spacedBy(PremiumSpace.md)) {
        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(PremiumSpace.xl), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PremiumSpace.md)) {
                        sign.graphicPaths.forEach { path ->
                            SignGraphic(path, "${sign.code} · ${sign.titleCs}", Modifier.fillMaxWidth().height(PremiumSize.signHero))
                        }
                    }
                    IconToggleButton(checked = isFavorite, onCheckedChange = favorite,
                        modifier = Modifier.align(Alignment.TopEnd).size(PremiumSize.touch).testTag("detail-favorite")) {
                        Icon(if(isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            text(if(isFavorite) R.string.favorite_remove else R.string.favorite_add), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
                CzechLearningText("${sign.code} · ${sign.titleCs}", policy, ::onWord, prominent = true, modifier = Modifier.testTag("sign-title-cs"))
                helperTitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if(policy.allowsLookup && !lookupTipSeen && !tipDismissedLocally) item("word-tip") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("word-tip")) {
                Column(Modifier.weight(1f)) {
                    Text("Tip: Klepněte na české slovo pro překlad.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(translationTag == "uk") "Торкніться чеського слова для перекладу." else "Нажмите на чешское слово для перевода.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = ::hideTip, modifier = Modifier.size(PremiumSize.touch)) { Icon(Icons.Default.Close, text(R.string.close)) }
            }
        }
        sign.meaningCs?.let { meaning -> item {
            DetailSectionCard("Co znamená") {
                learningText(meaning, "meaning")
            }
        } }
        sign.driverActionsCs?.let { action -> item {
            DetailSectionCard("Co má řidič udělat") { learningText(action, "action") }
        } }
        memoryAdvice?.let { memory -> item {
            DetailSectionCard("Zapamatuj si") { learningText(memory, "memory") }
        } }
        if(more.isNotEmpty()) item {
            PremiumCard(Modifier.fillMaxWidth()) {
                TextButton(onClick = { moreExpanded = !moreExpanded }, modifier = Modifier.fillMaxWidth().heightIn(min = PremiumSize.touch)) {
                    Text(text(R.string.sign_more_information), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(if(moreExpanded) "−" else "+", style = MaterialTheme.typography.titleMedium)
                }
                if(moreExpanded) Column(Modifier.padding(PremiumSpace.lg), verticalArrangement = Arrangement.spacedBy(PremiumSpace.sm)) {
                    more.forEachIndexed { index, value -> learningText(value, "additional-$index") }
                }
            }
        }
        item {
            TextButton(onClick = { sourceExpanded = !sourceExpanded }, modifier = Modifier.heightIn(min = PremiumSize.touch).testTag("sign-source")) {
                Text("Oficiální zdroj", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(sourceExpanded) Column(verticalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
                sign.sourceProvision?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = { sourceError = runCatching { uriHandler.openUri(sign.sourceUrl) }.isFailure }) { Text(text(R.string.sign_source_open)) }
                if(sourceError) Text(text(R.string.sign_source_unavailable), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    LearningWordPopup(selection, policy, words, saveWord, saveUnknownWord) { selection = null }
}
