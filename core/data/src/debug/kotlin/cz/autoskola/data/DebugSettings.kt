package cz.autoskola.data
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
/** This extension does not exist in release. */
suspend fun SettingsStore.resetOnboardingForDevelopment() { store.edit { it[booleanPreferencesKey("onboarding_completed")]=false } }
