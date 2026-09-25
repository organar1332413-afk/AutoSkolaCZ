package cz.autoskola.app.feature.onboarding
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cz.autoskola.app.R
import cz.autoskola.app.feature.profile.licenceGroupLabel
import cz.autoskola.app.ui.*
import cz.autoskola.domain.*
import java.util.Locale

@Composable fun OnboardingScreen(initial:UserSettings,finish:(UserSettings)->Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var ui by rememberSaveable { mutableStateOf(initial.uiLanguage) }
    var group by rememberSaveable { mutableStateOf(initial.licenceGroup) }
    var mode by rememberSaveable { mutableStateOf(initial.materialMode) }
    var level by rememberSaveable { mutableStateOf(initial.level) }
    val base=LocalContext.current
    val configuration=LocalConfiguration.current
    val localized=remember(base,configuration,ui) { base.createConfigurationContext(Configuration(configuration).apply { setLocale(Locale.forLanguageTag(ui.tag)) }) }
    CompositionLocalProvider(LocalContext provides localized) {
        Surface(Modifier.fillMaxSize()) { Box(Modifier.safeDrawingPadding()) { Page {
            item { Heading("Autoškola CZ") }
            item { Note("${step+1} / 3") }

            when(step) {
                0 -> {
                    item { Heading(text(R.string.onboarding_language_title)) }
                    item { Note(text(R.string.onboarding_intro)) }
                    UiLanguage.entries.forEach { language -> item {
                        Choice(language.nativeName,ui==language) { ui=language }
                    } }
                }
                1 -> {
                    item { Heading(text(R.string.onboarding_category_title)) }
                    LicenceGroup.entries.forEach { licence -> item {
                        Choice(licenceGroupLabel(licence),group==licence) { group=licence }
                    } }
                }
                else -> {
                    item { Heading(text(R.string.onboarding_help_title)) }
                    item { Note(text(R.string.onboarding_support)) }
                    item { Choice(text(R.string.support_full),level==LearningLevel.BEGINNER) {
                        mode=ui.defaultMaterialMode();level=LearningLevel.BEGINNER
                    } }
                    item { Choice(text(R.string.support_on_demand),level==LearningLevel.INTERMEDIATE) {
                        mode=ui.defaultMaterialMode();level=LearningLevel.INTERMEDIATE
                    } }
                    item { Choice(text(R.string.support_exam),level==LearningLevel.EXAM) {
                        mode=ui.defaultMaterialMode();level=LearningLevel.EXAM
                    } }
                    item { Note(text(R.string.cs_only_note)) }
                    item { Note(text(R.string.privacy)) }
                }
            }

            item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(step>0) OutlinedButton(onClick={step--}) { Text(text(R.string.back)) }
                Button(onClick={
                    if(step<2) step++ else finish(UserSettings(ui,mode,level,true,group))
                }) { Text(text(if(step<2) R.string.next else R.string.onboarding_finish)) }
            } }
        } } }
    }
}
