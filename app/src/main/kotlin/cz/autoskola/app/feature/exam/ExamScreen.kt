package cz.autoskola.app.feature.exam

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.autoskola.app.ExamUiState
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.domain.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
private fun examCategoryLabel(category:String)=text(when(category) {
    "rules"->R.string.rules
    "safe_driving"->R.string.topic_15
    "signs"->R.string.signs
    "situations"->R.string.topic_2
    "vehicle"->R.string.topic_16
    "first_aid"->R.string.first_aid
    else->R.string.topic_18
})

@Composable
fun ExamScreen(
    settings:UserSettings,
    status:ContentStatus?,
    state:ExamUiState,
    availability:ExamAvailability,
    start:()->Unit,
    answer:(String,String)->Unit,
    finish:()->Unit,
    refresh:()->Unit,
    open:(String)->Unit
) {
    val session=state.session
    if(session==null) {
        val config=ExamConfigurationProvider.forGroup(settings.licenceGroup)
        val verified=availability==ExamAvailability.READY
        Page {
            item { Heading(text(R.string.exam)) }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(text(R.string.exam_category,settings.licenceGroup.code),style=MaterialTheme.typography.titleLarge)
                        Note(text(R.string.exam_info,config.questionCount,config.durationMinutes))
                        Note(text(R.string.exam_score,config.maxPoints,config.passPoints))
                    }
                }
            }
            item { Note(text(R.string.exam_rules)) }
            item {
                Note(
                    when {
                        availability==ExamAvailability.SAMPLE_ONLY -> text(R.string.exam_sample_blocked)
                        availability==ExamAvailability.BLUEPRINT_UNVERIFIED -> text(R.string.exam_category_unverified,settings.licenceGroup.code)
                        availability==ExamAvailability.ELIGIBILITY_INCOMPLETE -> text(R.string.exam_unavailable,settings.licenceGroup.code)
                        availability==ExamAvailability.CONTENT_INCOMPLETE -> text(R.string.exam_content_incomplete,settings.licenceGroup.code)
                        availability==ExamAvailability.MEDIA_INCOMPLETE -> text(R.string.exam_media_incomplete,settings.licenceGroup.code)
                        else -> text(R.string.exam_ready,settings.licenceGroup.code)
                    }
                )
            }
            status?.databaseVersion?.let { version->
                item { Note(text(R.string.question_database_version,version)) }
            }
            status?.publicationDate?.let { date->
                item { Note(text(R.string.question_publication_date,date)) }
            }
            item {
                Button(
                    onClick=start,
                    enabled=verified && !state.busy,
                    modifier=Modifier.fillMaxWidth()
                ) {
                    Text(text(R.string.exam_start))
                }
            }
            item { Entry(text(R.string.statistics)) { open("statistics") } }
        }
        return
    }

    if(session.completedAt!=null) {
        val result=ExamEngine.result(session)
        val correct=session.items.count { session.answers[it.revisionId]==it.correctCode }
        val unanswered=session.items.count { it.revisionId !in session.answers }
        val wrong=session.items.size-correct-unanswered
        Page {
            item { Heading(text(R.string.exam_result)) }
            item { Note(text(R.string.exam_category,session.licenceGroup.code)) }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(
                            text(if(result.passed) R.string.exam_passed else R.string.exam_failed),
                            style=MaterialTheme.typography.headlineSmall
                        )
                        Text(text(R.string.exam_result_score,result.score,session.maxPoints),style=MaterialTheme.typography.headlineMedium)
                        Note(text(R.string.exam_result_counts,correct,wrong,unanswered))
                    }
                }
            }
            item { Note(text(R.string.exam_lost_points,result.lostPoints)) }
            if(result.wrongByCategory.isNotEmpty()) {
                item { Text(text(R.string.exam_weak_topics),style=MaterialTheme.typography.titleMedium) }
                result.wrongByCategory.forEach { (category,count)->
                    item { Note(examCategoryLabel(category) + ": " + count) }
                }
            }
            item {
                Button(onClick={open("mistakes")},modifier=Modifier.fillMaxWidth()) {
                    Text(text(R.string.exam_review_mistakes))
                }
            }
            item {
                OutlinedButton(
                    onClick=start,
                    enabled=availability==ExamAvailability.READY && !state.busy,
                    modifier=Modifier.fillMaxWidth()
                ) {
                    Text(text(R.string.exam_new))
                }
            }
            item { TextButton(onClick={open("statistics")}) { Text(text(R.string.statistics)) } }
        }
        return
    }

    var index by rememberSaveable(session.startedAt) { mutableIntStateOf(0) }
    var marked by rememberSaveable(session.startedAt) { mutableStateOf(emptyList<String>()) }
    var confirmFinish by rememberSaveable(session.startedAt) { mutableStateOf(false) }
    var now by remember(session.startedAt) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(session.deadlineAt,session.completedAt) {
        while(session.completedAt==null) {
            now=System.currentTimeMillis()
            if(now>=session.deadlineAt) {
                refresh()
                break
            }
            delay(1000)
        }
    }

    val remaining=ExamEngine.remainingSeconds(session,now)
    val minutes=remaining/60
    val seconds=remaining%60
    val timer=String.format(Locale.ROOT,"%02d:%02d",minutes,seconds)
    val safeIndex=index.coerceIn(0,session.items.lastIndex)
    if(safeIndex!=index) index=safeIndex
    val item=session.items[safeIndex]
    val question=state.questions.firstOrNull { it.revisionId==item.revisionId }
    val answered=session.answers.size
    val unanswered=session.items.size-answered

    Page {
        item { Note(text(R.string.exam_category,session.licenceGroup.code)) }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column {
                    Text(text(R.string.exam_question_number,safeIndex+1,session.items.size),style=MaterialTheme.typography.titleLarge)
                    Note(text(R.string.exam_answered,answered,session.items.size))
                }
                Text(timer,style=MaterialTheme.typography.headlineSmall)
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)
            ) {
                session.items.forEachIndexed { position,navItem->
                    val label=buildString {
                        if(navItem.revisionId in marked) append("★ ")
                        append(position+1)
                        if(navItem.revisionId in session.answers) append(" ✓")
                    }
                    FilterChip(
                        selected=position==safeIndex,
                        onClick={index=position},
                        label={Text(label)}
                    )
                }
            }
        }

        item {
            TextButton(onClick={
                marked=if(item.revisionId in marked) marked-item.revisionId else marked+item.revisionId
            }) {
                Text(text(if(item.revisionId in marked) R.string.exam_unmark else R.string.exam_mark))
            }
        }

        if(question==null) {
            item { Note(text(R.string.exam_question_load_error)) }
        } else {
            item { Text(question.textCs,style=MaterialTheme.typography.titleLarge) }
            question.media.filter { it.answerCode==null }.forEach { media->
                item { LocalMedia(media) }
            }
            question.answers.forEach { option->
                item {
                    val selected=session.answers[item.revisionId]==option.code
                    OutlinedCard(
                        onClick={ if(!state.busy) answer(item.revisionId,option.code) },
                        modifier=Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(
                                (if(selected) "● " else "") + option.code,
                                style=MaterialTheme.typography.titleMedium
                            )
                            Text(option.textCs,style=MaterialTheme.typography.bodyLarge)
                            question.media.filter { it.answerCode==option.code }.forEach { LocalMedia(it) }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                OutlinedButton(onClick={index--},enabled=index>0) { Text(text(R.string.previous)) }
                Button(onClick={index++},enabled=index<session.items.lastIndex) { Text(text(R.string.next)) }
            }
        }

        item {
            HorizontalDivider()
            Button(
                onClick={if(unanswered>0) ({confirmFinish=true}) else finish},
                enabled=!state.busy,
                modifier=Modifier.fillMaxWidth()
            ) {
                Text(text(R.string.exam_finish))
            }
        }
    }

    if(confirmFinish) {
        AlertDialog(
            onDismissRequest={confirmFinish=false},
            title={Text(text(R.string.exam_finish_confirm_title))},
            text={Text(text(R.string.exam_finish_confirm,unanswered))},
            confirmButton={
                TextButton(onClick={confirmFinish=false;finish()}) {
                    Text(text(R.string.exam_finish))
                }
            },
            dismissButton={
                TextButton(onClick={confirmFinish=false}) {
                    Text(text(R.string.cancel))
                }
            }
        )
    }
}
