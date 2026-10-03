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
    saveWord: (String) -> Unit, availableQuestionIds: Set<String>, openQuestion: (String) -> Unit) {
    var sourceExpanded by rememberSaveable(sign.code) { mutableStateOf(false) }
    var moreExpanded by rememberSaveable(sign.code) { mutableStateOf(false) }
    var selectedWordId by rememberSaveable(sign.code, translationTag, lookupEnabled) { mutableStateOf<String?>(null) }
    var sourceError by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val helperTitle = when(translationTag) { "ru" -> sign.titleRu; "uk" -> sign.titleUk; else -> null }
    val meaningTranslation = when(translationTag) { "ru" -> sign.ru; "uk" -> sign.uk; else -> null }
    val linked = sign.relatedOfficialIds.filter { it in availableQuestionIds }
    val more = listOfNotNull(sign.simpleCs, sign.memoryCs, sign.mistakeCs)
        .distinct().filter { it != sign.meaningCs && it != sign.driverActionsCs && it != sign.memoryCs }
    fun onWord(word: Lexeme) { selectedWordId = word.id }

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
                CzechLearningText("${sign.code} · ${sign.titleCs}", translationTag, words, lookupEnabled, ::onWord, prominent = true)
                helperTitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if(lookupEnabled && words.any { it.locale == translationTag && !it.translation.isNullOrBlank() }) item {
            Text(text(R.string.sign_word_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        sign.meaningCs?.let { meaning -> item {
            DetailSectionCard("Co znamená") {
                CzechLearningText(meaning, translationTag, words, lookupEnabled, ::onWord)
                meaningTranslation?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } }
        sign.driverActionsCs?.let { action -> item {
            DetailSectionCard("Co má řidič udělat") { CzechLearningText(action, translationTag, words, lookupEnabled, ::onWord) }
        } }
        sign.memoryCs?.takeIf { it != sign.driverActionsCs && it != sign.meaningCs }?.let { memory -> item {
            DetailSectionCard("Zapamatuj si") { CzechLearningText(memory, translationTag, words, lookupEnabled, ::onWord) }
        } }
        if(more.isNotEmpty()) item {
            DetailSectionCard(text(R.string.sign_more_information)) {
                TextButton(onClick = { moreExpanded = !moreExpanded }) { Text(text(if(moreExpanded) R.string.close else R.string.sign_more_information)) }
                if(moreExpanded) more.forEach { CzechLearningText(it, translationTag, words, lookupEnabled, ::onWord) }
            }
        }
        if(linked.isNotEmpty()) item {
            DetailSectionCard(text(R.string.signs_related_official_questions)) {
                linked.forEach { id -> TextButton(onClick = { openQuestion(id) }) { Text(id) } }
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
    LearningWordPopup(words.find { it.id == selectedWordId }?.takeIf { lookupEnabled && it.locale == translationTag }, saveWord) { selectedWordId = null }
}
