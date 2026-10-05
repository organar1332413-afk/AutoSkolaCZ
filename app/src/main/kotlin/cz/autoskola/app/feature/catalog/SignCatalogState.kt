package cz.autoskola.app.feature.catalog

import cz.autoskola.domain.SignProgress
import cz.autoskola.app.R

data class SignCatalogLoadState(val entries: List<SignEntry> = emptyList(), val loading: Boolean = true, val failed: Boolean = false)
enum class SignViewFilter { ALL, VIEWED, UNVIEWED }
data class SignCatalogFilter(
    val query: String = "",
    val category: String? = null,
    val favoritesOnly: Boolean = false,
    val view: SignViewFilter = SignViewFilter.ALL
)
fun filterSigns(entries: List<SignEntry>, filter: SignCatalogFilter, progress: SignProgress): List<SignEntry> =
    SignCatalog.search(entries, filter.query, filter.category).filter {
        (!filter.favoritesOnly || it.code in progress.favorites) && when(filter.view) {
            SignViewFilter.ALL -> true
            SignViewFilter.VIEWED -> it.code in progress.viewed
            SignViewFilter.UNVIEWED -> it.code !in progress.viewed
        }
    }

/** Existing category keys and their Czech legal families: A/P/B/C/IZ/IP/IS/IJ/E/V/S. */
val SignCategoryLabels = linkedMapOf(
    "warning" to R.string.sign_warning, "priority" to R.string.sign_priority, "prohibition" to R.string.sign_prohibition,
    "mandatory" to R.string.sign_mandatory, "information_zone" to R.string.sign_info_zone,
    "information_traffic" to R.string.sign_info_traffic, "information_direction" to R.string.sign_info_direction,
    "information_other" to R.string.sign_info_other, "additional_panel" to R.string.sign_extra,
    "road_marking" to R.string.sign_markings, "light_signal" to R.string.sign_lights
)
