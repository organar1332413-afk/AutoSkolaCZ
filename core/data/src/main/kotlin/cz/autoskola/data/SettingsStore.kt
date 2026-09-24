package cz.autoskola.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import cz.autoskola.domain.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
private val Context.settingsStore by preferencesDataStore("user_settings")
class SettingsStore(context: Context) : SettingsRepository {
    internal val store = context.applicationContext.settingsStore
    private val deviceTag = context.resources.configuration.locales[0].language
    private val ui = stringPreferencesKey("ui_language")
    private val material = stringPreferencesKey("material_mode")
    private val level = stringPreferencesKey("learning_level")
    private val completed = booleanPreferencesKey("onboarding_completed")
    private val licenceGroup = stringPreferencesKey("licence_group")
    override val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        restoredSettings(p[ui], p[material], p[level], p[completed], deviceTag, p[licenceGroup])
    }
    override suspend fun setUiLanguage(value: UiLanguage) { store.edit { it[ui] = value.name } }
    override suspend fun setMaterialMode(value: MaterialMode) { store.edit { it[material] = value.name } }
    override suspend fun setLevel(value: LearningLevel) { store.edit { it[level] = value.name } }
    override suspend fun setLicenceGroup(value: LicenceGroup) { store.edit { it[licenceGroup] = value.name } }
    override suspend fun completeOnboarding(settings: UserSettings) { store.edit {
        it[ui] = settings.uiLanguage.name; it[material] = settings.materialMode.name; it[level] = settings.level.name; it[licenceGroup] = settings.licenceGroup.name; it[completed] = true
    } }
}
