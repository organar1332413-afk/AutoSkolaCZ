package cz.autoskola.app.feature.catalog

import cz.autoskola.domain.SignProgress

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
val SignCategoryNames = linkedMapOf(
    "warning" to "Výstražné", "priority" to "Upravující přednost", "prohibition" to "Zákazové",
    "mandatory" to "Příkazové", "information_zone" to "Informativní zónové",
    "information_traffic" to "Informativní provozní", "information_direction" to "Informativní směrové",
    "information_other" to "Informativní jiné", "additional_panel" to "Dodatkové tabulky",
    "road_marking" to "Vodorovné značky", "light_signal" to "Světelné signály"
)
