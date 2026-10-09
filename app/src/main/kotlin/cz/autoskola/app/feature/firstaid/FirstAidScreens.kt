package cz.autoskola.app.feature.firstaid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.design.*
import cz.autoskola.domain.*

@Composable private fun AidLearning(value: AidText, tag: String?, policy: WordTranslationPolicy,
    onWord: (LearningWordSelection) -> Unit, modifier: Modifier = Modifier, prominent: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PremiumSpace.xxs)) {
        CzechLearningText(value.cs, policy, onWord, prominent, Modifier.testTag("aid-learning-cs"))
        value.helper(tag)?.let { Text(it, Modifier.testTag("aid-learning-helper"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun AidReviewNote() {
    Text(text(R.string.aid_review_pending), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("aid-review-pending"))
}

@Composable fun FirstAidCatalogScreen(bundle: AidBundle, translationTag: String?, open: (String) -> Unit, questions: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    val grid = rememberLazyGridState()
    val filtered = remember(bundle, query, category, translationTag) { filterAid(bundle.cards, query, category, translationTag) }
    Column(Modifier.fillMaxSize().testTag("aid-catalog")) {
        Column(Modifier.padding(horizontal = PremiumSpace.lg), verticalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text(R.string.first_aid), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                Text("${bundle.cards.size} · ${bundle.questions.size} RP", style = MaterialTheme.typography.labelMedium)
            }
            SearchField(query, { query = it }, text(R.string.aid_search), Modifier.testTag("aid-search"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
                listOf("all" to R.string.aid_all, "resuscitation" to R.string.aid_resuscitation, "trauma" to R.string.aid_trauma).forEach { (id, label) ->
                    item(id) { CategoryChip(category == id, { category = id }, text(label), Modifier.testTag("aid-category-$id")) }
                }
            }
            AidReviewNote()
            TextButton(questions, Modifier.heightIn(min = PremiumSize.touch).testTag("aid-all-questions")) { Text(text(R.string.aid_official_questions)) }
        }
        if(filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text(R.string.aid_empty)) }
        else LazyVerticalGrid(GridCells.Adaptive(156.dp), state = grid,
            modifier = Modifier.weight(1f).testTag("aid-grid"), contentPadding = PaddingValues(PremiumSpace.lg),
            horizontalArrangement = Arrangement.spacedBy(PremiumSpace.sm), verticalArrangement = Arrangement.spacedBy(PremiumSpace.sm)) {
            items(filtered, key = { it.id }) { card ->
                PremiumCard(Modifier.fillMaxWidth().testTag("aid-${card.id}"), onClick = { open(card.id) }) {
                    AidGraphic(card, Modifier.fillMaxWidth().height(112.dp))
                    Column(Modifier.padding(PremiumSpace.sm), verticalArrangement = Arrangement.spacedBy(PremiumSpace.xxs)) {
                        Text(card.id, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(card.title.cs, style = MaterialTheme.typography.titleSmall)
                        card.title.helper(translationTag)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable private fun AidGraphic(card: AidCard, modifier: Modifier = Modifier) {
    var failed by remember(card.image) { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    Box(modifier) {
        AsyncImage("file:///android_asset/${card.image}", card.title.language(configuration.locales[0].language),
            Modifier.fillMaxSize().testTag("aid-image-${card.id}"), contentScale = ContentScale.Fit,
            onError = { failed = true })
        if(failed) Text(text(R.string.aid_image_error), Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun AidSection(title: String, exam: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminanceValue() < 0.5f
    val tint = if(exam) (if(dark) Color(0xFF33262C) else Color(0xFFFFF4F6))
        else (if(dark) Color(0xFF202F3D) else Color(0xFFEAF4FF))
    Card(Modifier.fillMaxWidth(), shape = PremiumShapes.card,
        colors = CardDefaults.cardColors(containerColor = tint),
        border = BorderStroke(PremiumSize.border, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(PremiumElevation.card)) {
        Column(Modifier.padding(PremiumSpace.md), verticalArrangement = Arrangement.spacedBy(PremiumSpace.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
                Icon(if(exam) Icons.Default.CheckCircle else Icons.Default.Favorite, null,
                    tint = if(exam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
private fun Color.luminanceValue(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable fun FirstAidDetailScreen(card: AidCard, bundle: AidBundle, current: AidDestinationState,
    openCard: (String) -> Unit, nextCard: (String) -> Unit, openQuestion: (String) -> Unit,
    saveWord: (String) -> Unit, saveUnknownWord: (String) -> Unit) {
    val tag = current.settings.policy().translationTag
    val policy = WordTranslationPolicy(tag, current.settings.policy().canLookup)
    var selection by remember(card.id, tag, policy.enabled) { mutableStateOf<LearningWordSelection?>(null) }
    var clinicalExpanded by rememberSaveable(card.id) { mutableStateOf(false) }
    var answersExpanded by rememberSaveable(card.id) { mutableStateOf(false) }
    val words = current.words + bundle.vocabulary.filter { it.locale == tag }
    val index = bundle.cards.indexOfFirst { it.id == card.id }
    val next = bundle.cards.getOrNull(index + 1)
    val select: (LearningWordSelection) -> Unit = { selection = it }
    LazyColumn(Modifier.fillMaxSize().testTag("aid-detail-${card.id}"),
        contentPadding = PaddingValues(PremiumSpace.lg), verticalArrangement = Arrangement.spacedBy(PremiumSpace.md)) {
        item("title") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(card.id, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text("${index + 1} / ${bundle.cards.size}", style = MaterialTheme.typography.labelMedium)
            }
            AidLearning(card.title, tag, policy, select, prominent = true)
        }
        item("hero") {
            Box(Modifier.fillMaxWidth().heightIn(min = 208.dp)) {
                AidGraphic(card, Modifier.fillMaxWidth().height(232.dp))
                Column(Modifier.align(Alignment.CenterEnd).widthIn(max = 104.dp), verticalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
                    card.badges.forEach { badge ->
                        Surface(shape = PremiumShapes.chip, color = MaterialTheme.colorScheme.surface, tonalElevation = PremiumElevation.card) {
                            Column(Modifier.padding(PremiumSpace.xs)) {
                                Text(badge.cs, style = MaterialTheme.typography.labelSmall)
                                badge.helper(tag)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
        item("exam") {
            AidSection(text(R.string.aid_exam_block), exam = true) {
                Text(text(R.string.aid_exam_version), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AidLearning(card.examSummary, tag, policy, select)
                if(card.questionIds.isNotEmpty()) {
                    TextButton({ answersExpanded = !answersExpanded }, Modifier.heightIn(min = PremiumSize.touch).testTag("aid-answers-toggle")) {
                        Text(text(if(answersExpanded) R.string.aid_hide_answers else R.string.aid_exact_answers))
                    }
                    if(answersExpanded) card.questionIds.forEach { id ->
                        val q = bundle.questions.single { it.id == id }
                        Text("$id · ${q.correct.label}", style = MaterialTheme.typography.labelMedium)
                        AidLearning(q.answer, tag, policy, select)
                        TextButton({ openQuestion(id) }, Modifier.heightIn(min = PremiumSize.touch).testTag("aid-question-$id")) { Text(text(R.string.aid_open_question)) }
                    }
                }
            }
        }
        item("clinical") {
            AidSection(text(R.string.aid_clinical_block), exam = false) {
                AidLearning(card.summary, tag, policy, select)
                TextButton({ clinicalExpanded = !clinicalExpanded }, Modifier.heightIn(min = PremiumSize.touch).testTag("aid-clinical-toggle")) {
                    Text(text(if(clinicalExpanded) R.string.aid_less else R.string.aid_more))
                }
                if(clinicalExpanded) AidLearning(card.clinical, tag, policy, select, Modifier.testTag("aid-clinical-full"))
                AidReviewNote()
            }
        }
        if(card.relatedCards.isNotEmpty()) item("related") {
            card.relatedCards.forEach { id ->
                val linked = bundle.cards.single { it.id == id }
                TextButton({ openCard(id) }, Modifier.fillMaxWidth().heightIn(min = PremiumSize.touch).testTag("aid-related-$id")) {
                    Text("$id · ${linked.title.cs}", Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
        if(next != null) item("next") {
            Surface(shape = PremiumShapes.card, color = MaterialTheme.colorScheme.primaryContainer) {
                TextButton({ nextCard(next.id) }, Modifier.fillMaxWidth().heightIn(min = PremiumSize.touch)) {
                    Icon(Icons.Default.Favorite, null); Text("${next.id} · ${next.title.cs}", Modifier.weight(1f).padding(start = PremiumSpace.xs))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
            PrimaryButton(text(R.string.aid_next), { nextCard(next.id) }, Modifier.fillMaxWidth().testTag("aid-next"))
        }
        item("sources") { AidSources(card, bundle) }
    }
    LearningWordPopup(selection, policy, words, saveWord, saveUnknownWord, current.dictionaryState) { selection = null }
}

@Composable private fun AidSources(card: AidCard, bundle: AidBundle) {
    var expanded by rememberSaveable(card.id) { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    TextButton({ expanded = !expanded }, Modifier.heightIn(min = PremiumSize.touch).testTag("aid-source-toggle")) { Text(text(R.string.aid_sources)) }
    if(expanded) Column {
        Text(text(R.string.aid_translation_draft), style = MaterialTheme.typography.bodySmall)
        (listOf("md-bulletin") + card.sourceIds).distinct().forEach { id ->
            bundle.sources.find { it.id == id }?.let { s ->
                TextButton({ failed = runCatching { uri.openUri(s.url) }.isFailure }, Modifier.heightIn(min = PremiumSize.touch)) { Text(s.title) }
            }
        }
        if(failed) Text(text(R.string.aid_source_error), color = MaterialTheme.colorScheme.error)
    }
}

@Composable fun AidQuestionsScreen(bundle: AidBundle, tag: String?, open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = bundle.questions.filter { q -> query.isBlank() || (q.id + q.question.cs + q.question.helper(tag).orEmpty()).contains(query.trim(), ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize().testTag("aid-questions"), contentPadding = PaddingValues(PremiumSpace.lg), verticalArrangement = Arrangement.spacedBy(PremiumSpace.sm)) {
        item { SearchField(query, { query = it }, text(R.string.aid_search), Modifier.testTag("aid-question-search")) }
        item { Text(text(R.string.aid_exam_version)); Text("${shown.size} / 35 RP", style = MaterialTheme.typography.labelMedium) }
        if(shown.isEmpty()) item { Text(text(R.string.aid_empty)) }
        items(shown, key = { it.id }) { q ->
            PremiumCard(Modifier.fillMaxWidth().testTag("aid-rp-${q.id}"), onClick = { open(q.id) }) {
                Column(Modifier.padding(PremiumSpace.md), verticalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
                    Text(q.id, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(q.question.cs, style = MaterialTheme.typography.bodyLarge)
                    q.question.helper(tag)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable fun AidQuestionScreen(q: AidQuestion, bundle: AidBundle, current: AidDestinationState,
    openCard: (String) -> Unit, save: (String) -> Unit, saveUnknown: (String) -> Unit) {
    val tag = current.settings.policy().translationTag
    val policy = WordTranslationPolicy(tag, current.settings.policy().canLookup)
    var selection by remember(q.id, tag) { mutableStateOf<LearningWordSelection?>(null) }
    var expanded by rememberSaveable(q.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("aid-question-detail-${q.id}"), contentPadding = PaddingValues(PremiumSpace.lg), verticalArrangement = Arrangement.spacedBy(PremiumSpace.md)) {
        item { Text(q.id, color = MaterialTheme.colorScheme.primary); AidLearning(q.question, tag, policy, { selection = it }, prominent = true) }
        item {
            AidSection(text(R.string.aid_exam_block), exam = true) {
                Text(text(R.string.aid_exam_version), style = MaterialTheme.typography.labelSmall)
                Text("✓ ${q.correct.label}", style = MaterialTheme.typography.titleMedium)
                AidLearning(q.answer, tag, policy, { selection = it })
            }
        }
        item {
            TextButton({ expanded = !expanded }, Modifier.heightIn(min = PremiumSize.touch).testTag("aid-variants-toggle")) { Text(text(R.string.aid_official_variants)) }
            if(expanded) q.options.forEach { option ->
                Text("${if(option.correct) "✓" else ""} ${option.label}", style = MaterialTheme.typography.labelMedium)
                CzechLearningText(option.cs, policy, { selection = it })
            }
        }
        item { Text(text(R.string.aid_related_material), style = MaterialTheme.typography.titleMedium) }
        items(bundle.cardsFor(q.id), key = { it.id }) { c ->
            PremiumCard(Modifier.fillMaxWidth().testTag("aid-question-card-${c.id}"), onClick = { openCard(c.id) }) {
                Column(Modifier.padding(PremiumSpace.md)) { Text("${c.id} · ${c.title.cs}"); c.title.helper(tag)?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
            }
        }
        item { AidReviewNote() }
    }
    val words = current.words + bundle.vocabulary.filter { it.locale == tag }
    LearningWordPopup(selection, policy, words, save, saveUnknown, current.dictionaryState) { selection = null }
}
