package cz.autoskola.app.feature.statistics
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.app.feature.questions.*
import cz.autoskola.domain.*
@Composable fun StatisticsScreen(snapshot:LearningSnapshot,questions:List<QuestionCard>,status:ContentStatus?,words:List<Lexeme>) {
    val official=snapshot.attempts.filter { !it.sample }
    val sample=snapshot.attempts.filter { it.sample }
    val active=if(status?.sample==false) questions else emptyList()
    val learned=active.count { q->official.any { it.revisionId==q.revisionId } }
    Page {
        item { Heading(text(R.string.statistics)) }
        item { Note(text(R.string.stats_questions,learned)) }
        item { Note(text(R.string.stats_remaining,active.size-learned)) }
        if(official.isEmpty()) item { Note(text(R.string.stats_empty_official)) }
        else item { Note(text(R.string.stats_correct,official.count { it.correct }*100/official.size)) }
        item { Note(text(R.string.stats_exams,snapshot.examScores.size)) }
        if(snapshot.examScores.isNotEmpty()) {
            item { Note(text(R.string.stats_average,"%.1f".format(java.util.Locale.ROOT,snapshot.examScores.average()))) }
            item { Note(text(R.string.stats_recent));Note(snapshot.examScores.take(5).joinToString(" · ")) }
        }
        item { Note(text(R.string.stats_words,words.count { it.saved },words.count { it.saved && it.repetitions>0 })) }
        item { HorizontalDivider() }
        if(sample.isNotEmpty()) {
            item { Heading(text(R.string.sample_title)) }
            item { Note(text(R.string.stats_sample,sample.size,sample.count { it.correct })) }
        }
        val current=if(status?.sample==true) sample else official
        current.groupBy { it.category }.forEach { (category,attempts)->item { Text(categoryLabel(category));Note(text(R.string.stats_topic,attempts.count { it.correct },attempts.size)) } }
        item { Text(text(R.string.stats_reasons),style=MaterialTheme.typography.titleMedium) }
        ErrorReason.entries.forEach { reason->item { Note(text(R.string.stats_reason_count,reasonLabel(reason),current.count { !it.correct && it.reason==reason })) } }
    }
}
