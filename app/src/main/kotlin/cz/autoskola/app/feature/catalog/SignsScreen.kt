package cz.autoskola.app.feature.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import cz.autoskola.app.R
import cz.autoskola.app.ui.text
import cz.autoskola.design.*
import cz.autoskola.domain.SignProgress

@Composable fun SignsScreen(
    catalog: SignCatalogLoadState,
    translationTag: String?,
    progress: SignProgress,
    openSign: (String) -> Unit,
    favorite: (String, Boolean) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var viewFilter by rememberSaveable { mutableStateOf(SignViewFilter.ALL) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    // Navigation's SaveableStateHolder restores this destination, including the exact pixel offset.
    // User changes to the search/filter scope start a new grid; viewing/bookmarking does not.
    val gridState = rememberSaveable(query, category, favoritesOnly, viewFilter, saver = LazyGridState.Saver) { LazyGridState() }
    val filter = SignCatalogFilter(query, category, favoritesOnly, viewFilter)
    val visible = remember(catalog.entries, filter, progress) { filterSigns(catalog.entries, filter, progress) }
    val counts = remember(catalog.entries) { catalog.entries.groupingBy { it.category }.eachCount() }
    val activeFilters = (if(favoritesOnly) 1 else 0) + (if(viewFilter != SignViewFilter.ALL) 1 else 0)

    Column(Modifier.fillMaxSize().testTag("sign-catalog")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = PremiumSpace.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${catalog.entries.size} znaků", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (translationTag != null) Text(text(R.string.signs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { filtersOpen = true }, modifier = Modifier.heightIn(min = PremiumSize.touch).testTag("sign-filters")) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
                Text(text(R.string.sign_filters) + if(activeFilters > 0) " · $activeFilters" else "")
            }
        }
        SearchField(query, { query = it }, "Hledat kód nebo název",
            Modifier.padding(horizontal = PremiumSpace.lg).testTag("sign-search"), trailingIcon = {
                if(query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, text(R.string.sign_clear_search)) }
                else Icon(Icons.Default.Search, contentDescription = null)
            })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = PremiumSpace.lg),
            horizontalArrangement = Arrangement.spacedBy(PremiumSpace.xs)) {
            CategoryChip(category == null, { category = null }, "Vše", Modifier.testTag("category-all"))
            SignCategoryNames.forEach { (key, label) ->
                if (counts.containsKey(key)) CategoryChip(category == key, { category = key }, "$label · ${counts[key]}", Modifier.testTag("category-$key"))
            }
        }
        when {
            catalog.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            catalog.failed -> Box(Modifier.fillMaxSize().padding(PremiumSpace.lg), contentAlignment = Alignment.Center) { Text(text(R.string.sign_catalog_error)) }
            visible.isEmpty() -> Column(Modifier.fillMaxWidth().padding(PremiumSpace.xl), verticalArrangement = Arrangement.spacedBy(PremiumSpace.sm)) {
                Text(text(R.string.sign_empty), style = MaterialTheme.typography.titleMedium)
                Text(text(R.string.sign_empty_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryButton(text(R.string.sign_reset_filters), { query = ""; category = null; favoritesOnly = false; viewFilter = SignViewFilter.ALL })
            }
            else -> LazyVerticalGrid(columns = GridCells.Fixed(2), state = gridState,
                modifier = Modifier.fillMaxSize().testTag("sign-grid"),
                contentPadding = PaddingValues(start = PremiumSpace.lg, end = PremiumSpace.lg, top = PremiumSpace.xs, bottom = PremiumSpace.xl),
                horizontalArrangement = Arrangement.spacedBy(PremiumSpace.sm), verticalArrangement = Arrangement.spacedBy(PremiumSpace.sm)) {
                items(visible, key = { it.code }, contentType = { "sign" }) { sign ->
                    SignGridCard(sign, translationTag, sign.code in progress.viewed, sign.code in progress.favorites,
                        { openSign(sign.code) }, { favorite(sign.code, it) })
                }
            }
        }
    }
    if(filtersOpen) AlertDialog(onDismissRequest = { filtersOpen = false }, title = { Text(text(R.string.sign_filters)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text(R.string.sign_favorites_only), Modifier.weight(1f))
                    Switch(favoritesOnly, { favoritesOnly = it }, Modifier.testTag("sign-favorites-filter"))
                }
                SignViewFilter.entries.forEach { value ->
                    val label = text(when(value) { SignViewFilter.ALL -> R.string.sign_state_all; SignViewFilter.VIEWED -> R.string.sign_viewed; SignViewFilter.UNVIEWED -> R.string.sign_unviewed })
                    TextButton(onClick = { viewFilter = value }, modifier = Modifier.fillMaxWidth().heightIn(min = PremiumSize.touch).testTag("filter-${value.name}")) {
                        RadioButton(viewFilter == value, onClick = null)
                        Text(label, Modifier.padding(start = PremiumSpace.xs).weight(1f))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { filtersOpen = false }) { Text(text(R.string.close)) } })
}

@Composable fun SignGraphic(path: String, label: String, modifier: Modifier = Modifier) {
    // Coil decodes the bundled WebP off the UI thread, samples to measured size, and shares its cache.
    AsyncImage(model = "file:///android_asset/signs/$path", contentDescription = label,
        modifier = modifier, contentScale = ContentScale.Fit)
}

@Composable fun SignGridCard(sign: SignEntry, translationTag: String?, viewed: Boolean, favorite: Boolean,
    open: () -> Unit, setFavorite: (Boolean) -> Unit) {
    val viewedLabel = text(if(viewed) R.string.sign_viewed else R.string.sign_unviewed)
    val favoriteLabel = text(if(favorite) R.string.favorite_remove else R.string.favorite_add)
    val helper = when(translationTag) { "ru" -> sign.titleRu; "uk" -> sign.titleUk; else -> null }
    PremiumCard(Modifier.fillMaxWidth().heightIn(min = PremiumSize.gridCardMinHeight).testTag("sign-${sign.code}")
        .semantics { stateDescription = viewedLabel }, viewed = viewed, onClick = open) {
        Column(Modifier.fillMaxWidth().padding(start = PremiumSpace.sm, end = PremiumSpace.sm, top = PremiumSpace.md)) {
            sign.graphicPaths.firstOrNull()?.let { path ->
                SignGraphic(path, "${sign.code} · ${sign.titleCs}", Modifier.fillMaxWidth().height(PremiumSize.signThumbnail))
            }
            Spacer(Modifier.height(PremiumSpace.sm))
            Text(sign.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(PremiumSpace.xxs))
            Text(sign.titleCs, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            helper?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Row(Modifier.fillMaxWidth().padding(start = PremiumSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            if(viewed) {
                Icon(Icons.Default.Check, contentDescription = null, tint = LocalPremiumPalette.current.success, modifier = Modifier.size(PremiumSize.stateIcon))
                Text(text(R.string.sign_viewed), style = MaterialTheme.typography.labelMedium,
                    color = LocalPremiumPalette.current.success, modifier = Modifier.padding(start = PremiumSpace.xxs).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Spacer(Modifier.weight(1f))
            IconToggleButton(checked = favorite, onCheckedChange = setFavorite,
                modifier = Modifier.size(PremiumSize.touch).testTag("favorite-${sign.code}")) {
                Icon(if(favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, favoriteLabel,
                    tint = if(favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
