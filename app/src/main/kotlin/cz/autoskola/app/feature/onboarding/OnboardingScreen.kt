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
import cz.autoskola.app.feature.profile.*
import cz.autoskola.app.ui.*
import cz.autoskola.domain.*
import java.util.Locale
@Composable fun OnboardingScreen(initial:UserSettings,finish:(UserSettings)->Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var ui by rememberSaveable { mutableStateOf(initial.uiLanguage) }
    var mode by rememberSaveable { mutableStateOf(initial.materialMode) }
    var level by rememberSaveable { mutableStateOf(LearningLevel.BEGINNER) }
    val base=LocalContext.current;val configuration=LocalConfiguration.current
    val localized=remember(base,configuration,ui) { base.createConfigurationContext(Configuration(configuration).apply { setLocale(Locale.forLanguageTag(ui.tag)) }) }
    CompositionLocalProvider(LocalContext provides localized) {
        Surface(Modifier.fillMaxSize()) { Box(Modifier.safeDrawingPadding()) { Page {
            item { Heading("Autoškola CZ") }
            item { Note("${step+1} / 3") }
            if(step==0) {
                item { Heading(text(R.string.onboarding_title)) }
                item { Note(text(R.string.onboarding_intro)) }
                item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) { UiLanguage.entries.forEach { language -> TextButton(onClick={ui=language}) { Text(language.nativeName) } } } }
                MaterialMode.entries.forEach { preset -> item {
                    val title=if(preset==MaterialMode.CS_ONLY) text(R.string.onboarding_cs) else preset.label
                    val detail=text(when(preset) { MaterialMode.CS_ONLY->R.string.onboarding_cs_detail;MaterialMode.CS_RU->R.string.onboarding_ru;MaterialMode.CS_UK->R.string.onboarding_uk })
                    Entry(title,detail) { val defaults=preset.onboardingDefaults();ui=defaults.uiLanguage;mode=defaults.materialMode;level=defaults.level;step=1 }
                } }
            } else if(step==1) {
                item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(text(R.string.interface_language));UiLanguage.entries.forEach { language->Choice(language.nativeName,ui==language) { ui=language } }
                    Text(text(R.string.material_language));MaterialMode.entries.forEach { material->Choice(material.label,mode==material) { mode=material } }
                } }
            } else {
                item { Heading(text(R.string.onboarding_support)) }
                LearningLevel.entries.forEach { l->item { Choice(levelLabel(l),level==l) { level=l } } }
                item { Note(text(R.string.cs_only_note)) }
                item { Note(text(R.string.privacy)) }
            }
            if(step>0) item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick={step--}) { Text(text(R.string.back)) }
                Button(onClick={if(step<2) step++ else finish(UserSettings(ui,mode,level,true))}) { Text(text(if(step<2) R.string.next else R.string.onboarding_finish)) }
            } }
        } } }
    }
}
