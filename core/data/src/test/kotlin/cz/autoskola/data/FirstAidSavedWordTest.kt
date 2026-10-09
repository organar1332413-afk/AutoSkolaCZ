package cz.autoskola.data

import android.app.Application
import androidx.room.Room
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.domain.Lexeme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class FirstAidSavedWordTest {
    @Test fun savesBothHelpersWithFormsWithoutOverwritingDictionaryOrDuplicatingSavedWord() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AutoSkolaDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo=RoomStudyRepository(db)
            val word=Lexeme("aid-přilba","přilba","шлем","шлем","přilba","шлем",false,"ru",listOf("přilbu"))
            repo.saveAidWord(word);repo.saveAidWord(word.copy(translation="wrong replacement"))
            repo.saveAidWord(word.copy(locale="uk",translation="шолом",meaning="шолом"))
            val ru=repo.words("ru").first().single(); val uk=repo.words("uk").first().single()
            assertEquals("шлем",ru.translation);assertEquals("шолом",uk.translation)
            assertTrue(ru.saved);assertTrue(uk.saved);assertTrue("přilbu" in ru.forms)
            assertEquals(1,db.words().saved().first().size)
        } finally { db.close() }
    }
}
