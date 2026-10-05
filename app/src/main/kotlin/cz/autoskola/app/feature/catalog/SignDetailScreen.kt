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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.design.*
import cz.autoskola.domain.Lexeme
import cz.autoskola.data.DictionaryLoadState

@Composable fun SignDetailScreen(sign: SignEntry, translationTag: String?, words: List<Lexeme>,
    lookupEnabled: Boolean, isFavorite: Boolean, favorite: (Boolean) -> Unit,
    saveWord: (String) -> Unit, availableQuestionIds: Set<String>, openQuestion: (String) -> Unit,
    saveUnknownWord: (String) -> Unit = {}, lookupTipSeen: Boolean = false, dismissLookupTip: () -> Unit = {},
    dictionaryState: DictionaryLoadState = DictionaryLoadState.READY) {
    var sourceExpanded by rememberSaveable(sign.code) { mutableStateOf(false) }
    var moreExpanded by rememberSaveable(sign.code) { mutableStateOf(false) }
    var selection by remember(sign.code, translationTag, lookupEnabled) { mutableStateOf<LearningWordSelection?>(null) }
    var tipDismissedLocally by rememberSaveable { mutableStateOf(false) }
    val policy = WordTranslationPolicy(translationTag, lookupEnabled)
    var sourceError by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val helperTitle = when(translationTag) { "ru" -> sign.titleRu; "uk" -> sign.titleUk; else -> null }
    val memoryAdvice = sign.memoryAdvice
    val more = sign.additionalLearningTexts
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
        item("hero") {
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
        item("title") {
            Column(verticalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
                CzechLearningText("${sign.code} · ${sign.titleCs}", policy, ::onWord, prominent = true, modifier = Modifier.testTag("sign-title-cs"))
                helperTitle?.let { Text(it, Modifier.testTag("sign-title-helper"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        sign.meaningCs?.let { meaning -> item("meaning") {
            DetailSectionCard(text(R.string.sign_meaning_title)) {
                learningText(meaning, "meaning")
            }
        } }
        sign.driverActionsCs?.let { action -> item("action") {
            DetailSectionCard(text(R.string.sign_action_title)) { learningText(action, "action") }
        } }
        memoryAdvice?.let { memory -> item("memory") {
            DetailSectionCard(text(R.string.sign_memory_title)) { learningText(memory, "memory") }
        } }
        if(more.isNotEmpty()) item("additional") {
            val expandedLabel = text(if(moreExpanded) R.string.sign_section_expanded else R.string.sign_section_collapsed)
            PremiumCard(Modifier.fillMaxWidth()) {
                TextButton(onClick = { moreExpanded = !moreExpanded }, modifier = Modifier.fillMaxWidth().heightIn(min = PremiumSize.touch)
                    .testTag("sign-additional").semantics { stateDescription = expandedLabel }) {
                    Text(text(R.string.sign_more_information), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(if(moreExpanded) "−" else "+", style = MaterialTheme.typography.titleMedium)
                }
                if(moreExpanded) Column(Modifier.padding(PremiumSpace.lg), verticalArrangement = Arrangement.spacedBy(PremiumSpace.sm)) {
                    more.forEachIndexed { index, value -> learningText(value, "additional-$index") }
                }
            }
        }
        item("source") {
            val expandedLabel = text(if(sourceExpanded) R.string.sign_section_expanded else R.string.sign_section_collapsed)
            TextButton(onClick = { sourceExpanded = !sourceExpanded }, modifier = Modifier.heightIn(min = PremiumSize.touch).testTag("sign-source")
                .semantics { stateDescription = expandedLabel }) {
                Text(text(R.string.sign_source_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(sourceExpanded) Column(verticalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
                sign.sourceProvision?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = { sourceError = runCatching { uriHandler.openUri(sign.sourceUrl) }.isFailure }, modifier = Modifier.testTag("sign-source-open")) { Text(text(R.string.sign_source_open)) }
                if(sourceError) Text(text(R.string.sign_source_unavailable), Modifier.testTag("sign-source-error"), color = MaterialTheme.colorScheme.error)
            }
        }
        if(policy.allowsLookup && !lookupTipSeen && !tipDismissedLocally) item("word-tip") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("word-tip")) {
                Column(Modifier.weight(1f)) {
                    Text(text(R.string.sign_word_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = ::hideTip, modifier = Modifier.size(PremiumSize.touch)) { Icon(Icons.Default.Close, text(R.string.close)) }
            }
        }
    }
    LearningWordPopup(selection, policy, words, saveWord, saveUnknownWord, dictionaryState) { selection = null }
}
