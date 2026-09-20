package cz.autoskola.app.feature.home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.domain.*
@Composable fun HomeScreen(settings:UserSettings,lessons:List<LessonCard>,progress:LearningSnapshot,open:(String)->Unit) {
    val continued=progress.progress.firstOrNull { it.completedAt==null && lessons.any { l->l.id==it.lessonId } }
    Page {
        item { Heading(text(if(settings.nativeCzechMode) R.string.native_title else R.string.home_title)) }
        item { Note(text(if(settings.nativeCzechMode) R.string.native_intro else R.string.home_intro)) }
        item { Entry(text(if(continued==null) R.string.start_learning else R.string.continue_learning)) { open(continued?.let { "lesson/${it.lessonId}" } ?: "learn") } }
        item { Entry(text(R.string.rules)) { open("learn") } }
        item { Entry(text(R.string.questions)) { open("questions") } }
        if(!settings.nativeCzechMode) item { Entry(text(R.string.czech)) { open("czech") } }
        item { Entry(text(R.string.exam),text(R.string.exam_info)) { open("exam") } }
        item { Entry(text(R.string.signs)) { open("signs") } }
        item { Entry(text(R.string.first_aid)) { open("first_aid") } }
        item { HorizontalDivider() }
        item { Entry(text(R.string.mistakes)) { open("mistakes") } }
        item { Entry(text(R.string.favorites)) { open("favorites") } }
        item { Entry(text(R.string.statistics)) { open("statistics") } }
    }
}
