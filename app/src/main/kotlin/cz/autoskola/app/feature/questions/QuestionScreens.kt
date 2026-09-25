package cz.autoskola.app.feature.questions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.app.feature.words.*
import cz.autoskola.design.CorrectColor
import cz.autoskola.design.IncorrectColor
import cz.autoskola.domain.*
import java.util.Locale
import java.util.UUID

@Composable fun filterLabel(filter:QuestionFilter)=text(when(filter) {
    QuestionFilter.ALL->R.string.filter_all
    QuestionFilter.MISTAKES->R.string.mistakes
    QuestionFilter.DOUBTFUL->R.string.filter_doubtful
    QuestionFilter.UNKNOWN->R.string.filter_unknown
    QuestionFilter.KNOWN->R.string.filter_known
    QuestionFilter.FAVORITES->R.string.favorites
    QuestionFilter.UNSEEN->R.string.filter_unseen
})
@Composable fun categoryLabel(category:String)=text(when(category) {
    "rules"->R.string.rules
    "safe_driving"->R.string.topic_15
    "signs"->R.string.signs
    "situations"->R.string.topic_2
    "vehicle"->R.string.topic_16
    "first_aid"->R.string.first_aid
    else->R.string.topic_18
})
@Composable fun reasonLabel(reason:ErrorReason)=text(when(reason) {
    ErrorReason.RULE_UNKNOWN->R.string.reason_rule
    ErrorReason.CZECH_UNCLEAR->R.string.reason_czech
    ErrorReason.SIGN_CONFUSED->R.string.reason_sign
    ErrorReason.INATTENTION->R.string.reason_attention
})

private fun QuestionCard.matchesSearch(raw:String):Boolean {
    val query=raw.trim().lowercase(Locale.ROOT)
    if(query.isEmpty()) return true
    val haystack=buildString {
        append(officialId).append(' ')
        append(textCs).append(' ')
        answers.forEach { append(it.textCs).append(' ') }
        translation?.let { t ->
            append(t.text).append(' ')
            append(t.explanation).append(' ')
            t.answers.values.forEach { append(it).append(' ') }
        }
    }.lowercase(Locale.ROOT)
    return query in haystack
}

