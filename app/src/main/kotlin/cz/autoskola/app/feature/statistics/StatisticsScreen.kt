package cz.autoskola.app.feature.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.app.feature.questions.*
import cz.autoskola.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun RowScope.StatCard(title:String,value:String) {
    OutlinedCard(Modifier.weight(1f)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(value,style=MaterialTheme.typography.headlineSmall)
            Text(title,style=MaterialTheme.typography.bodySmall)
        }
    }
}

private fun examDate(value:Long):String =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(value))

private fun duration(value:Long):String {
    val minutes=value/60
    val seconds=value%60
    return "%d:%02d".format(Locale.ROOT,minutes,seconds)
}

@Composable
fun StatisticsScreen(snapshot:LearningSnapshot,questions:List<QuestionCard>,status:ContentStatus?,words:List<Lexeme>,selectedGroup:LicenceGroup) {
    val official=snapshot.attempts.filter { !it.sample }
    val sample=snapshot.attempts.filter { it.sample }
    val active=if(status?.sample==false) questions else emptyList()
    val history=snapshot.examHistory
    val examStats=snapshot.examStatistics(selectedGroup)
    val relevantAttempts=official.filter { it.examLicenceGroup==null } + examStats.attempts
    val learned=active.count { q->relevantAttempts.any { it.revisionId==q.revisionId } }
    val summary=examStats.history
    val weakTopics=examStats.weakTopics

    Page {
        item { Heading(text(R.string.statistics)) }

        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                StatCard(text(R.string.stats_exams),summary.size.toString())
                StatCard(text(R.string.stats_best),examStats.best?.let { "${it.score}/${it.maxPoints}" } ?: "—")
                StatCard(text(R.string.stats_pass_rate),examStats.passRate?.let { "$it%" } ?: "—")
            }
        }

        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(text(R.string.stats_exam_summary),style=MaterialTheme.typography.titleMedium)
                    Note(text(R.string.stats_average,examStats.averagePercent?.let { "%.1f".format(Locale.ROOT,it) } ?: "—"))
                    summary.firstOrNull()?.let { last->
                        Note(text(R.string.stats_last_exam,last.score,last.maxPoints,examDate(last.completedAt)))
                    }
                }
            }
        }

        if(history.isNotEmpty()) {
            item { Text(text(R.string.stats_recent),style=MaterialTheme.typography.titleMedium) }
            history.take(5).forEach { exam->
                item {
                    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(examDate(exam.completedAt))
                            Text(exam.licenceGroup.code + " · " + exam.score + " / " + exam.maxPoints)
                        }
                        LinearProgressIndicator(
                            progress={ (exam.score.toFloat()/exam.maxPoints).coerceIn(0f,1f) },
                            modifier=Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item { Text(text(R.string.stats_exam_history),style=MaterialTheme.typography.titleMedium) }
            history.forEach { exam->
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(
                                text(
                                    if(exam.passed) R.string.exam_passed else R.string.exam_failed
                                ),
                                style=MaterialTheme.typography.titleMedium
                            )
                            Note(text(R.string.stats_history_score,exam.score,exam.maxPoints))
                            Note(text(R.string.stats_history_date,examDate(exam.completedAt)))
                            Note(text(R.string.stats_history_duration,duration(exam.durationSeconds)))
                            Note(text(R.string.stats_history_category,exam.licenceGroup.code))
                            Note(text(R.string.stats_history_database,exam.databaseVersion))
                        }
                    }
                }
            }
        } else {
            item { Note(text(R.string.stats_no_exam_history)) }
        }

        item { HorizontalDivider() }
        item { Text(text(R.string.stats_question_progress),style=MaterialTheme.typography.titleMedium) }
        item { Note(text(R.string.stats_questions,learned)) }
        item { Note(text(R.string.stats_remaining,(active.size-learned).coerceAtLeast(0))) }
        if(examStats.correctPercent==null) item { Note(text(R.string.stats_empty_official)) }
        else item { Note(text(R.string.stats_correct,examStats.correctPercent)) }

        item { Note(text(R.string.stats_words,words.count { it.saved },words.count { it.saved && it.repetitions>0 })) }

        item { HorizontalDivider() }
        item { Text(text(R.string.stats_weak_topics),style=MaterialTheme.typography.titleMedium) }
        if(weakTopics.isEmpty()) item { Note(text(R.string.stats_no_weak_topics)) }
        weakTopics.forEach { (category,count)->
            item { Note(text(R.string.stats_weak_topic,categoryLabel(category),count)) }
        }

        if(sample.isNotEmpty()) {
            item { HorizontalDivider() }
            item { Heading(text(R.string.sample_title)) }
            item { Note(text(R.string.stats_sample,sample.size,sample.count { it.correct })) }
        }

        val current=if(status?.sample==true) sample else examStats.attempts
        item { Text(text(R.string.stats_reasons),style=MaterialTheme.typography.titleMedium) }
        ErrorReason.entries.forEach { reason->
            item {
                Note(text(R.string.stats_reason_count,reasonLabel(reason),current.count { !it.correct && it.reason==reason }))
            }
        }
    }
}
