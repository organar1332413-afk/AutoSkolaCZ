package cz.autoskola.design

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val CorrectColor = PremiumColors.success
val IncorrectColor = PremiumColors.error
private val LightColors = lightColorScheme(
    primary = PremiumColors.primary, onPrimary = Color.White,
    primaryContainer = PremiumColors.primaryContainer, onPrimaryContainer = PremiumColors.primaryText,
    background = PremiumColors.background, surface = PremiumColors.surface,
    surfaceContainer = PremiumColors.surface, surfaceContainerHigh = PremiumColors.elevatedSurface,
    surfaceVariant = PremiumColors.primaryContainer, onSurface = PremiumColors.primaryText,
    onSurfaceVariant = PremiumColors.secondaryText, outline = PremiumColors.secondaryText,
    outlineVariant = PremiumColors.subtleBorder, error = PremiumColors.error
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF95CFC1), onPrimary = Color(0xFF173B33),
    primaryContainer = Color(0xFF294A41), onPrimaryContainer = Color(0xFFD1EADF),
    background = Color(0xFF172024), surface = Color(0xFF202B2E),
    surfaceContainer = Color(0xFF243236), surfaceContainerHigh = Color(0xFF2A383B),
    onSurface = Color(0xFFE2EBE6), onSurfaceVariant = Color(0xFFB8C8C2),
    outlineVariant = Color(0xFF40554E), error = Color(0xFFFFB4AB)
)
private val PremiumTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)
@Composable fun AutoSkolaTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (darkTheme) PremiumPalette(DarkColors.surfaceContainerHigh, Color(0xFF253C33), Color(0xFF9DD3AE))
        else PremiumPalette(PremiumColors.elevatedSurface, PremiumColors.visitedTint, PremiumColors.success)
    CompositionLocalProvider(LocalPremiumPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PremiumTypography,
            shapes = Shapes(medium = PremiumShapes.button, large = PremiumShapes.card),
            content = content
        )
    }
}
