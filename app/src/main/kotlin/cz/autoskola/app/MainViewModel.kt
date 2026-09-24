package cz.autoskola.app
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.autoskola.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
enum class LoadState { LOADING, READY, ERROR }
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(internal val container: AppContainer) : ViewModel() {
    val loadState = MutableStateFlow(LoadState.LOADING)
    val operationError = MutableStateFlow(false)
    val settingsReady = MutableStateFlow(false)
    val settings = container.settings.settings.onEach { settingsReady.value=true }.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings(uiLanguage=UiLanguage.forTag(Locale.getDefault().language)))
    val status = container.study.status().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val questions = settings.flatMapLatest { current ->
        container.study.questions(current.materialMode.translationTag).map { cards ->
            cards.filter { it.licenceGroups.isEmpty() || current.licenceGroup.code in it.licenceGroups }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val words = settings.flatMapLatest { container.study.words(it.materialMode.translationTag ?: "cs") }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lessons = settings.flatMapLatest { container.learning.lessons(it.materialMode.translationTag) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val learning = container.learning.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LearningSnapshot())
    val sessionStartedAt = System.currentTimeMillis()
    init { initialize() }
    fun initialize() { viewModelScope.launch {
        loadState.value=LoadState.LOADING
        try { container.bootstrap.initialize(); initializeBuildContent(container); loadState.value=LoadState.READY }
        catch(e:CancellationException) { throw e } catch(e:Exception) { loadState.value=LoadState.ERROR }
    } }
    internal fun update(block:suspend ()->Unit) { viewModelScope.launch {
        try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { operationError.value=true }
    } }
    fun ui(v:UiLanguage)=update { container.settings.setUiLanguage(v) }
    fun material(v:MaterialMode)=update { container.settings.setMaterialMode(v) }
    fun level(v:LearningLevel)=update { container.settings.setLevel(v) }
    fun licenceGroup(v:LicenceGroup)=update { container.settings.setLicenceGroup(v) }
    fun onboarding(v:UserSettings)=update { container.settings.completeOnboarding(v) }
    fun saveWord(id:String)=update { container.study.saveWord(id) }
    fun saveUnknownWord(token:String)=update { container.study.saveUnknownWord(token) }
    fun removeWord(id:String)=update { container.learning.removeWord(id) }
    fun wordReview(id:String,correct:Boolean)=update { container.learning.wordReview(id,correct) }
    fun favorite(id:String,v:Boolean)=update { container.learning.favorite(id,v) }
    fun reason(id:String,v:ErrorReason)=update { container.learning.reason(id,v) }
    fun progress(id:String,position:Int,complete:Boolean)=update { container.learning.progress(id,position,complete) }
    fun answer(id:String,revision:String,code:String,done:(Boolean?)->Unit) { viewModelScope.launch {
        try { done(container.learning.answer(id,revision,code,settings.value,sessionStartedAt)) }
        catch(e:CancellationException) { throw e } catch(e:Exception) { operationError.value=true;done(null) }
    } }
    class Factory(private val container:AppContainer):ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T { require(modelClass.isAssignableFrom(MainViewModel::class.java));return MainViewModel(container) as T }
    }
}
