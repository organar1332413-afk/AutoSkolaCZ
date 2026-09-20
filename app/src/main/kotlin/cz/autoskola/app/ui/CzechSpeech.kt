package cz.autoskola.app.ui
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cz.autoskola.app.R
import java.util.Locale
import java.util.UUID
/** Offline installed Czech voices only. Missing voice data never triggers a network request. */
class CzechSpeech(context:Context) {
    var ready by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    private var engine:TextToSpeech?=null
    private var disposed=false
    init { engine=TextToSpeech(context.applicationContext) { status ->
        if(!disposed && status==TextToSpeech.SUCCESS) {
            val tts=engine
            val voice=tts?.voices?.firstOrNull { it.locale.language=="cs" && !it.isNetworkConnectionRequired && it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)!=true }
            if(voice!=null) { ready=tts.setVoice(voice)==TextToSpeech.SUCCESS;failed=!ready } else { ready=false;failed=true }
        } else if(!disposed) failed=true
    } }
    fun speak(value:String,slow:Boolean) { if(ready) {
        engine?.setSpeechRate(if(slow) 0.7f else 1f)
        if(engine?.speak(value,TextToSpeech.QUEUE_FLUSH,null,UUID.randomUUID().toString())==TextToSpeech.ERROR) failed=true
    } }
    fun stop() { engine?.stop() }
    fun close() { disposed=true;engine?.stop();engine?.shutdown();engine=null }
}
val LocalCzechSpeech=staticCompositionLocalOf<CzechSpeech?> { null }
@Composable fun SpeechProvider(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val speech=remember(context.applicationContext) { CzechSpeech(context) }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(speech,owner) {
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_STOP) speech.stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);speech.close() }
    }
    CompositionLocalProvider(LocalCzechSpeech provides speech,content=content)
}
@Composable fun SpeechButtons(value:String) {
    val speech=LocalCzechSpeech.current ?: return
    Row { TextButton(onClick={speech.speak(value,false)},enabled=speech.ready) { Text(text(R.string.speak)) };TextButton(onClick={speech.speak(value,true)},enabled=speech.ready) { Text(text(R.string.speak_slow)) } }
    if(speech.failed) Note(text(R.string.tts_unavailable))
}
