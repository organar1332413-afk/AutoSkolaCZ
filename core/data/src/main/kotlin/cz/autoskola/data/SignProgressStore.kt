package cz.autoskola.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import cz.autoskola.domain.SignProgress
import kotlinx.coroutines.flow.map

/** Uses the existing user_settings DataStore, not a parallel preference file or Room schema. */
class SignProgressStore internal constructor(private val store: DataStore<Preferences>) {
    private val viewed = stringSetPreferencesKey("signs_viewed")
    private val favorites = stringSetPreferencesKey("signs_favorites")
    val progress = store.data.map { SignProgress(it[viewed].orEmpty(), it[favorites].orEmpty()) }

    suspend fun markViewed(code: String) {
        validate(code)
        store.edit { it[viewed] = it[viewed].orEmpty() + code }
    }
    suspend fun setFavorite(code: String, favorite: Boolean) {
        validate(code)
        store.edit { it[favorites] = if (favorite) it[favorites].orEmpty() + code else it[favorites].orEmpty() - code }
    }
    private fun validate(code: String) { require(code.isNotBlank() && code.length <= 100) }
}
