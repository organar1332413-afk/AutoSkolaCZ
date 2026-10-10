package cz.autoskola.app.feature.firstaid

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

// The same rules apply to every alternative, independent of the answer key.
// Preserve text, offsets, colours and font sizes. Do not highlight RP IDs.
private val aidNumbers = Regex("(?<![\\p{L}\\p{N}])\\d+(?:\\s*[–−:-]\\s*\\d+)*(?![\\p{L}\\p{N}])")
private val aidCommands = Regex(
    "(?<![\\p{L}\\p{M}])(?:AED|KPR|СЛР|" +
        "Volej|Pomoz|Oslov|Zahaj|Zapni|Nevytahuj|Nepovoluj|Nedávej pít|Zastav|odstup|" +
        "Звони|Вызови|Помоги|Обратись|Начни|Включи|Не вытаскивай|Не ослабляй|Не давай пить|Останови|Отойди|" +
        "Телефонуй|Виклич|Допоможи|Звернися|Почни|Увімкни|Не витягай|Не послаблюй|Не давай пити|Зупини|Відійди" +
        ")(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)

internal fun aidEmphasized(value: String): AnnotatedString = buildAnnotatedString {
    append(value)
    (aidNumbers.findAll(value) + aidCommands.findAll(value)).forEach {
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1)
    }
}
