package cz.autoskola.app.feature.learn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.app.feature.words.*
import cz.autoskola.domain.*

val topicTitles = listOf(
    R.string.topic_0, R.string.topic_1, R.string.topic_2, R.string.topic_3, R.string.topic_4,
    R.string.topic_5, R.string.topic_6, R.string.topic_7, R.string.topic_8, R.string.topic_9,
    R.string.topic_10, R.string.topic_11, R.string.topic_12, R.string.topic_13, R.string.topic_14,
    R.string.topic_15, R.string.topic_16, R.string.topic_17, R.string.topic_18
)

@Composable fun LearnScreen(lessons:List<LessonCard>,settings:UserSettings,open:(String)->Unit) {
    Page {
        item { Heading(text(R.string.learn)) }
        item { Note(text(R.string.study_tools)) }

        if(!settings.nativeCzechMode) item { Entry(text(R.string.czech)) { open("czech") } }
        item { Entry(text(if(settings.nativeCzechMode) R.string.review else R.string.my_words)) { open("words") } }
        item { Entry(text(R.string.signs)) { open("signs") } }
        item { Entry(text(R.string.first_aid)) { open("first_aid") } }
        item { Entry(text(R.string.statistics)) { open("statistics") } }

        if(lessons.isNotEmpty()) item { HorizontalDivider() }
        items(lessons,key={it.id}) { lesson->
            Entry(
                lesson.titleCs,
                if(settings.policy().showTranslation) lesson.title else null
            ) { open("lesson/${lesson.id}") }
        }

        item { HorizontalDivider() }
        items(topicTitles.indices.toList()) { index->
            Entry(text(topicTitles[index])) { open("topic/$index") }
        }
    }
}

@Composable fun TopicScreen(index:Int,lessons:List<LessonCard>,open:(String)->Unit) {
    val matching=lessons.filter { it.topic==index.toString() }
    Page {
        item { Heading(text(topicTitles.getOrElse(index) { R.string.learn })) }
        if(matching.isEmpty()) item { Note(text(R.string.content_pending)) }
        items(matching,key={it.id}) { lesson->Entry(lesson.titleCs) { open("lesson/${lesson.id}") } }
    }
}

@Composable fun LessonScreen(
    lesson:LessonCard?,
    settings:UserSettings,
    progress:LessonProgress?,
    words:List<Lexeme>,
    save:(String)->Unit,
    saveUnknown:(String)->Unit,
    record:(String,Int,Boolean)->Unit,
    open:(String)->Unit
) {
    if(lesson==null) {
        Page { item { Note(text(R.string.content_pending)) } }
        return
    }
    var index by rememberSaveable(lesson.id) {
        mutableIntStateOf((progress?.position ?: 0).coerceIn(0,(lesson.blocks.size-1).coerceAtLeast(0)))
    }
    var reveal by rememberSaveable(lesson.id,index,settings.materialMode,settings.level) { mutableStateOf(false) }
    var word by rememberSaveable(lesson.id,index,settings.materialMode,settings.level) { mutableStateOf<String?>(null) }
    var finished by rememberSaveable(lesson.id) { mutableStateOf(progress?.completedAt!=null) }
    var phrase by rememberSaveable(lesson.id,index) { mutableStateOf(false) }
    val policy=settings.policy(revealed=reveal)
    val block=lesson.blocks.getOrNull(index)

    Page {
        item { Heading(lesson.titleCs) }
        if(policy.showTranslation) item { lesson.title?.let { Note(it) } }
        if(lesson.reviewStatus!="reviewed") item {
            Note(text(R.string.lesson_sample))
            Note(text(R.string.lesson_sample_note))
        }
        item { Note(text(R.string.lesson_minutes,lesson.minutes)) }
        if(finished) item { Heading(text(R.string.lesson_result)) }
        if(block!=null) {
            item { Note(text(R.string.lesson_step,index+1,lesson.blocks.size)) }
            item {
                Text(
                    text(when(block.kind) {
                        "rule"->R.string.lesson_rule
                        "source"->R.string.lesson_source
                        "explanation"->R.string.lesson_explanation
                        "words"->R.string.lesson_words
                        "practice"->R.string.lesson_practice
                        "translation"->R.string.lesson_translation
                        "situation"->R.string.lesson_situation
                        "official_questions"->R.string.questions
                        "result"->R.string.lesson_result
                        else->R.string.learn
                    }),
                    style=MaterialTheme.typography.titleMedium
                )
            }
            item { CzechText(block.textCs,policy.canLookup) { word=it } }
            if(settings.level!=LearningLevel.EXAM) item { SpeechButtons(block.textCs) }
            if(policy.canReveal && !reveal) item {
                TextButton(onClick={reveal=true}) { Text(text(R.string.show_translation)) }
            }
            if(policy.showTranslation) item { Note(block.translation ?: text(R.string.translation_missing)) }
            if(block.kind=="situation") item { Note(text(R.string.lesson_media_pending)) }
            if(block.kind=="words" && policy.canLookup) items(words.take(7),key={"lesson-word-${it.id}"}) {
                WordCard(it,save)
            }
            if(block.kind in listOf("practice","official_questions")) items(lesson.questions,key={"lesson-question-$it"}) { id->
                Entry(id,text(R.string.questions)) { open("question/$id") }
            }
            if(policy.canLookup) item {
                TextButton(onClick={phrase=!phrase}) { Text(text(R.string.phrase)) }
            }
            if(phrase && policy.canLookup) item {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { PhraseAnalysis(block.textCs,words,save) }
            }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick={index--;record(lesson.id,index,false);finished=false},
                        enabled=index>0
                    ) { Text(text(R.string.previous)) }
                    Button(onClick={
                        if(index<lesson.blocks.lastIndex) {
                            index++;finished=false;record(lesson.id,index,false)
                        } else {
                            finished=true;record(lesson.id,index,true)
                        }
                    }) {
                        Text(text(if(index<lesson.blocks.lastIndex) R.string.next else R.string.lesson_complete))
                    }
                }
            }
        }
    }
    if(policy.canLookup) DictionarySheet(word,words,save,saveUnknown) { word=null }
}
