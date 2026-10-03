package cz.autoskola.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SignProgressStoreTest {
    @Test fun viewedAndFavoriteSurviveClosingAndReopeningDataStoreWithoutChangingSettings() = runBlocking {
        val file = Files.createTempDirectory("sign-progress").resolve("user_settings.preferences_pb").toFile()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val language = stringPreferencesKey("material_mode")
        dataStore.edit { it[language] = "CS_UK" }
        val firstStore = SignProgressStore(dataStore)
        firstStore.setFavorite("A 10", true)
        assertFalse("A bookmark must not mark a sign viewed", "A 10" in firstStore.progress.first().viewed)
        firstStore.markViewed("A 10")
        firstStore.markViewed("P 4")
        firstStore.markViewed("A 10")
        assertEquals(setOf("A 10", "P 4"), firstStore.progress.first().viewed)
        scope.coroutineContext[Job]!!.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val reopened = SignProgressStore(dataStore)
            assertEquals(setOf("A 10", "P 4"), reopened.progress.first().viewed)
            assertEquals(setOf("A 10"), reopened.progress.first().favorites)
            assertEquals("CS_UK", dataStore.data.first()[language])
            reopened.setFavorite("A 10", false)
            assertTrue(reopened.progress.first().favorites.isEmpty())
            assertEquals(setOf("A 10", "P 4"), reopened.progress.first().viewed)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.parentFile.deleteRecursively() }
    }
}
