package cz.autoskola.app.ui
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString

/** Tokenization never rewrites CS content. */
@Suppress("DEPRECATION")
@Composable fun CzechText(value: String, lookupEnabled: Boolean, onWord: (String) -> Unit) {
    CzechText(value, lookupEnabled, onWord, prominent = false)
}

@Suppress("DEPRECATION")
@Composable fun CzechText(value: String, lookupEnabled: Boolean, onWord: (String) -> Unit, prominent: Boolean) {
    val base = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
    val style = base.copy(color = MaterialTheme.colorScheme.onSurface)
    if (!lookupEnabled) Text(value, style = style)
    else ClickableText(text = AnnotatedString(value), style = style, onClick = { offset ->
        Regex("[\\p{L}]+(?:[-’'][\\p{L}]+)*").findAll(value).firstOrNull { offset in it.range }?.let { onWord(it.value) }
    })
}
