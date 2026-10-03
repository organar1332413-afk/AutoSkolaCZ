package cz.autoskola.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable fun PremiumCard(
    modifier: Modifier = Modifier,
    viewed: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalPremiumPalette.current
    val colors = CardDefaults.cardColors(containerColor = if (viewed) palette.visitedTint else palette.elevatedSurface)
    val border = BorderStroke(PremiumSize.border, MaterialTheme.colorScheme.outlineVariant)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PremiumMotion.pressedScale else 1f,
        animationSpec = tween(PremiumMotion.quickMillis), label = "card-press")
    val elevation = CardDefaults.cardElevation(defaultElevation = PremiumElevation.card, pressedElevation = PremiumElevation.pressed)
    if (onClick == null) Card(modifier, shape = PremiumShapes.card, colors = colors, elevation = elevation, border = border, content = content)
    else Card(onClick = onClick, modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = PremiumShapes.card, colors = colors, elevation = elevation, border = border,
        interactionSource = source, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = navigationIcon, actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
}

@Composable fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, trailingIcon: @Composable (() -> Unit)? = null) {
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), singleLine = true,
        label = { Text(placeholder) }, shape = PremiumShapes.field, trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = LocalPremiumPalette.current.elevatedSurface,
            focusedContainerColor = LocalPremiumPalette.current.elevatedSurface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
}

@Composable fun CategoryChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    FilterChip(selected, onClick, label = { Text(label) }, modifier = modifier.heightIn(min = PremiumSize.touch),
        shape = PremiumShapes.chip,
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
}

@Composable fun DetailSectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    PremiumCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(PremiumSpace.lg), verticalArrangement = Arrangement.spacedBy(PremiumSpace.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier.heightIn(min = PremiumSize.touch), shape = PremiumShapes.button) { Text(label) }
}
