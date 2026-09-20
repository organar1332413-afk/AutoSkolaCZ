package cz.autoskola.data
import android.content.Context
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.importer.ContentImporter
/** Production content is installed through the verified importer, never through sample seeding. */
class Bootstrap(context:Context,db:AutoSkolaDatabase,importer:ContentImporter,allowSamples:Boolean=false) {
    suspend fun initialize() = Unit
}
