package cz.autoskola.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import cz.autoskola.app.R
import cz.autoskola.design.PremiumSpace
import cz.autoskola.domain.Lexeme
import java.util.Locale

/** Reuses imported dictionary forms. Never manufactures translations for unknown words. */
fun findLearningWord(token: String, words: List<Lexeme>, translationTag: String?): Lexeme? {
    if(translationTag == null) return null
    val normalized = token.lowercase(Locale.forLanguageTag("cs"))
    return words.firstOrNull { word ->
        word.locale == translationTag && !word.translation.isNullOrBlank() &&
            (word.lemma.lowercase(Locale.forLanguageTag("cs")) == normalized ||
                word.forms.any { it.lowercase(Locale.forLanguageTag("cs")) == normalized })
    }
}

@Composable fun CzechLearningText(value: String, translationTag: String?, words: List<Lexeme>,
    lookupEnabled: Boolean, onWord: (Lexeme) -> Unit, prominent: Boolean = false) {
    val enabled = lookupEnabled && words.any { findLearningWord(it.lemma, words, translationTag) != null }
    CzechText(value, enabled, { token -> findLearningWord(token, words, translationTag)?.let(onWord) }, prominent)
}

/** A compact popup on the current learning screen; outside tap closes it. */
@Composable fun LearningWordPopup(word: Lexeme?, save: (String) -> Unit, dismiss: () -> Unit) {
    if(word == null) return
    var requestedSave by rememberSaveable(word.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(word.lemma) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PremiumSpace.sm)) {
                word.translation?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                word.meaning?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                SpeechButtons(word.lemma)
            }
        },
        confirmButton = {
            TextButton(onClick = { save(word.id); requestedSave = true }, enabled = !word.saved && !requestedSave) {
                Text(text(if(word.saved || requestedSave) R.string.word_saved else R.string.save_word))
            }
        }, dismissButton = { TextButton(onClick = dismiss) { Text(text(R.string.close)) } })
}
