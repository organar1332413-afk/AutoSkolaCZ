package cz.autoskola.app.feature.catalog
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
@Composable fun SignsScreen() {
    Page {
        item { Heading(text(R.string.signs)) }
        item { Note(text(R.string.content_pending)) }
        items(listOf(R.string.sign_warning, R.string.sign_priority, R.string.sign_prohibition, R.string.sign_mandatory, R.string.sign_info, R.string.sign_extra, R.string.sign_markings, R.string.sign_lights)) { item -> Text(text(item)) }
    }
}
@Composable fun FirstAidScreen(open: (String) -> Unit) {
    Page {
        item { Heading(text(R.string.first_aid)) }
        item { Note(text(R.string.content_pending)) }
        items(listOf(R.string.aid_safety, R.string.aid_call, R.string.aid_conscious, R.string.aid_cpr, R.string.aid_bleed, R.string.aid_position, R.string.aid_car, R.string.aid_injury)) { item -> Text(text(item)) }
        item { Entry(text(R.string.questions)) { open("aid_questions") } }
    }
}
@Composable fun EmptyScreen(title: Int, description: Int) { Page { item { Heading(text(title)) }; item { Note(text(description)) } }
}
