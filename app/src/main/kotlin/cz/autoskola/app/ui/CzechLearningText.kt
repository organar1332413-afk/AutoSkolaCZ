package cz.autoskola.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import cz.autoskola.app.R
import cz.autoskola.design.*
import cz.autoskola.domain.Lexeme
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

private val czechWords = Regex("[\\p{L}\\p{M}]+(?:[-’'][\\p{L}\\p{M}]+)*")
fun czechWordRanges(value: String): List<MatchResult> = czechWords.findAll(value).toList()
fun normalizeLearningWord(token: String): String = Normalizer.normalize(
    czechWords.find(token)?.value.orEmpty(), Normalizer.Form.NFC).lowercase(Locale.forLanguageTag("cs"))

/** Existing Room dictionary, including imported inflected forms; no runtime translation. */
fun findLearningWord(token: String, words: List<Lexeme>, translationTag: String?): Lexeme? {
    if(translationTag == null) return null
    val normalized = normalizeLearningWord(token)
    if(normalized.isEmpty()) return null
    return words.firstOrNull { word -> word.locale == translationTag &&
        (normalizeLearningWord(word.lemma) == normalized || word.forms.any { normalizeLearningWord(it) == normalized }) }
        ?.takeIf { !it.translation.isNullOrBlank() }
}
fun findSavedLearningWord(token: String, words: List<Lexeme>, translationTag: String?): Lexeme? =
    words.firstOrNull { it.locale == translationTag && it.saved &&
        (normalizeLearningWord(it.lemma) == normalizeLearningWord(token) || it.forms.any { form -> normalizeLearningWord(form) == normalizeLearningWord(token) }) }

/** Callers must pass their learning/exam policy explicitly. CZ-only and strict exam deny lookup. */
data class WordTranslationPolicy(val translationTag: String?, val enabled: Boolean) {
    val allowsLookup get() = enabled && translationTag in setOf("ru", "uk")
    companion object { val StrictExam = WordTranslationPolicy(null, false) }
}
data class LearningWordSelection(val token: String, val boundsInWindow: IntRect)

/** Original text is unchanged. Hit testing uses the laid-out character under the finger. */
@Suppress("DEPRECATION")
@Composable fun CzechLearningText(value: String, policy: WordTranslationPolicy,
    onWord: (LearningWordSelection) -> Unit, prominent: Boolean = false, modifier: Modifier = Modifier) {
    val style = (if(prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
        .copy(color = MaterialTheme.colorScheme.onSurface)
    if(!policy.allowsLookup) { Text(value, modifier, style = style); return }
    var layout by remember(value) { mutableStateOf<TextLayoutResult?>(null) }
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val tokens = remember(value) { czechWordRanges(value) }
    fun select(token: MatchResult, offset: Int = token.range.first) {
        val result = layout ?: return
        val box: Rect = result.getBoundingBox(offset)
        val position = box.translate(origin)
        onWord(LearningWordSelection(token.value, IntRect(position.left.roundToInt(), position.top.roundToInt(),
            position.right.roundToInt(), position.bottom.roundToInt())))
    }
    ClickableText(AnnotatedString(value), modifier.onGloballyPositioned { origin = it.positionInWindow() }
        .semantics { customActions = tokens.distinctBy { it.value }.map { token ->
            CustomAccessibilityAction("Překlad: ${token.value}") { select(token); true }
        } }, style = style, onTextLayout = { layout = it }, onClick = { offset ->
        tokens.firstOrNull { offset in it.range }?.let { select(it, offset) }
    })
}

/** Below the tapped character, or above it when the bottom of the window is close. */
internal class LearningWordPopupPosition(private val word: IntRect, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (word.left + word.width / 2 - popupContentSize.width / 2)
            .coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
        val below = word.bottom + gap
        val y = if(below + popupContentSize.height <= windowSize.height - gap) below else word.top - popupContentSize.height - gap
        return IntOffset(x, y.coerceIn(gap, (windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap)))
    }
}

/** Focusable lightweight surface: outside tap and Back dismiss without navigating or scrolling. */
@Composable fun LearningWordPopup(selection: LearningWordSelection?, policy: WordTranslationPolicy,
    words: List<Lexeme>, save: (String) -> Unit, saveUnknown: (String) -> Unit, dismiss: () -> Unit) {
    if(selection == null || !policy.allowsLookup) return
    val word = findLearningWord(selection.token, words, policy.translationTag)
    val saved = word?.saved == true || findSavedLearningWord(selection.token, words, policy.translationTag) != null
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = with(density) { PremiumSpace.xs.roundToPx() }
    val position = remember(selection.boundsInWindow, gap) { LearningWordPopupPosition(selection.boundsInWindow, gap) }
    val speech = LocalCzechSpeech.current
    val unknown = if(policy.translationTag == "uk") "Переклад поки недоступний" else "Перевод пока недоступен"
    BackHandler { dismiss() }
    Popup(popupPositionProvider = position, onDismissRequest = dismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)) {
        Surface(modifier = Modifier.width(minOf(PremiumSize.wordPopupMaxWidth,
            LocalConfiguration.current.screenWidthDp.dp - PremiumSpace.lg * 2)).testTag("learning-word-popup"),
            shape = PremiumShapes.card, color = LocalPremiumPalette.current.elevatedSurface,
            shadowElevation = PremiumElevation.hero) {
            Column(Modifier.padding(PremiumSpace.md), verticalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(selection.token, Modifier.weight(1f).testTag("translation-token"), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = dismiss, modifier = Modifier.size(PremiumSize.touch).testTag("translation-close")) {
                        Icon(Icons.Default.Close, text(R.string.close))
                    }
                }
                Text(if(policy.translationTag == "uk") "UA" else "RU", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if(word != null) {
                    Text(requireNotNull(word.translation), Modifier.testTag("word-translation"), style = MaterialTheme.typography.bodyMedium)
                    word.meaning?.takeIf { it.isNotBlank() && it != word.translation }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("Překlad zatím není k dispozici", style = MaterialTheme.typography.bodyMedium)
                    Text(unknown, Modifier.testTag("word-translation-unavailable"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if(speech != null) TextButton(onClick = { speech.speak(selection.token, false) }, enabled = speech.ready,
                        modifier = Modifier.heightIn(min = PremiumSize.touch)) { Text(text(R.string.speak)) }
                    TextButton(onClick = { if(word != null) save(word.id) else saveUnknown(normalizeLearningWord(selection.token)) },
                        enabled = !saved, modifier = Modifier.heightIn(min = PremiumSize.touch).testTag("translation-save")) {
                        Text(if(saved) "✓ ${text(R.string.word_saved)}" else "＋ ${text(R.string.save_word)}")
                    }
                }
                if(speech?.failed == true) Text(text(R.string.tts_unavailable), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
