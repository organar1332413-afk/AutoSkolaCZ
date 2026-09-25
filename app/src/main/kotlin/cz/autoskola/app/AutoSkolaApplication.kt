package cz.autoskola.app
import android.app.Application
import androidx.room.Room
import cz.autoskola.data.*
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.db.MIGRATION_1_2
import cz.autoskola.data.importer.ContentImporter
import java.io.File
class AutoSkolaApplication : Application() {
    val container by lazy { AppContainer(this) }
}
class AppContainer(internal val application: Application) {
    internal val db = Room.databaseBuilder(application, AutoSkolaDatabase::class.java, "autoskola.db").addMigrations(MIGRATION_1_2).build()
    val settings = SettingsStore(application)
    val study = RoomStudyRepository(db)
    val learning = LearningRepository(db)
    val assessments = QuestionAssessmentStore(application)
    val exams = ExamRepository(db, mediaRoot = File(application.filesDir, "content"))
    private val importer = ContentImporter(db, File(application.filesDir, "content"))
    val bootstrap = Bootstrap(application, db, importer, BuildConfig.DEBUG)
}
