package cz.autoskola.app.feature.firstaid

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import cz.autoskola.domain.Lexeme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AidText(val cs: String, val ru: String, val uk: String) {
    fun helper(tag: String?): String? = when(tag) { "ru" -> ru; "uk" -> uk; else -> null }
    fun language(tag: String) = helper(tag) ?: cs
}
data class AidCard(val id: String, val category: String, val title: AidText, val summary: AidText,
    val examSummary: AidText, val clinical: AidText, val badges: List<AidText>, val questionIds: List<String>,
    val image: String, val sourceIds: List<String>, val relatedCards: List<String>)
data class AidOption(val label: String, val cs: String, val correct: Boolean, val ru: String, val uk: String) {
    fun helper(tag: String?): String? = when(tag) { "ru" -> ru; "uk" -> uk; else -> null }
}
data class AidQuestion(val id: String, val question: AidText, val answer: AidText, val options: List<AidOption>) {
    val correct get() = options.single { it.correct }
}
data class AidSource(val id: String, val title: String, val url: String)
data class AidBundle(val cards: List<AidCard>, val questions: List<AidQuestion>, val sources: List<AidSource>,
    val vocabulary: List<Lexeme> = emptyList()) {
    fun cardsFor(id: String) = cards.filter { id in it.questionIds }
}
data class AidLoadState(val bundle: AidBundle? = null, val loading: Boolean = true, val failed: Boolean = false)

private fun JSONArray.strings() = (0 until length()).map { getString(it) }
private fun JSONObject.aidText() = AidText(getString("cs"), getString("ru"), getString("uk"))
object FirstAidContent {
    fun load(context: Context): AidBundle {
        fun asset(path: String) = JSONObject(context.assets.open("first_aid/$path").bufferedReader().use { it.readText() })
        val rows = asset("cards.json").getJSONArray("cards")
        val cards = (0 until rows.length()).map { i -> rows.getJSONObject(i).let { c ->
            AidCard(c.getString("id"), c.getString("category"), c.getJSONObject("title").aidText(),
                c.getJSONObject("summary").aidText(), c.getJSONObject("examSummary").aidText(),
                c.getJSONObject("clinical").aidText(), c.getJSONArray("badges").let { a -> (0 until a.length()).map { a.getJSONObject(it).aidText() } },
                c.getJSONArray("questionIds").strings(), c.getString("image"), c.getJSONArray("sourceIds").strings(),
                c.getJSONArray("relatedCards").strings())
        } }
        val helpers = asset("question-helpers.json").getJSONArray("questions").let { a ->
            (0 until a.length()).associate { a.getJSONObject(it).let { h -> h.getString("id") to h } }
        }
        val questions = asset("official-questions.json").getJSONArray("questions").let { a ->
            (0 until a.length()).map { i -> a.getJSONObject(i).let { q ->
                val h = helpers.getValue(q.getString("id"))
                val optionHelpers = h.getJSONObject("options")
                val options = q.getJSONArray("options").let { o -> (0 until o.length()).map { index ->
                    o.getJSONObject(index).let {
                        val helper = optionHelpers.getJSONObject(it.getString("label"))
                        val ru = helper.getString("ru"); val uk = helper.getString("uk")
                        require(ru.isNotBlank() && uk.isNotBlank())
                        AidOption(it.getString("label"), it.getString("textCs"), it.getBoolean("correct"), ru, uk)
                    }
                } }
                require(optionHelpers.keys().asSequence().toSet() == options.map { it.label }.toSet())
                require(h.getJSONObject("question").getString("cs") == q.getString("questionCs"))
                require(h.getJSONObject("answer").getString("cs") == options.single { it.correct }.cs)
                AidQuestion(q.getString("id"), h.getJSONObject("question").aidText(), h.getJSONObject("answer").aidText(), options)
            } }
        }
        val sources = asset("sources.json").getJSONArray("sources").let { a -> (0 until a.length()).map {
            a.getJSONObject(it).let { s -> AidSource(s.getString("id"), s.getString("title"), s.getString("url")) }
        } }
        val vocabulary = asset("vocabulary.json").getJSONArray("words").let { a -> (0 until a.length()).flatMap { i ->
            val w = a.getJSONObject(i)
            listOf("ru", "uk").map { tag -> Lexeme("aid-${w.getString("lemma")}", w.getString("lemma"),
                w.getString(tag), w.getString(tag), w.getString("exampleCs"), w.getString(tag), false, tag, w.getJSONArray("forms").strings()) }
        } }
        require(cards.size == 16 && cards.map { it.id }.toSet().size == 16)
        require(questions.size == 35 && questions.map { it.id }.toSet().size == 35)
        require(cards.flatMap { it.questionIds }.toSet() == questions.map { it.id }.toSet())
        require(cards.all { c -> c.sourceIds.all { id -> sources.any { it.id == id } } })
        cards.forEach { context.assets.open(it.image).use { stream -> require(stream.read() >= 0) } }
        return AidBundle(cards, questions, sources, vocabulary)
    }
}

@Composable fun rememberFirstAidContent(): Pair<State<AidLoadState>, () -> Unit> {
    val context = LocalContext.current.applicationContext
    var attempt by remember { mutableIntStateOf(0) }
    val state = produceState(AidLoadState(), context, attempt) {
        value = AidLoadState()
        value = withContext(Dispatchers.IO) { runCatching { AidLoadState(FirstAidContent.load(context), loading = false) }
            .getOrElse { AidLoadState(loading = false, failed = true) } }
    }
    return state to { attempt++ }
}

fun filterAid(cards: List<AidCard>, query: String, category: String, translationTag: String?): List<AidCard> {
    val normalized = query.trim().lowercase(java.util.Locale.ROOT)
    return cards.filter { (category == "all" || it.category == category) &&
        (normalized.isEmpty() || listOf(it.id, it.title.cs, it.summary.cs, it.title.helper(translationTag).orEmpty(),
            it.summary.helper(translationTag).orEmpty(), it.questionIds.joinToString(" ")).any { value ->
            value.lowercase(java.util.Locale.ROOT).contains(normalized) }) }
}
