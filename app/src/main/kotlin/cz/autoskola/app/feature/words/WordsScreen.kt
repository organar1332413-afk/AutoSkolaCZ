package cz.autoskola.app.feature.words
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.domain.*
import java.util.Locale
@Composable fun WordCard(word:Lexeme,save:(String)->Unit,remove:((String)->Unit)?=null,showMeaning:Boolean=true) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(word.lemma,style=MaterialTheme.typography.titleLarge)
        SpeechButtons(word.lemma)
        if(showMeaning) {
            word.translation?.let { Note(it) };word.meaning?.let { Note(it) }
            if(word.id.startsWith("user-")) Note(text(R.string.word_unknown))
            if(word.exampleCs.isNotBlank()) Text(word.exampleCs,style=MaterialTheme.typography.bodyLarge)
            word.exampleTranslation?.let { Note(it) }
        }
        if(!word.saved) Button(onClick={save(word.id)}) { Text(text(R.string.save_word)) }
        else if(remove!=null) TextButton(onClick={remove(word.id)}) { Text(text(R.string.word_remove)) }
        else Note(text(R.string.word_saved))
    } }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DictionarySheet(token:String?,words:List<Lexeme>,save:(String)->Unit,saveUnknown:(String)->Unit,dismiss:()->Unit) {
    if(token==null) return
    ModalBottomSheet(onDismissRequest=dismiss) {
        Column(Modifier.padding(20.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val normalized=token.lowercase(Locale.forLanguageTag("cs"))
            val found=words.find { w->w.forms.any { it.lowercase(Locale.forLanguageTag("cs"))==normalized } || w.lemma.lowercase(Locale.forLanguageTag("cs"))==normalized }
            if(found!=null) WordCard(found,save) else { Heading(token);Note(text(R.string.word_unknown));SpeechButtons(token);Button(onClick={saveUnknown(token)}) { Text(text(R.string.save_word)) } }
            Spacer(Modifier.height(24.dp))
        }
    }
}
@Composable fun PhraseAnalysis(value:String,words:List<Lexeme>,save:(String)->Unit) {
    val forms=Regex("[\\p{L}]+(?:[-’'][\\p{L}]+)*").findAll(value).map { it.value.lowercase(Locale.forLanguageTag("cs")) }.toSet()
    val known=words.filter { w->w.forms.any { it.lowercase(Locale.forLanguageTag("cs")) in forms } || w.lemma.lowercase(Locale.forLanguageTag("cs")) in forms }
    Note(text(R.string.phrase_note))
    known.forEach { word -> WordCard(word,save) }
    if(known.isEmpty()) Note(text(R.string.word_unknown))
}
@Composable fun WordsScreen(words:List<Lexeme>,savedOnly:Boolean,save:(String)->Unit,remove:(String)->Unit,review:(String,Boolean)->Unit) {
    val displayed=if(savedOnly) words.filter { it.saved } else words
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var reviewed by rememberSaveable { mutableStateOf(listOf<String>()) }
    val current=displayed.firstOrNull { it.id !in reviewed }
    var revealed by rememberSaveable(current?.id,reviewing) { mutableStateOf(false) }
    Page {
        item { Heading(text(if(savedOnly) R.string.my_words else R.string.czech)) }
        if(displayed.isEmpty()) item { Note(text(R.string.no_words)) }
        if(savedOnly && displayed.isNotEmpty()) item {
            TextButton(onClick={reviewing=!reviewing;reviewed=emptyList()}) { Text(text(if(reviewing) R.string.close else R.string.word_review_title)) }
        }
        if(reviewing) {
            if(current==null) item { Note(text(R.string.review_finish)) }
            else {
                item { WordCard(current,save,showMeaning=revealed) }
                if(!revealed) item { Button(onClick={revealed=true}) { Text(text(R.string.word_reveal)) } }
                else item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick={review(current.id,false);reviewed=reviewed+current.id}) { Text(text(R.string.word_unknown_review)) }
                    Button(onClick={review(current.id,true);reviewed=reviewed+current.id}) { Text(text(R.string.word_known)) }
                } }
            }
        } else items(displayed,key={it.id}) { word ->
            WordCard(word,save,if(savedOnly) remove else null)
            if(savedOnly) Note(text(R.string.word_repetitions,word.repetitions,word.correctCount))
        }
        if(displayed.any { it.translation!=null }) item { Note(text(R.string.draft_translation)) }
    }
}
