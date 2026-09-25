package cz.autoskola.data

import androidx.room.Room
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.db.MIGRATION_1_2
import cz.autoskola.domain.ExamConfigurationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RoomMigrationTest {
    @Test fun exportedV1SchemaMigratesCompletedAndUnfinishedBExamsWithoutLosingRevisions() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-${System.nanoTime()}.db"
        val schema = JSONObject(File("schemas/cz.autoskola.data.db.AutoSkolaDatabase/1.json").readText()).getJSONObject("database")
        val legacy = context.openOrCreateDatabase(name, 0, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) legacy.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) legacy.execSQL(setup.getString(i))
            legacy.execSQL("INSERT INTO DatabaseVersion VALUES('legacy','legacy','2025-01-01','https://etesty.md.gov.cz/','2025-01-01T00:00:00Z',1,0,1,'hash')")
            legacy.execSQL("INSERT INTO DatabaseVersion VALUES('sample','sample',NULL,'https://etesty.md.gov.cz/','2025-01-01T00:00:00Z',1,1,0,'hash2')")
            legacy.execSQL("INSERT INTO ActiveContent VALUES(1,'legacy')")
            ExamConfigurationProvider.forGroup(cz.autoskola.domain.LicenceGroup.B).sections.forEach { section ->
                legacy.execSQL("INSERT INTO QuestionCategory VALUES(?,?)", arrayOf(section.category, section.category))
                repeat(section.questionCount) { n ->
                    val id = "${section.category}-$n"
                    val revision = "legacy:$id"
                    legacy.execSQL("INSERT INTO Question VALUES(?,?)", arrayOf(id, id))
                    legacy.execSQL("INSERT INTO QuestionRevision VALUES(?,?,?,?,?,?,?)", arrayOf(revision, id, "legacy", section.category, "Test", section.pointsPerQuestion, "https://etesty.md.gov.cz/"))
                    legacy.execSQL("INSERT INTO Answer VALUES(?,?,?,?,?)", arrayOf(revision, "A", "Yes", 1, 0))
                    legacy.execSQL("INSERT INTO Answer VALUES(?,?,?,?,?)", arrayOf(revision, "B", "No", 0, 1))
                    legacy.execSQL("INSERT INTO QuestionLicenceGroup VALUES(?,?)", arrayOf(revision, "B"))
                }
            }
            legacy.execSQL("INSERT INTO ExamAttempt VALUES('unfinished','legacy','B-stage2-v1',100,1800100,NULL,NULL,'REAL')")
            legacy.execSQL("INSERT INTO ExamAttempt VALUES('completed','legacy','B-stage2-v1',100,1800100,200,50,'REAL')")
            val revisions = mutableListOf<String>()
            legacy.rawQuery("SELECT id FROM QuestionRevision ORDER BY categoryId, questionId", null).use { cursor -> while (cursor.moveToNext()) revisions += cursor.getString(0) }
            revisions.forEachIndexed { n, revision ->
                legacy.execSQL("INSERT INTO ExamAnswer VALUES(?,?,?,NULL,NULL)", arrayOf("unfinished", n, revision))
                legacy.execSQL("INSERT INTO ExamAnswer VALUES(?,?,?,?,?)", arrayOf("completed", n, revision, "A", 0))
            }
            legacy.version = 1
        } finally { legacy.close() }
        val db = Room.databaseBuilder(context, AutoSkolaDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val old = db.learning().exam("unfinished")!!
            assertEquals("B", old.licenceGroup)
            assertEquals(25, old.questionCount)
            assertEquals(50, old.maxPoints)
            assertEquals(43, old.passPoints)
            assertEquals("B-stage2-v1", old.blueprintVersion)
            assertEquals(25, db.learning().examAnswers("unfinished").size)
            assertEquals(25, db.learning().examAnswers("completed").size)
            assertEquals("legacy:", db.learning().examAnswers("unfinished").first().revisionId.take(7))
            assertEquals("unfinished", db.learning().unfinishedExam()!!.id)
            assertEquals(50, db.learning().exam("completed")!!.score)
            assertEquals(cz.autoskola.domain.LicenceGroup.B, LearningRepository(db).snapshot.first().examHistory.single().licenceGroup)
            assertNull(db.content().readiness("sample", "B"))
            assertNotNull(db.content().readiness("legacy", "B"))
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            db.openHelper.writableDatabase.query("PRAGMA index_list('ExamAttempt')").use { assertTrue(it.count > 0) }
            assertEquals(25, ExamRepository(db) { 150L }.unfinished()!!.second.items.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
