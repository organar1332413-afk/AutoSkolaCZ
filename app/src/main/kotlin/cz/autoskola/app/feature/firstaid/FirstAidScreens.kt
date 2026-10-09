package cz.autoskola.app.feature.firstaid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
    onWord: (LearningWordSelection) -> Unit, modifier: Modifier = Modifier, prominent: Boolean = false, compact: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PremiumSpace.xxs)) {
        CzechLearningText(value.cs, policy, onWord, prominent, Modifier.testTag("aid-learning-cs"), lookupPhrases = aidLookupPhrases,
            textStyle = if(compact) MaterialTheme.typography.titleSmall else if(prominent) MaterialTheme.typography.headlineSmall else null)
        value.helper(tag)?.let { Text(it, Modifier.testTag("aid-learning-helper"),
            style = if(compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    val words = bundle.vocabulary.filter { it.locale == tag } + current.words
    val index = bundle.cards.indexOfFirst { it.id == card.id }
    val next = bundle.cards.getOrNull(index + 1)
    val select: (LearningWordSelection) -> Unit = { selection = it }
    LazyColumn(Modifier.fillMaxSize().testTag("aid-detail-${card.id}"), state = rememberLazyListState(),
        contentPadding = PaddingValues(PremiumSpace.lg), verticalArrangement = Arrangement.spacedBy(PremiumSpace.md)) {
        item("title") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(card.id, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text("${index + 1} / ${bundle.cards.size}", style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(progress = { (index + 1f) / bundle.cards.size },
                modifier = Modifier.fillMaxWidth().padding(vertical = PremiumSpace.xs).testTag("aid-progress"))
            AidLearning(card.title, tag, policy, select, prominent = true)
        }
        item("hero") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
                AidGraphic(card, Modifier.weight(1f).height(260.dp))
                Column(Modifier.width(116.dp).testTag("aid-badges"), verticalArrangement = Arrangement.spacedBy(PremiumSpace.sm)) {
                    card.badges.forEachIndexed { badgeIndex, badge ->
                        Surface(Modifier.fillMaxWidth().testTag("aid-badge-$badgeIndex"),
                            shape = PremiumShapes.card, color = MaterialTheme.colorScheme.surface,
                            shadowElevation = PremiumElevation.card) {
                            Column(Modifier.padding(PremiumSpace.sm), verticalArrangement = Arrangement.spacedBy(PremiumSpace.xxs)) {
                                Icon(aidBadgeIcon(card.id, badgeIndex), null, Modifier.size(24.dp),
                                    tint = listOf(Color(0xFFDB3948), Color(0xFF2785D6), Color(0xFF238959))[badgeIndex])
                                AidLearning(badge, tag, policy, select, compact = true)
                            }
                        }
                    }
                }
            }
        }
        item("exam") {
            AidSection(text(R.string.aid_exam_block), exam = true) {
                AidLearning(card.examSummary, tag, policy, select)
                Text(text(R.string.aid_exam_version), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item("question-heading") {
            Text(text(R.string.aid_linked_questions) + " · ${card.questionIds.size}", style = MaterialTheme.typography.titleMedium)
            if(card.questionIds.isEmpty()) Text(text(R.string.aid_no_direct_questions), style = MaterialTheme.typography.bodySmall)
        }
        items(card.questionIds, key = { "question-$it" }) { id ->
            AidQuestionTile(bundle.questions.single { it.id == id }, tag) { openQuestion(id) }
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
        item("sources") { AidReviewNote(); AidSources(card, bundle) }
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
    var selectedLabel by rememberSaveable(q.id) { mutableStateOf<String?>(null) }
    var checked by rememberSaveable(q.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("aid-question-detail-${q.id}"), state = rememberLazyListState(),
        contentPadding = PaddingValues(PremiumSpace.lg), verticalArrangement = Arrangement.spacedBy(PremiumSpace.md)) {
        item { Text(q.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AidLearning(q.question, tag, policy, { selection = it }, prominent = true) }
        items(q.options, key = { it.label }) { option ->
            val correct = checked && option.correct
            val wrong = checked && selectedLabel == option.label && !option.correct
            Card(Modifier.fillMaxWidth().testTag("aid-option-${option.label}"), shape = PremiumShapes.card,
                colors = CardDefaults.cardColors(containerColor = when {
                    correct -> MaterialTheme.colorScheme.secondaryContainer
                    wrong -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface
                }), border = BorderStroke(PremiumSize.border, if(selectedLabel == option.label)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.padding(PremiumSpace.sm), verticalAlignment = Alignment.Top) {
                    // A separate 48dp control keeps choosing an answer independent of word lookup.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(selected = selectedLabel == option.label,
                            onClick = { selectedLabel = option.label }, enabled = !checked,
                            modifier = Modifier.size(PremiumSize.touch).testTag("aid-select-${option.label}"))
                        Text(option.label, style = MaterialTheme.typography.labelLarge)
                    }
                    Column(Modifier.weight(1f).padding(top = PremiumSpace.xs)) {
                        CzechLearningText(option.cs, policy, { selection = it }, modifier = Modifier.testTag("aid-option-text-${option.label}"), lookupPhrases = aidLookupPhrases)
                        if(correct) Text(text(R.string.aid_correct_answer), Modifier.testTag("aid-correct-${option.label}"),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            Button(onClick = { checked = true }, enabled = selectedLabel != null && !checked,
                modifier = Modifier.fillMaxWidth().heightIn(min = PremiumSize.touch).testTag("aid-check")) {
                Text(text(R.string.aid_check))
            }
            if(checked) {
                Text(text(if(selectedLabel == q.correct.label) R.string.aid_result_correct else R.string.aid_result_wrong),
                    Modifier.testTag("aid-result"), style = MaterialTheme.typography.titleMedium)
                TextButton({ selectedLabel = null; checked = false }, Modifier.testTag("aid-retry")) { Text(text(R.string.aid_try_again)) }
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
    val words = bundle.vocabulary.filter { it.locale == tag } + current.words
    LearningWordPopup(selection, policy, words, save, saveUnknown, current.dictionaryState) { selection = null }
}

@Composable private fun AidQuestionTile(q: AidQuestion, tag: String?, open: () -> Unit) {
    PremiumCard(Modifier.fillMaxWidth().testTag("aid-question-${q.id}"), onClick = open) {
        Column(Modifier.padding(PremiumSpace.md), verticalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
            Text(q.question.cs, style = MaterialTheme.typography.bodyLarge, maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            q.question.helper(tag)?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            Text(q.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun aidBadgeIcon(card: String, index: Int): androidx.compose.ui.graphics.vector.ImageVector = when(card) {
    "C05" -> listOf(Icons.Default.Favorite, AidHintIcons.Depth, AidHintIcons.Breath)[index]
    "C02" -> listOf(Icons.Default.Phone, AidHintIcons.Emergency, Icons.Default.LocationOn)[index]
    "C06" -> listOf(AidHintIcons.Power, Icons.Default.Favorite, AidHintIcons.Bolt)[index]
    "C08", "C09", "C13" -> listOf(AidHintIcons.Hand, AidHintIcons.Shield, Icons.Default.Phone)[index]
    "C16" -> listOf(AidHintIcons.Clock, Icons.Default.Favorite, Icons.Default.Phone)[index]
    else -> listOf(AidHintIcons.Shield, AidHintIcons.Eye, Icons.Default.Phone)[index]
}

internal val aidLookupPhrases = listOf("odnětí svobody", "řídit se", "řiď se", "hlasitý odposlech", "s hlasitým odposlechem", "první pomoc", "první pomoci", "dýchací cesty", "dýchacích cest", "lapavé dechy")

// Small native vectors avoid the large material-icons-extended dependency.
private object AidHintIcons {
    private fun vector(name: String, data: String) = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(data).toNodes(),
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round)
    }.build()
    val Depth = vector("Depth", "M12 3V21M8 7L12 3L16 7M8 17L12 21L16 17")
    val Breath = vector("Breath", "M3 8H15C20 8 20 3 16 3M3 12H19M3 16H14C19 16 19 21 15 21")
    val Emergency = vector("Emergency", "M12 3L22 21H2ZM12 9V14M12 17V18")
    val Power = vector("Power", "M12 2V12M7 5C-1 10 3 22 12 22C21 22 25 10 17 5")
    val Bolt = vector("Bolt", "M13 2L4 14H11L10 22L20 10H13Z")
    val Shield = vector("Shield", "M12 2L21 6V12C21 17 16 21 12 23C8 21 3 17 3 12V6ZM12 7V17M7 12H17")
    val Clock = vector("Clock", "M12 3A9 9 0 1 1 12 21A9 9 0 1 1 12 3M12 7V12L16 15")
    val Eye = vector("Eye", "M2 12C7 3 17 3 22 12C17 21 7 21 2 12ZM12 9A3 3 0 1 1 12 15A3 3 0 1 1 12 9")
    val Hand = vector("Hand", "M7 12V5Q7 3 9 5V11V3Q11 1 12 3V11V4Q14 2 15 4V12V7Q18 5 18 7V15Q18 22 12 22Q8 22 6 18L2 12Q3 9 5 12L7 14")
}
