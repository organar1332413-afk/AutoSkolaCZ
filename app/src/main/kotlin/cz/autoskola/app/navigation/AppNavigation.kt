package cz.autoskola.app.navigation
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import cz.autoskola.app.*
import cz.autoskola.app.R
import cz.autoskola.app.feature.home.HomeScreen
import cz.autoskola.app.feature.learn.*
import cz.autoskola.app.feature.exam.ExamScreen
import cz.autoskola.app.feature.profile.ProfileScreen
import cz.autoskola.app.feature.questions.*
import cz.autoskola.app.feature.words.WordsScreen
import cz.autoskola.app.feature.catalog.*
import cz.autoskola.app.feature.statistics.StatisticsScreen
import cz.autoskola.app.feature.onboarding.OnboardingScreen
import cz.autoskola.app.ui.*
import cz.autoskola.domain.*

private data class Tab(val route:String,val title:Int,val icon:ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppNavigation(vm:MainViewModel,settings:UserSettings) {
    val ready by vm.settingsReady.collectAsStateWithLifecycle()
    val error by vm.operationError.collectAsStateWithLifecycle()
    if(error) AlertDialog(
        onDismissRequest={vm.operationError.value=false},
        text={Text(text(R.string.operation_error))},
        confirmButton={TextButton(onClick={vm.operationError.value=false}) { Text(text(R.string.close)) }}
    )
    if(!ready) {
        Box(Modifier.safeDrawingPadding().padding(24.dp)) { CircularProgressIndicator() }
        return
    }
    if(!settings.onboardingCompleted) {
        OnboardingScreen(settings,vm::onboarding)
        return
    }

    SpeechProvider {
        val speech=LocalCzechSpeech.current
        LaunchedEffect(settings.level) { if(settings.level==LearningLevel.EXAM) speech?.stop() }

        val nav=rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route=entry?.destination?.route ?: "home"
        val status by vm.status.collectAsStateWithLifecycle()
        val questions by vm.questions.collectAsStateWithLifecycle()
        val loadedWords by vm.words.collectAsStateWithLifecycle()
        val loadedLessons by vm.lessons.collectAsStateWithLifecycle()
        val learning by vm.learning.collectAsStateWithLifecycle()
        val examState by vm.exam.collectAsStateWithLifecycle()
        val examAvailability by vm.examAvailability.collectAsStateWithLifecycle()
        val words=loadedWords.filter { it.locale==(settings.materialMode.translationTag ?: "cs") }
        val lessons=loadedLessons.map { l->
            if(l.locale==settings.materialMode.translationTag) l
            else l.copy(title=null,blocks=l.blocks.map { it.copy(translation=null) })
        }
        val load by vm.loadState.collectAsStateWithLifecycle()

        val tabs=listOf(
            Tab("home",R.string.home,Icons.Default.Home),
            Tab("questions",R.string.questions,Icons.AutoMirrored.Filled.List),
            Tab("exam",R.string.exam,Icons.Default.CheckCircle),
            Tab("learn",R.string.learn,Icons.Default.Star)
        )
        val topLevelRoutes=tabs.map { it.route }.toSet()
        val examRunning=route=="exam" && examState.session?.completedAt==null && examState.session!=null
        val open:(String)->Unit={destination->nav.navigate(destination) { launchSingleTop=true }}

        Scaffold(
            topBar={
                TopAppBar(
                    title={Text("Autoškola CZ",style=MaterialTheme.typography.titleLarge)},
                    navigationIcon={
                        if(route !in topLevelRoutes) {
                            TextButton(onClick={nav.popBackStack()}) { Text(text(R.string.back)) }
                        }
                    },
                    actions={
                        if(route!="profile" && !examRunning) {
                            IconButton(onClick={open("profile")}) {
                                Icon(Icons.Default.Settings,contentDescription=text(R.string.profile))
                            }
                        }
                    }
                )
            },
            bottomBar={
                if(route in topLevelRoutes && !examRunning) {
                    NavigationBar {
                        tabs.forEach { tab->
                            NavigationBarItem(
                                selected=route==tab.route,
                                enabled=load==LoadState.READY,
                                onClick={
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState=true }
                                        launchSingleTop=true
                                        restoreState=true
                                    }
                                },
                                icon={Icon(tab.icon,null)},
                                label={Text(text(tab.title))}
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when(load) {
                    LoadState.LOADING->Column(Modifier.padding(24.dp)) {
                        CircularProgressIndicator()
                        Text(text(R.string.loading))
                    }
                    LoadState.ERROR->Column(Modifier.padding(24.dp)) {
                        Text(text(R.string.load_error))
                        Button(onClick=vm::initialize) { Text(text(R.string.retry)) }
                    }
                    LoadState.READY->NavHost(navController=nav,startDestination="home") {
                        composable("home") { HomeScreen(settings,questions,status,lessons,learning,words,open) }
                        composable("questions") { QuestionsScreen(questions,status,learning,QuestionFilter.ALL) { open("question/$it") } }
                        composable("exam") {
                            ExamScreen(
                                settings,
                                status,
                                examState,
                                examAvailability,
                                vm::startExam,
                                vm::examAnswer,
                                vm::finishExam,
                                vm::refreshExam,
                                open
                            )
                        }
                        composable("learn") { LearnScreen(lessons,settings,open) }

                        composable("words") { WordsScreen(words,true,vm::saveWord,vm::removeWord,vm::wordReview) }
                        composable("czech") { WordsScreen(words,false,vm::saveWord,vm::removeWord,vm::wordReview) }
                        composable("profile") {
                            ProfileScreen(settings,status,vm::ui,vm::material,vm::level,vm::licenceGroup,open) {
                                BuildExtras(vm,settings,status,open)
                            }
                        }
                        listOf(
                            "mistakes" to QuestionFilter.MISTAKES,
                            "doubtful" to QuestionFilter.DOUBTFUL,
                            "unknown" to QuestionFilter.UNKNOWN,
                            "known" to QuestionFilter.KNOWN,
                            "favorites" to QuestionFilter.FAVORITES,
                            "unseen" to QuestionFilter.UNSEEN
                        ).forEach { (path,filter)->
                            composable(path) { QuestionsScreen(questions,status,learning,filter) { open("question/$it") } }
                        }
                        composable("question/{id}") { back->
                            val index=questions.indexOfFirst { it.officialId==back.arguments?.getString("id") }
                            val move:(Int)->Unit={i->
                                nav.navigate("question/${questions[i].officialId}") {
                                    popUpTo(back.destination.id) { inclusive=true }
                                    launchSingleTop=true
                                }
                            }
                            QuestionScreen(
                                questions.getOrNull(index),
                                status,
                                settings,
                                words,
                                learning,
                                vm::saveWord,
                                vm::saveUnknownWord,
                                vm::favorite,
                                vm::assessment,
                                vm::answer,
                                vm::reason,
                                if(index>0) ({move(index-1)}) else null,
                                if(index>=0 && index<questions.lastIndex) ({move(index+1)}) else null
                            )
                        }
                        composable("topic/{index}") { back->
                            TopicScreen(back.arguments?.getString("index")?.toIntOrNull() ?: 0,lessons,open)
                        }
                        composable("lesson/{id}") { back->
                            val id=back.arguments?.getString("id")
                            LessonScreen(
                                lessons.find { it.id==id },
                                settings,
                                learning.progress.find { it.lessonId==id },
                                words,
                                vm::saveWord,
                                vm::saveUnknownWord,
                                vm::progress,
                                open
                            )
                        }
                        composable("signs") {
                            SignsScreen(
                                settings.materialMode.translationTag,
                                questions.mapTo(mutableSetOf()) { it.officialId },
                            ) { open("question/$it") }
                        }
                        composable("first_aid") { FirstAidScreen(open) }
                        composable("aid_questions") {
                            QuestionsScreen(questions.filter { it.category=="first_aid" },status,learning) { open("question/$it") }
                        }
                        composable("statistics") { StatisticsScreen(learning,questions,status,words,settings.licenceGroup) }
                    }
                }
            }
        }
    }
}
