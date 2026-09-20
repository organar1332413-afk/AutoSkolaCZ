package cz.autoskola.app.feature.questions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import java.util.UUID
@Composable fun filterLabel(filter:QuestionFilter)=text(when(filter) { QuestionFilter.ALL->R.string.filter_all;QuestionFilter.MISTAKES->R.string.mistakes;QuestionFilter.FAVORITES->R.string.favorites;QuestionFilter.UNSEEN->R.string.filter_unseen })
@Composable fun categoryLabel(category:String)=text(when(category) { "rules"->R.string.rules;"safe_driving"->R.string.topic_15;"signs"->R.string.signs;"situations"->R.string.topic_2;"vehicle"->R.string.topic_16;"first_aid"->R.string.first_aid;else->R.string.topic_18 })
@Composable fun reasonLabel(reason:ErrorReason)=text(when(reason) { ErrorReason.RULE_UNKNOWN->R.string.reason_rule;ErrorReason.CZECH_UNCLEAR->R.string.reason_czech;ErrorReason.SIGN_CONFUSED->R.string.reason_sign;ErrorReason.INATTENTION->R.string.reason_attention })
@Composable fun QuestionsScreen(questions:List<QuestionCard>,status:ContentStatus?,learning:LearningSnapshot,initial:QuestionFilter=QuestionFilter.ALL,open:(String)->Unit) {
    var filter by rememberSaveable(initial) { mutableStateOf(initial) }
    val displayed=questions.filter { learning.matches(it,filter) }
    Page {
        item { Heading(text(R.string.questions)) }
        if(status?.sample==true) item { Note(text(R.string.sample_notice,questions.size)) }
        item { Column { QuestionFilter.entries.forEach { f->Choice(filterLabel(f),filter==f) { filter=f } } } }
        if(displayed.isEmpty()) item { Note(text(when(filter) { QuestionFilter.MISTAKES->R.string.no_mistakes;QuestionFilter.FAVORITES->R.string.no_favorites;else->R.string.empty_questions })) }
        items(displayed,key={it.revisionId}) { q -> Entry(q.textCs,"${q.officialId} · ${categoryLabel(q.category)} · ${text(if(learning.attempts.any { it.revisionId==q.revisionId }) R.string.question_status_seen else R.string.question_status_new)}") { open(q.officialId) } }
    }
}
@Composable fun QuestionScreen(question:QuestionCard?,sample:Boolean,settings:UserSettings,words:List<Lexeme>,learning:LearningSnapshot,save:(String)->Unit,saveUnknown:(String)->Unit,favorite:(String,Boolean)->Unit,answer:(String,String,String,(Boolean?)->Unit)->Unit,reason:(String,ErrorReason)->Unit,previous:(()->Unit)?,next:(()->Unit)?) {
    if(question==null) { Page { item { Note(text(R.string.empty_questions)) } };return }
    var reveal by rememberSaveable(question.revisionId,settings.materialMode,settings.level) { mutableStateOf(false) }
    var attemptId by rememberSaveable(question.revisionId) { mutableStateOf(UUID.randomUUID().toString()) }
    var selected by rememberSaveable(question.revisionId,attemptId) { mutableStateOf<String?>(null) }
    var correct by rememberSaveable(question.revisionId,attemptId) { mutableStateOf<Boolean?>(null) }
    var busy by remember(question.revisionId,attemptId) { mutableStateOf(false) }
    var selectedReason by rememberSaveable(question.revisionId,attemptId) { mutableStateOf<ErrorReason?>(null) }
    var word by rememberSaveable(question.revisionId,settings.materialMode,settings.level) { mutableStateOf<String?>(null) }
    var phrase by rememberSaveable(question.revisionId,settings.materialMode,settings.level) { mutableStateOf(false) }
    val persisted=learning.attempts.find { it.id==attemptId }
    LaunchedEffect(persisted) { if(persisted!=null) { selected=persisted.answerCode;correct=persisted.correct;selectedReason=persisted.reason;busy=false } }
    val policy=settings.policy(revealed=reveal)
    val translation=question.translation?.takeIf { it.locale==policy.translationTag }
    val noHints=settings.level==LearningLevel.EXAM
    val onWord:(String)->Unit={word=it}
    Page {
        item { Note("${text(if(sample) R.string.question_sample else R.string.question_official)} · ${question.officialId} · ${categoryLabel(question.category)}") }
        item { CzechText(question.textCs,policy.canLookup,onWord) }
        if(!noHints) item { SpeechButtons(question.textCs) }
        if(policy.canReveal && !reveal) item { TextButton(onClick={reveal=true}) { Text(text(R.string.show_translation)) } }
        if(policy.showTranslation) item { Note(translation?.text ?: text(R.string.translation_missing)) }
        items(question.media.filter { it.answerCode==null },key={it.path}) { LocalMedia(it) }
        items(question.answers,key={it.code}) { option ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                CzechText(option.textCs,policy.canLookup,onWord)
                question.media.filter { it.answerCode==option.code }.forEach { LocalMedia(it) }
                if(policy.showTranslation) Note(translation?.answers?.get(option.code) ?: text(R.string.translation_missing))
                if(!noHints) SpeechButtons(option.textCs)
                OutlinedButton(onClick={busy=true;val pendingId=attemptId;answer(pendingId,question.revisionId,option.code) { result->if(pendingId==attemptId) { busy=false;if(result!=null) { selected=option.code;correct=result } } } },enabled=selected==null && !busy) { Text(if(selected==option.code) "✓ ${option.code}" else option.code) }
            } }
        }
        if(correct!=null && !noHints) {
            item { Text(text(if(correct==true) R.string.correct else R.string.incorrect),color=if(correct==true) CorrectColor else IncorrectColor) }
            item { Text(text(R.string.correct_answer,question.answers.single { it.correct }.code)) }
            if(policy.showTranslation) item { Note(translation?.explanation ?: text(R.string.translation_missing)) }
            if(correct==false) {
                item { Text(text(R.string.why_error)) }
                item { Column { ErrorReason.entries.forEach { r->Choice(reasonLabel(r),selectedReason==r) { selectedReason=r;reason(attemptId,r) } } } }
            }
        }
        if(selected!=null) item { TextButton(onClick={attemptId=UUID.randomUUID().toString()}) { Text(text(R.string.answer_again)) } }
        if(noHints) item { Note(text(R.string.no_hints_check)) }
        item { Note(question.points?.let { text(R.string.question_points,it) } ?: text(R.string.points_pending)) }
        item { val streak=learning.reviews[question.officialId]?.takeIf { it.revisionId==question.revisionId }?.streak ?: 0;Note(text(R.string.mastery,streak));Note(text(R.string.mastery_note)) }
        item { TextButton(onClick={favorite(question.officialId,question.officialId !in learning.favorites)}) { Text(text(if(question.officialId in learning.favorites) R.string.favorite_remove else R.string.favorite_add)) } }
        if(policy.canLookup) item { TextButton(onClick={phrase=!phrase}) { Text(text(R.string.phrase)) } }
        if(phrase && policy.canLookup) item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { PhraseAnalysis(question.textCs,words,save) } }
        if(policy.showTranslation) item { Note(text(R.string.draft_translation)) }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            OutlinedButton(onClick={previous?.invoke()},enabled=previous!=null) { Text(text(R.string.previous)) }
            Button(onClick={next?.invoke()},enabled=next!=null) { Text(text(R.string.next)) }
        } }
    }
    if(policy.canLookup) DictionarySheet(word,words,save,saveUnknown) { word=null }
}
