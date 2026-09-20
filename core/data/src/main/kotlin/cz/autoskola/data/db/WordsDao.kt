package cz.autoskola.data.db

import androidx.room.*
import cz.autoskola.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WordsDao {
    @Query("SELECT * FROM DictionaryForm") fun forms(): Flow<List<DictionaryFormEntity>>
    @Query("SELECT * FROM DictionaryWord ORDER BY lemma") fun words(): Flow<List<DictionaryWordEntity>>
    @Query("SELECT * FROM DictionaryTranslation WHERE locale = :locale") fun translations(locale: String): Flow<List<DictionaryTranslationEntity>>
    @Query("SELECT * FROM SavedWord") fun saved(): Flow<List<SavedWordEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun save(item: SavedWordEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWords(items: List<DictionaryWordEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTranslations(items: List<DictionaryTranslationEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertForms(items: List<DictionaryFormEntity>)
}
