package cz.autoskola.app.feature.home
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.domain.*

@Composable
private fun RowScope.MetricCard(title:String,value:Int,onClick:()->Unit) {
    OutlinedCard(onClick=onClick,modifier=Modifier.weight(1f)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(value.toString(),style=MaterialTheme.typography.headlineSmall)
            Text(title,style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun HomeScreen(
    settings:UserSettings,
    questions:List<QuestionCard>,
    status:ContentStatus?,
    lessons:List<LessonCard>,
    progress:LearningSnapshot,
    words:List<Lexeme>,
    open:(String)->Unit
) {
    val attempted=questions.count { q->progress.attempts.any { it.revisionId==q.revisionId } }
    val mistakes=questions.count { progress.isMistake(it) }
    val unseen=questions.count { q->progress.attempts.none { it.revisionId==q.revisionId } }
    val favorites=questions.count { it.officialId in progress.favorites }
    val doubtful=questions.count { progress.isDoubtful(it) }
    val percent=if(questions.isEmpty()) 0 else attempted*100/questions.size
    val continued=progress.progress.firstOrNull { it.completedAt==null && lessons.any { l->l.id==it.lessonId } }
    val savedWords=words.count { it.saved }
    val lastExam=progress.examHistory.firstOrNull()

    Page {
        item { Heading(text(R.string.home_prepare_category,settings.licenceGroup.code)) }
        item {
            Entry(
                text(R.string.home_progress),
                text(R.string.home_progress_detail,attempted,questions.size,percent)
            ) { open("questions") }
        }
        if(status?.sample==true) item { Note(text(R.string.sample_notice,questions.size)) }

        item {
            Button(
                onClick={open(continued?.let { "lesson/${it.lessonId}" } ?: "questions")},
                modifier=Modifier.fillMaxWidth()
            ) {
                Text(text(if(continued==null) R.string.home_continue_questions else R.string.continue_learning))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MetricCard(text(R.string.mistakes),mistakes) { open("mistakes") }
                MetricCard(text(R.string.filter_unseen),unseen) { open("unseen") }
                MetricCard(text(R.string.filter_doubtful),doubtful) { open("doubtful") }
            }
        }

        item { HorizontalDivider() }

        if(lastExam!=null) {
            item {
                Entry(
                    text(R.string.home_last_exam),
                    text(R.string.home_last_exam_score,lastExam.score)
                ) { open("exam") }
            }
        } else {
            item { Entry(text(R.string.home_last_exam),text(R.string.home_no_exams)) { open("exam") } }
        }

        item {
            Entry(
                text(if(settings.nativeCzechMode) R.string.review else R.string.czech),
                text(R.string.home_saved_words,savedWords)
            ) { open(if(settings.nativeCzechMode) "words" else "czech") }
        }

        item { Entry(text(R.string.statistics)) { open("statistics") } }

        item { HorizontalDivider() }
        item {
            Note(
                text(
                    R.string.home_database,
                    status?.publicationDate ?: status?.databaseVersion ?: text(R.string.database_unknown)
                )
            )
        }
    }
}
