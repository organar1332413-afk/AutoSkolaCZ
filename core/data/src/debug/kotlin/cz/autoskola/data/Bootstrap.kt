package cz.autoskola.data
import android.content.Context
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.importer.ContentImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
class Bootstrap(private val context: Context, private val db: AutoSkolaDatabase, private val importer: ContentImporter, private val allowSamples: Boolean = false) {
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (!allowSamples) return@withContext
        if (db.content().activeVersion().first() == null) {
            importer.importPackage(context.assets.open("content/sample-v1.json").use { it.readBytes() })
        }
    }
}
