package cz.autoskola.app.feature.profile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
import cz.autoskola.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
@Composable fun SettingsChoices(settings:UserSettings,ui:(UiLanguage)->Unit,material:(MaterialMode)->Unit,level:(LearningLevel)->Unit) {
    Text(text(R.string.interface_language),style=MaterialTheme.typography.titleMedium)
    UiLanguage.entries.forEach { Choice(it.nativeName,settings.uiLanguage==it) { ui(it) } }
    Text(text(R.string.material_language),style=MaterialTheme.typography.titleMedium)
    MaterialMode.entries.forEach { Choice(if(it==MaterialMode.CS_ONLY) text(R.string.only_czech) else it.label,settings.materialMode==it) { material(it) } }
    Text(text(R.string.learning_level),style=MaterialTheme.typography.titleMedium)
    LearningLevel.entries.forEach { Choice(levelLabel(it),settings.level==it) { level(it) } }
}
@Composable fun levelLabel(level:LearningLevel)=text(when(level) { LearningLevel.BEGINNER->R.string.beginner; LearningLevel.INTERMEDIATE->R.string.intermediate; LearningLevel.EXAM->R.string.exam_level })
@Composable fun ProfileScreen(settings:UserSettings,status:ContentStatus?,ui:(UiLanguage)->Unit,material:(MaterialMode)->Unit,level:(LearningLevel)->Unit,open:(String)->Unit,extras:@Composable ()->Unit) {
    Page {
        item { Heading(text(R.string.profile)) }
        item { androidx.compose.foundation.layout.Column { SettingsChoices(settings,ui,material,level) } }
        item { Note(text(R.string.cs_only_note)) }
        item { HorizontalDivider() }
        item { Text(text(R.string.database_version),style=MaterialTheme.typography.titleMedium) }
        item { Note(status?.publicationDate?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrNull() } ?: text(R.string.database_unknown)) }
        if(status==null) item { Note(text(R.string.database_empty)) }
        if(status!=null) item { Note(status.databaseVersion) }
        item { Note(text(R.string.source)) }
        item { Note(text(R.string.privacy)) }
        item { Entry(text(R.string.czech)) { open("czech") } }
        item { extras() }
    }
}
