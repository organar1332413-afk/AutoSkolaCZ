package cz.autoskola.app.feature.catalog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cz.autoskola.app.R
import cz.autoskola.app.ui.*
@Composable fun SignsScreen(
    translationTag: String?,
    availableQuestionIds: Set<String>,
    openQuestion: (String) -> Unit,
) {
    val context = LocalContext.current
    val entries = remember(context) { SignCatalog.load(context) }
    val guide = remember(context) { SignCatalog.loadGuide(context) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<SignEntry?>(null) }
    val language = translationTag
    val categories = listOf(
        "warning" to R.string.sign_warning, "priority" to R.string.sign_priority,
        "prohibition" to R.string.sign_prohibition, "mandatory" to R.string.sign_mandatory,
        "information_zone" to R.string.sign_info, "information_traffic" to R.string.sign_info,
        "information_direction" to R.string.sign_info, "information_other" to R.string.sign_info,
        "additional_panel" to R.string.sign_extra, "road_marking" to R.string.sign_markings,
        "light_signal" to R.string.sign_lights,
    )
    val visible = remember(entries, query, category) { SignCatalog.search(entries, query, category) }
    Page {
        item { Heading(text(R.string.signs)) }
        item { Text(text(R.string.signs_guide_title), style = MaterialTheme.typography.titleLarge) }
        items(guide) { block ->
            OutlinedCard {
                Column(Modifier.padding(18.dp)) {
                    Text(block.provision, style = MaterialTheme.typography.titleMedium)
                    block.officialTextCs?.let { Text(it) }
                    Text(block.summaryCs)
                    Note(block.simpleCs)
                    when (language) {
                        "ru" -> Text(block.ru)
                        "uk" -> Text(block.uk)
                    }
                }
            }
        }
        item { Note(text(R.string.signs_inventory_notice)) }
        item {
            OutlinedTextField(value = query, onValueChange = { query = it },
                label = { Text(text(R.string.signs_search)) }, singleLine = true)
        }
        item { Text(text(R.string.signs_count, visible.size)) }
        item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text(text(R.string.signs_all)) }) }
        items(categories) { (key, label) ->
            FilterChip(selected = category == key, onClick = { category = key }, label = { Text("${text(label)} · ${entries.count { it.category == key }}") })
        }
        selected?.let { sign ->
            item {
                OutlinedCard(onClick = { selected = null }) {
                    Column(Modifier.padding(18.dp)) {
                        Text("${sign.code} · ${sign.titleCs}", style = MaterialTheme.typography.titleLarge)
                        when (language) {
                            "ru" -> sign.titleRu?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                            "uk" -> sign.titleUk?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        }
                        sign.meaningCs?.let { Text(it) }
                        sign.simpleCs?.let { Text(it) }
                        when (language) {
                            "ru" -> sign.ru?.let { Text(it) }
                            "uk" -> sign.uk?.let { Text(it) }
                        }
                        if (sign.meaningCs == null) Note(text(R.string.signs_unreviewed))
                        sign.memoryCs?.let { Note(it) }
                        sign.mistakeCs?.let { Text(it) }
                        if (sign.confusedWith.isNotEmpty()) Text(sign.confusedWith.joinToString(" · "))
                        sign.sourceProvision?.let { Text(it) }
                        Note(text(R.string.signs_source, sign.sourceUrl))
                        val linked = sign.relatedOfficialIds.filter { it in availableQuestionIds }
                        if (linked.isNotEmpty()) {
                            Text(text(R.string.signs_related_official_questions))
                            linked.forEach { id ->
                                Entry(id) { openQuestion(id) }
                            }
                        }
                        if (sign.graphicStatus != "VERIFIED") Note(text(R.string.signs_graphic_pending))
                    }
                }
            }
        }
        items(visible, key = { it.code }) { sign ->
            Entry("${sign.code} · ${sign.titleCs}", if (sign.meaningCs != null) sign.simpleCs else text(R.string.signs_unreviewed)) {
                selected = if (selected?.code == sign.code) null else sign
            }
        }
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
