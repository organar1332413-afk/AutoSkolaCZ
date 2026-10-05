package cz.autoskola.app.ui
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
@Composable fun text(@StringRes id: Int): String = LocalContext.current.getString(id)
@Composable fun text(@StringRes id: Int, vararg args: Any): String = LocalContext.current.getString(id, *args)
@Composable fun quantityText(@PluralsRes id: Int, quantity: Int): String =
    LocalContext.current.resources.getQuantityString(id, quantity, quantity)
@Composable fun Page(content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable fun Heading(title: String) { Text(title, style = MaterialTheme.typography.headlineSmall) }
@Composable fun Note(value: String) { Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun Entry(title: String, detail: String? = null, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) Note(detail)
        }
    }
}
@Composable fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Text(if (selected) "✓  $label" else label, modifier = Modifier.padding(vertical = 4.dp))
    }
}
