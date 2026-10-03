package cz.autoskola.app
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.autoskola.domain.*
import cz.autoskola.app.feature.catalog.SignCatalog
import cz.autoskola.app.feature.catalog.SignCatalogLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

enum class LoadState { LOADING, READY, ERROR }

data class ExamUiState(
    val id:String?=null,
    val session:ExamSession?=null,
    val questions:List<QuestionCard> = emptyList(),
    val busy:Boolean=false
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(internal val container: AppContainer) : ViewModel() {
    val loadState = MutableStateFlow(LoadState.LOADING)
    val operationError = MutableStateFlow(false)
    val dictionaryState = container.dictionary.state
    val settingsReady = MutableStateFlow(false)
    val settings = container.settings.settings
        .onEach { settingsReady.value=true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings(uiLanguage=UiLanguage.forTag(Locale.getDefault().language)))
    val status = container.study.status().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val examAvailability = combine(settings,status) { current, content -> current.licenceGroup to content }
        .mapLatest { (group, content) ->
            if(content==null) ExamAvailability.CONTENT_INCOMPLETE else container.exams.availability(group)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExamAvailability.CONTENT_INCOMPLETE)
    val questions = combine(settings, status) { current, content -> current to content }.flatMapLatest { (current, content) ->
        container.study.questions(current.materialMode.translationTag).map { cards ->
            cards.filter { current.licenceGroup.code in it.licenceGroups || (content?.sample == true && it.licenceGroups.isEmpty()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val words = settings.flatMapLatest {
        container.study.words(it.materialMode.translationTag ?: "cs")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lessons = settings.flatMapLatest {
        container.learning.lessons(it.materialMode.translationTag)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val learning = combine(container.learning.snapshot, container.assessments.assessments) { snapshot, assessments ->
        snapshot.copy(assessments=assessments)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LearningSnapshot())
    private val signsReload = MutableStateFlow(0)
    val signCatalog = signsReload.flatMapLatest {
        flow {
            emit(SignCatalogLoadState())
            emit(SignCatalogLoadState(entries = SignCatalog.load(container.application), loading = false))
        }.catch { e ->
            if(e is CancellationException) throw e
            emit(SignCatalogLoadState(loading = false, failed = true))
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Lazily, SignCatalogLoadState())
    val signProgress = container.settings.signs.progress
        .catch { e ->
            if(e is CancellationException) throw e
            operationError.value = true
            emit(SignProgress())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SignProgress())
    val lookupTipSeen = container.settings.lookupTipSeen
        .catch { e -> if(e is CancellationException) throw e; emit(false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun dismissLookupTip() = update { container.settings.dismissLookupTip() }
    val exam = MutableStateFlow(ExamUiState())
    val sessionStartedAt = System.currentTimeMillis()

    init { initialize() }

    fun initialize() { viewModelScope.launch {
        loadState.value=LoadState.LOADING
        // Learning words must load in every variant, independently of debug exam samples.
        try { container.dictionary.initialize() }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { android.util.Log.e("BundledDictionary", "Cannot initialize offline dictionary", e) }
        try {
            container.bootstrap.initialize()
            initializeBuildContent(container)
            restoreExam()
            loadState.value=LoadState.READY
        } catch(e:CancellationException) {
            throw e
        } catch(e:Exception) {
            loadState.value=LoadState.ERROR
        }
    } }

    internal fun update(block:suspend ()->Unit) { viewModelScope.launch {
        try { block() }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { operationError.value=true }
    } }

    private suspend fun setExam(value:Pair<String,ExamSession>) {
        val cards=container.exams.cards(value.second)
        exam.value=ExamUiState(value.first,value.second,cards,false)
    }

    private suspend fun restoreExam() {
        val value=container.exams.unfinished() ?: return
        setExam(value)
    }

    private fun examAction(block:suspend ()->Unit) {
        if(exam.value.busy) return
        exam.value=exam.value.copy(busy=true)
        viewModelScope.launch {
            try { block() }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { operationError.value=true }
            finally { exam.value=exam.value.copy(busy=false) }
        }
    }

    fun reloadSigns() { signsReload.value++ }
    fun viewSign(code:String)=update { container.settings.signs.markViewed(code) }
    fun favoriteSign(code:String,value:Boolean)=update { container.settings.signs.setFavorite(code,value) }

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
    fun assessment(id:String,v:QuestionAssessment)=update { container.assessments.set(id,v) }
    fun progress(id:String,position:Int,complete:Boolean)=update { container.learning.progress(id,position,complete) }

    fun answer(id:String,revision:String,code:String,done:(Boolean?)->Unit) { viewModelScope.launch {
        try { done(container.learning.answer(id,revision,code,settings.value,sessionStartedAt)) }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { operationError.value=true;done(null) }
    } }

    fun startExam()=examAction {
        setExam(container.exams.start(settings.value.licenceGroup))
    }

    fun examAnswer(revision:String,code:String)=examAction {
        val id=requireNotNull(exam.value.id)
        val updated=container.exams.answer(id,revision,code)
        exam.value=exam.value.copy(session=updated)
    }

    fun finishExam()=examAction {
        val id=requireNotNull(exam.value.id)
        val updated=container.exams.finish(id)
        exam.value=exam.value.copy(session=updated)
    }

    fun refreshExam()=examAction {
        val id=exam.value.id ?: return@examAction
        val current=exam.value.session ?: return@examAction
        if(current.completedAt!=null) return@examAction
        val updated=container.exams.resume(id)
        exam.value=exam.value.copy(session=updated)
    }

    class Factory(private val container:AppContainer):ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T:ViewModel> create(modelClass:Class<T>):T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(container) as T
        }
    }
}
