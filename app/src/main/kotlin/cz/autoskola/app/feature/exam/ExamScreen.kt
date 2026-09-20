package cz.autoskola.app.feature.exam
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
@Composable fun ExamScreen() {
    Page {
        item { Heading(text(R.string.exam)) }
        item { Text(text(R.string.exam_info), style = MaterialTheme.typography.titleLarge) }
        item { Note(text(R.string.exam_score)) }
        item { Note(text(R.string.exam_rules)) }
        item { Note(text(R.string.exam_unavailable)) }
        item { Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(text(R.string.exam_start)) } }
    }
}
