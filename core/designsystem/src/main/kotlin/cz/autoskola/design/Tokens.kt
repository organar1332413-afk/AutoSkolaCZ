package cz.autoskola.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Soft 3D Premium / Material 3 Expressive Lite. No blur or dynamic palette. */
object PremiumColors {
    val background = Color(0xFFF5F8F7)
    val surface = Color(0xFFFBFDFC)
    val elevatedSurface = Color.White
    val primary = Color(0xFF36776E)
    val primaryContainer = Color(0xFFE0EFEB)
    val primaryText = Color(0xFF202F36)
    val secondaryText = Color(0xFF52656C)
    val subtleBorder = Color(0xFFDDE7E3)
    val success = Color(0xFF326C50)
    val warning = Color(0xFF8A650E)
    val error = Color(0xFFB34343)
    val visitedTint = Color(0xFFEDF5F1)
}
data class PremiumPalette(val elevatedSurface: Color, val visitedTint: Color, val success: Color)
val LocalPremiumPalette = staticCompositionLocalOf {
    PremiumPalette(PremiumColors.elevatedSurface, PremiumColors.visitedTint, PremiumColors.success)
}
object PremiumSpace {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}
object PremiumShapes {
    val card = RoundedCornerShape(24.dp)
    val button = RoundedCornerShape(18.dp)
    val chip = RoundedCornerShape(16.dp)
    val field = RoundedCornerShape(20.dp)
}
object PremiumElevation {
    val card = 2.dp
    val pressed = 0.dp
    val hero = 3.dp
    val navigation = 2.dp
}
object PremiumSize {
    val touch = 48.dp
    val signThumbnail = 112.dp
    val signHero = 220.dp
    val stateIcon = 16.dp
    val gridCardMinHeight = 242.dp
    val border = 1.dp
}
object PremiumMotion {
    const val quickMillis = 140
    const val pressedScale = 0.992f
}
