package cz.autoskola.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import cz.autoskola.domain.UiLanguage
import java.util.Locale

/** UI resources follow only uiLanguage; material translations keep their independent policy. */
@Composable fun InterfaceLanguage(language: UiLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(base, configuration, language) {
        base.createConfigurationContext(Configuration(configuration).apply {
            setLocale(Locale.forLanguageTag(language.tag))
        })
    }
    CompositionLocalProvider(LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration, content = content)
}