@Composable fun QuestionsScreen(
    questions:List<QuestionCard>,
    status:ContentStatus?,
    learning:LearningSnapshot,
    initial:QuestionFilter=QuestionFilter.ALL,
    open:(String)->Unit
) {
    var filter by rememberSaveable(initial) { mutableStateOf(initial) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val categoryCounts=questions.groupingBy { it.category }.eachCount().toSortedMap()
    val displayed=questions.asSequence()
        .filter { learning.matches(it,filter) }
        .filter { category==null || it.category==category }
        .filter { it.matchesSearch(query) }
        .toList()

    Page {
        item { Heading(text(R.string.questions)) }
        if(status?.sample==true) item { Note(text(R.string.sample_notice,questions.size)) }

        item {
            OutlinedTextField(
                value=query,
                onValueChange={query=it},
                modifier=Modifier.fillMaxWidth(),
                singleLine=true,
                label={Text(text(R.string.search_questions))}
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                QuestionFilter.entries.forEach { f->
                    FilterChip(
                        selected=filter==f,
                        onClick={filter=f},
                        label={Text(filterLabel(f))}
                    )
                }
            }
        }

        item { Text(text(R.string.topics),style=MaterialTheme.typography.titleMedium) }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected=category==null,
                    onClick={category=null},
                    label={Text(text(R.string.all_topics))}
                )
                categoryCounts.forEach { (key,count)->
                    FilterChip(
                        selected=category==key,
                        onClick={category=key},
                        label={Text(categoryLabel(key) + " · " + count)}
                    )
                }
            }
        }

        item { Note(text(R.string.questions_found,displayed.size,questions.size)) }

        if(displayed.isEmpty()) item {
            Note(
                if(query.isNotBlank()) text(R.string.search_empty)
                else text(when(filter) {
                    QuestionFilter.MISTAKES->R.string.no_mistakes
                    QuestionFilter.FAVORITES->R.string.no_favorites
                    else->R.string.empty_questions
                })
            )
        }

        items(displayed,key={it.revisionId}) { q ->
            val state=when {
                learning.isKnown(q)->R.string.filter_known
                learning.isMistake(q)->R.string.mistakes
                learning.isDoubtful(q)->R.string.filter_doubtful
                learning.isUnknown(q)->R.string.filter_unknown
                else->if(learning.attempts.any { it.revisionId==q.revisionId }) R.string.question_status_seen else R.string.question_status_new
            }
            Entry(
                q.textCs,
                q.officialId + " · " + categoryLabel(q.category) + " · " + text(state)
            ) { open(q.officialId) }
        }
    }
}
@Composable fun QuestionScreen(
    question:QuestionCard?,
    status:ContentStatus?,
    settings:UserSettings,
    words:List<Lexeme>,
    learning:LearningSnapshot,
    save:(String)->Unit,
    saveUnknown:(String)->Unit,
    favorite:(String,Boolean)->Unit,
    assess:(String,QuestionAssessment)->Unit,
    answer:(String,String,String,(Boolean?)->Unit)->Unit,
    reason:(String,ErrorReason)->Unit,
    previous:(()->Unit)?,
    next:(()->Unit)?
) {
    if(question==null) { Page { item { Note(text(R.string.empty_questions)) } };return }

    val sample=status?.sample!=false
    var reveal by rememberSaveable(question.revisionId,settings.materialMode,settings.level) { mutableStateOf(false) }
    var attemptId by rememberSaveable(question.revisionId) { mutableStateOf(UUID.randomUUID().toString()) }
    var selected by rememberSaveable(question.revisionId,attemptId) { mutableStateOf<String?>(null) }
    var correct by rememberSaveable(question.revisionId,attemptId) { mutableStateOf<Boolean?>(null) }
    var busy by remember(question.revisionId,attemptId) { mutableStateOf(false) }
    var selectedReason by rememberSaveable(question.revisionId,attemptId) { mutableStateOf<ErrorReason?>(null) }
    var word by rememberSaveable(question.revisionId,settings.materialMode,settings.level) { mutableStateOf<String?>(null) }
    var phrase by rememberSaveable(question.revisionId,settings.materialMode,settings.level) { mutableStateOf(false) }

    val persisted=learning.attempts.find { it.id==attemptId }
    LaunchedEffect(persisted) {
        if(persisted!=null) {
            selected=persisted.answerCode
            correct=persisted.correct
            selectedReason=persisted.reason
            busy=false
        }
    }

    val policy=settings.policy(revealed=reveal)
    val translation=question.translation?.takeIf { it.locale==policy.translationTag }
    val noHints=settings.level==LearningLevel.EXAM
    val selectedAssessment=learning.assessments[question.officialId]
    val onWord:(String)->Unit={word=it}

    Page {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(text(if(sample) R.string.question_sample else R.string.question_official),style=MaterialTheme.typography.labelLarge)
                    Note(question.officialId + " · " + categoryLabel(question.category))
                    Note(question.points?.let { text(R.string.question_points,it) } ?: text(R.string.points_pending))
                }
            }
        }

        item { CzechText(question.textCs,policy.canLookup,onWord,prominent=true) }
        if(policy.canLookup) item { Note(text(R.string.tap_word_hint)) }
        if(!noHints) item { SpeechButtons(question.textCs) }

        if(policy.canReveal && !reveal) item {
            OutlinedButton(onClick={reveal=true},modifier=Modifier.fillMaxWidth()) {
                Text(text(R.string.show_translation))
            }
        }

        if(policy.showTranslation) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(text(R.string.translation_title),style=MaterialTheme.typography.titleMedium)
                    Note(translation?.text ?: text(R.string.translation_missing))
                }
            }
        }

        items(question.media.filter { it.answerCode==null },key={it.path}) { LocalMedia(it) }

        items(question.answers,key={it.code}) { option ->
            val showResult=correct!=null && !noHints
            val chosen=selected==option.code
            val label=when {
                showResult && option.correct -> "✓ " + option.code
                showResult && chosen -> "✕ " + option.code
                chosen -> "• " + option.code
                else -> option.code
            }
            OutlinedCard(
                onClick={
                    if(selected==null && !busy) {
                        busy=true
                        val pendingId=attemptId
                        answer(pendingId,question.revisionId,option.code) { result->
                            if(pendingId==attemptId) {
                                busy=false
                                if(result!=null) {
                                    selected=option.code
                                    correct=result
                                }
                            }
                        }
                    }
                },
                modifier=Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(label,style=MaterialTheme.typography.titleMedium)
                    CzechText(option.textCs,policy.canLookup,onWord)
                    question.media.filter { it.answerCode==option.code }.forEach { LocalMedia(it) }
                    if(policy.showTranslation) Note(translation?.answers?.get(option.code) ?: text(R.string.translation_missing))
                    if(!noHints) SpeechButtons(option.textCs)
                    if(showResult && (option.correct || chosen)) {
                        Text(
                            text(if(option.correct) R.string.correct else R.string.incorrect),
                            color=if(option.correct) CorrectColor else IncorrectColor
                        )
                    }
                }
            }
        }

        if(correct!=null && !noHints) {
            item {
                Text(
                    text(if(correct==true) R.string.correct else R.string.incorrect),
                    style=MaterialTheme.typography.titleLarge,
                    color=if(correct==true) CorrectColor else IncorrectColor
                )
            }
            item { Text(text(R.string.correct_answer,question.answers.single { it.correct }.code)) }

            if(policy.showTranslation) item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(text(R.string.explanation_title),style=MaterialTheme.typography.titleMedium)
                        Note(translation?.explanation?.takeIf { it.isNotBlank() } ?: text(R.string.translation_missing))
                    }
                }
            }

            if(correct==false) {
                item { Text(text(R.string.why_error),style=MaterialTheme.typography.titleMedium) }
                item {
                    Column {
                        ErrorReason.entries.forEach { r->
                            Choice(reasonLabel(r),selectedReason==r) {
                                selectedReason=r
                                reason(attemptId,r)
                            }
                        }
                    }
                }
            }
        }

        if(selected!=null) item {
            TextButton(onClick={attemptId=UUID.randomUUID().toString()}) {
                Text(text(R.string.answer_again))
            }
        }

        if(noHints) item { Note(text(R.string.no_hints_check)) }

        item { HorizontalDivider() }
        item { Text(text(R.string.self_assessment),style=MaterialTheme.typography.titleMedium) }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                QuestionAssessment.entries.forEach { value->
                    val label=text(when(value) {
                        QuestionAssessment.KNOWN->R.string.filter_known
                        QuestionAssessment.DOUBTFUL->R.string.filter_doubtful
                        QuestionAssessment.UNKNOWN->R.string.filter_unknown
                    })
                    FilterChip(
                        selected=selectedAssessment==value,
                        onClick={assess(question.officialId,value)},
                        label={Text(label)}
                    )
                }
            }
        }

        item {
            TextButton(onClick={favorite(question.officialId,question.officialId !in learning.favorites)}) {
                Text(text(if(question.officialId in learning.favorites) R.string.favorite_remove else R.string.favorite_add))
            }
        }

        if(policy.canLookup) item {
            TextButton(onClick={phrase=!phrase}) { Text(text(R.string.phrase)) }
        }
        if(phrase && policy.canLookup) item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                PhraseAnalysis(question.textCs,words,save)
            }
        }
        if(policy.showTranslation) item { Note(text(R.string.draft_translation)) }

        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(text(R.string.question_details),style=MaterialTheme.typography.titleMedium)
                    question.source?.let { Note(text(R.string.question_source,it)) }
                    status?.databaseVersion?.let { Note(text(R.string.question_database_version,it)) }
                    status?.publicationDate?.let { Note(text(R.string.question_publication_date,it)) }
                    Note(text(R.string.official_original_notice))
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                OutlinedButton(onClick={previous?.invoke()},enabled=previous!=null) { Text(text(R.string.previous)) }
                Button(onClick={next?.invoke()},enabled=next!=null) { Text(text(R.string.next)) }
            }
        }
    }

    if(policy.canLookup) DictionarySheet(word,words,save,saveUnknown) { word=null }
}
