package cz.autoskola.data
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.importer.ContentImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class ImportTest {
    @Test fun importIsIdempotentAndBadUpdateKeepsActiveVersion() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AutoSkolaDatabase::class.java).build()
        val root = File(context.cacheDir, "import-test-${System.nanoTime()}")
        try {
            val importer = ContentImporter(db, root)
            val original = instrumentation.context.assets.open("sample-v1.json").use { it.readBytes() }
            val version = importer.importPackage(original)
            importer.importPackage(original)
            assertEquals(3, db.content().questions().first().size)
            val corrupt = original.decodeToString().replace("RP0604222", "RP0606185").encodeToByteArray()
            var rejected = false
            try { importer.importPackage(corrupt) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(version, db.content().activeVersion().first()!!.id)
            assertEquals(3, db.content().questions().first().size)
            val collision = original.decodeToString().replace("Транспортное средство", "Транспорт").encodeToByteArray()
            rejected = false
            try { importer.importPackage(collision) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        } finally { db.close(); root.deleteRecursively() }
    }
}
