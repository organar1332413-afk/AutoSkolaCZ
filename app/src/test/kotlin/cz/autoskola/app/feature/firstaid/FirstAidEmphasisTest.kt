package cz.autoskola.app.feature.firstaid

import androidx.compose.ui.text.font.FontWeight
import cz.autoskola.app.ui.czechWordRanges
import org.junit.Assert.*
import org.junit.Test

class FirstAidEmphasisTest {
    @Test fun importantValuesAndShortCommandsAreBoldWithoutChangingTextOrWordOffsets() {
        listOf("Volej 155 nebo 112. Zapni AED. Přibližně 100/min, 4–5 cm, 6-8 vdechů/min a 5 min.",
            "Звони 155 или 112. Включи AED. Около 100/мин, 4–5 см и 5 мин.",
            "Телефонуй 155 або 112. Увімкни AED. Близько 100/хв, 4–5 см і 5 хв.").forEach { value ->
            val formatted = aidEmphasized(value)
            assertEquals(value, formatted.text)
            listOf("155", "112", "AED", "100", "4–5").forEach { token ->
                val start = value.indexOf(token)
                assertTrue(token, formatted.spanStyles.any { it.start <= start && it.end >= start + token.length && it.item.fontWeight == FontWeight.Bold })
            }
            assertEquals(czechWordRanges(value).map { it.range }, czechWordRanges(formatted.text).map { it.range })
            assertTrue(formatted.spanStyles.all { it.item.fontSize == androidx.compose.ui.unit.TextUnit.Unspecified && it.item.color == androidx.compose.ui.graphics.Color.Unspecified })
        }
        assertTrue(aidEmphasized("RP1102011 · A").spanStyles.isEmpty())
    }

    @Test fun everyNumericAlternativeUsesTheSameStyleIncludingIncorrectValues() {
        listOf("Přibližně 60 stlačení za minutu.", "Přibližně 100 stlačení za minutu.", "Přibližně 160 stlačení za minutu.",
            "4-5 cm", "stačí 1-2 cm", "8-10 cm", "30:2", "20 мин.", "10 хв.").forEach { value ->
            val text = aidEmphasized(value)
            assertTrue(text.spanStyles.isNotEmpty())
            assertTrue(text.spanStyles.all { it.item == androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold) })
            Regex("\\d").findAll(value).forEach { digit ->
                assertTrue(text.spanStyles.any { digit.range.first in it.start until it.end })
            }
        }
    }
}
