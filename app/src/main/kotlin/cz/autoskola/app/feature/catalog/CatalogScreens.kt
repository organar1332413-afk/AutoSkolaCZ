package cz.autoskola.app.feature.catalog
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
@Composable fun EmptyScreen(title: Int, description: Int) { Page { item { Heading(text(title)) }; item { Note(text(description)) } }
}
