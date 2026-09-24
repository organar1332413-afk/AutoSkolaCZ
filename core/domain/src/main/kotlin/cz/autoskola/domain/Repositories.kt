package cz.autoskola.domain

import kotlinx.coroutines.flow.Flow
interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setUiLanguage(value: UiLanguage)
    suspend fun setMaterialMode(value: MaterialMode)
    suspend fun completeOnboarding(settings: UserSettings)
    suspend fun setLevel(value: LearningLevel)
    suspend fun setLicenceGroup(value: LicenceGroup)
}
interface StudyRepository {
    fun status(): Flow<ContentStatus?>
    fun questions(locale: String?): Flow<List<QuestionCard>>
    fun words(locale: String): Flow<List<Lexeme>>
    suspend fun saveWord(id: String)
}
// Network implementation and signed update distribution belong to a later stage.
interface ContentUpdateSource { suspend fun latestManifest(): ContentManifest? }
