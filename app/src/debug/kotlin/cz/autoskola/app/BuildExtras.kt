package cz.autoskola.app
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import cz.autoskola.app.ui.*
import cz.autoskola.app.feature.profile.SettingsChoices
import cz.autoskola.data.resetOnboardingForDevelopment
import cz.autoskola.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
/** Compiled only in debug; all mutation controls live in this source set. */
private class DeveloperController(private val container:AppContainer) {
    suspend fun clear(kind:String) = withContext(Dispatchers.IO) {
        val tables=when(kind) {
            "attempts"->listOf("ExamAnswer","ExamAttempt","QuestionAttempt","QuestionReview")
            "progress"->listOf("ExamAnswer","ExamAttempt","QuestionAttempt","QuestionReview","LearningProgress","WordReview")
            "favorites"->listOf("FavoriteQuestion")
            "words"->listOf("WordReview","SavedWord")
            else->error("Unknown action")
        }
        container.db.runInTransaction {
            tables.forEach { container.db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
            if(kind=="progress") container.db.openHelper.writableDatabase.execSQL("UPDATE SavedWord SET repetitions=0,correctCount=0,nextReviewAt=NULL")
        }
        container.db.invalidationTracker.refreshAsync()
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun BuildExtras(vm:MainViewModel,settings:UserSettings,status:ContentStatus?,open:(String)->Unit) {
    var visible by rememberSaveable { mutableStateOf(false) };var pending by rememberSaveable { mutableStateOf<String?>(null) }
    TextButton(onClick={visible=true}) { Text(text(R.string.dev_title)) }
    if(visible) ModalBottomSheet(onDismissRequest={visible=false}) { Box(androidx.compose.ui.Modifier.fillMaxHeight(0.9f)) { Page {
        item { Heading(text(R.string.dev_title));Note(text(R.string.dev_note)) }
        item { Note(text(R.string.dev_room,1));Note(text(R.string.dev_version,status?.databaseVersion ?: "—"));Note(text(R.string.dev_count,status?.count ?: 0));Note(text(R.string.dev_complete,text(if(status?.completeForB==true) R.string.dev_yes else R.string.dev_no))) }
        item { Column { SettingsChoices(settings,vm::ui,vm::material,vm::level) } }
        item { TextButton(onClick={vm.update { vm.container.settings.resetOnboardingForDevelopment() };visible=false}) { Text(text(R.string.dev_reset_onboarding)) } }
        listOf("progress" to R.string.dev_reset_progress,"attempts" to R.string.dev_clear_attempts,"favorites" to R.string.dev_clear_favorites,"words" to R.string.dev_clear_words).forEach { (key,label)->item { TextButton(onClick={pending=key}) { Text(text(label)) } } }
        item { Text(text(R.string.dev_tts));SpeechButtons("Řidič nesmí ohrozit ostatní účastníky provozu.") }
        item { Entry(text(R.string.dev_sample)) { visible=false;open("lesson/debug-reading") } }
    } } }
    if(pending!=null) AlertDialog(onDismissRequest={pending=null},text={Text(text(R.string.dev_confirm))},confirmButton={TextButton(onClick={val action=pending!!;pending=null;vm.update { DeveloperController(vm.container).clear(action) }}) { Text(text(R.string.confirm)) }},dismissButton={TextButton(onClick={pending=null}) { Text(text(R.string.cancel)) }})
}
internal suspend fun initializeBuildContent(container:AppContainer) = withContext(Dispatchers.IO) {
    val lesson=container.application.assets.open("content/lesson-demo.json").bufferedReader().use { JSONObject(it.readText()) }
    container.db.runInTransaction {
        val db=container.db.openHelper.writableDatabase
        db.execSQL("INSERT OR IGNORE INTO Lesson(id,topic,titleCs,source,contentVersion,reviewStatus,estimatedMinutes) VALUES(?,?,?,?,?,?,?)",arrayOf<Any>("debug-reading","demo",lesson.getString("cs"),"debug:interface-walkthrough","stage2-demo","draft",5))
        listOf("ru","uk").forEach { locale->db.execSQL("INSERT OR IGNORE INTO LessonTranslation(lessonId,locale,title) VALUES(?,?,?)",arrayOf<Any>("debug-reading",locale,lesson.getString(locale))) }
        val blocks=lesson.getJSONArray("blocks")
        for(i in 0 until blocks.length()) {
            val block=blocks.getJSONObject(i);val id="debug-reading-$i"
            db.execSQL("INSERT OR IGNORE INTO LessonBlock(id,lessonId,position,kind,textCs,mediaPath) VALUES(?,?,?,?,?,NULL)",arrayOf<Any>(id,"debug-reading",i,block.getString("kind"),block.getString("cs")))
            listOf("ru","uk").forEach { locale->db.execSQL("INSERT OR IGNORE INTO LessonBlockTranslation(blockId,locale,text) VALUES(?,?,?)",arrayOf<Any>(id,locale,block.getString(locale))) }
        }
        listOf("RP0604222","RP0606185","RP0605535").forEachIndexed { i,id->db.execSQL("INSERT OR IGNORE INTO LessonQuestion(lessonId,questionId,position) SELECT ?,id,? FROM Question WHERE id=?",arrayOf<Any>("debug-reading",i,id)) }
    }
    container.db.invalidationTracker.refreshAsync()
}
