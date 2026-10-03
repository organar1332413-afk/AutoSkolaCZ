package cz.autoskola.app.feature.catalog
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
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
