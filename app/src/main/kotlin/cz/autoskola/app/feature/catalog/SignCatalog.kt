package cz.autoskola.app.feature.catalog

import android.content.Context
import org.json.JSONObject

/** Official Czech index and separately authored source-backed teaching copy. */
data class SignEntry(
    val code: String,
    val titleCs: String,
    val titleRu: String?,
    val titleUk: String?,
    val category: String,
    val sourceUrl: String,
    val sourceProvision: String?,
    val graphicStatus: String,
    val meaningCs: String?,
    val simpleCs: String?,
    val mistakeCs: String?,
    val memoryCs: String?,
    val confusedWith: List<String>,
    val ru: String?,
    val uk: String?,
)

data class SignGuideBlock(
    val provision: String,
    val officialTextCs: String?,
    val summaryCs: String,
    val simpleCs: String,
    val ru: String,
    val uk: String,
)

object SignCatalog {
    fun load(context: Context): List<SignEntry> {
        fun asset(path: String) = JSONObject(context.assets.open(path).bufferedReader().use { it.readText() })
        val inventory = asset("signs/catalog.json")
        val cards = asset("signs/curated.json").getJSONArray("cards")
        val sources = asset("signs/sources.json")
        val byCode = (0 until cards.length()).associate { index ->
            cards.getJSONObject(index).let { it.getString("code") to it }
        }
        val entries = inventory.getJSONArray("signs")
        return (0 until entries.length()).map { index ->
            val sign = entries.getJSONObject(index)
            val card = byCode[sign.getString("code")]
            val sourceId = card?.getJSONArray("sourceIds")?.getString(0)
                ?: sign.getJSONArray("sourceIds").getString(0)
            SignEntry(
                code = sign.getString("code"),
                titleCs = sign.getString("titleCs"),
                titleRu = card?.optString("titleRu")?.takeIf { it.isNotBlank() },
                titleUk = card?.optString("titleUk")?.takeIf { it.isNotBlank() },
                category = sign.getString("category"),
                sourceUrl = sources.getJSONObject(sourceId).getString("url"),
                sourceProvision = card?.optString("sourceProvision")?.takeIf { it.isNotBlank() },
                graphicStatus = sign.getJSONObject("graphic").getString("status"),
                meaningCs = card?.getString("meaningCs"),
                simpleCs = card?.getString("simpleCs"),
                mistakeCs = card?.optString("mistakeCs")?.takeIf { it.isNotBlank() },
                memoryCs = card?.optString("memoryCs")?.takeIf { it.isNotBlank() },
                confusedWith = card?.optJSONArray("confusedWith")?.let { related ->
                    (0 until related.length()).map { related.getString(it) }
                }.orEmpty(),
                ru = card?.getString("ru"),
                uk = card?.getString("uk"),
            )
        }
    }

    fun search(entries: List<SignEntry>, query: String, category: String?): List<SignEntry> {
        val scoped = entries.filter { category == null || it.category == category }
        val term = query.trim()
        if (term.isBlank()) return scoped
        val exact = scoped.filter { it.code.equals(term, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact
        return scoped.filter { sign ->
            sign.code.contains(term, ignoreCase = true) || sign.titleCs.contains(term, ignoreCase = true) ||
                sign.titleRu?.contains(term, ignoreCase = true) == true ||
                sign.titleUk?.contains(term, ignoreCase = true) == true
        }
    }

    fun loadGuide(context: Context): List<SignGuideBlock> {
        val root = JSONObject(context.assets.open("signs/guide.json").bufferedReader().use { it.readText() })
        val blocks = root.getJSONArray("blocks")
        return (0 until blocks.length()).map { index ->
            val block = blocks.getJSONObject(index)
            SignGuideBlock(
                provision = block.getString("provision"),
                officialTextCs = block.optString("officialTextCs").takeIf { it.isNotBlank() && it != "null" },
                summaryCs = block.getString("ruleSummaryCs"),
                simpleCs = block.getString("simpleCs"),
                ru = block.getString("ru"),
                uk = block.getString("uk"),
            )
        }
    }
}
