package cz.autoskola.design
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
val CorrectColor = Color(0xFF32654A)
val IncorrectColor = Color(0xFFB34343)
private val LightColors = lightColorScheme(
    primary = Color(0xFF376977), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EDF0), onPrimaryContainer = Color(0xFF203F47),
    background = Color(0xFFF7F8F7), surface = Color(0xFFFCFDFC),
    surfaceVariant = Color(0xFFEDF0EE), onSurface = Color(0xFF263238),
    onSurfaceVariant = Color(0xFF58656B), outline = Color(0xFF859396), error = IncorrectColor
)
private val DarkColors = darkColorScheme(primary = Color(0xFF94C8D6), background = Color(0xFF182024), surface = Color(0xFF202A2E))
@Composable fun AutoSkolaTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(
            headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
            bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
        ), content = content)
}
