package cz.autoskola.domain

import java.time.LocalDate
import java.time.Instant

object PackageValidator {
    fun validate(pack: QuestionPackage) {
        val m = pack.manifest
        require(m.formatVersion == 1) { "Unsupported content format" }
        require(m.databaseVersion.isNotBlank() && m.source.startsWith("https://etesty.md.gov.cz/"))
        Instant.parse(m.retrievedAt)
        m.publicationDate?.let { LocalDate.parse(it) }
        require(m.sample || m.publicationDate != null) { "Production publication date is required" }
        require(!m.sample || !m.completeForB)
        require(pack.questions.isNotEmpty())
        require(pack.questions.map { it.officialId }.distinct().size == pack.questions.size) { "Duplicate official IDs" }
        pack.questions.forEach { q ->
            require(q.officialId.matches(Regex("[A-Za-z0-9_-]+")))
            require(q.source.startsWith("https://etesty.md.gov.cz/"))
            require(q.category in ExamBlueprint.counts)
            require(q.textCs.isNotBlank() && q.answers.size in 2..3)
            require(q.answers.map { it.code } == listOf("A", "B", "C").take(q.answers.size))
            require(q.answers.all { it.textCs.isNotBlank() } && q.answers.count { it.correct } == 1)
            require(q.points == null && m.sample || q.points in listOf(1, 2, 4))
            require(q.licenceGroups.isNotEmpty() || m.sample)
            require(q.licenceGroups.distinct().size == q.licenceGroups.size)
            require(q.translations.map { it.locale }.distinct().size == q.translations.size)
            q.translations.forEach { t ->
                require(t.locale != "cs" && t.locale.matches(Regex("[a-z]{2,3}(-[A-Za-z0-9]+)*")))
                require(t.text.isNotBlank() && t.answers.keys == q.answers.map { it.code }.toSet())
                require(t.answers.values.all { it.isNotBlank() })
                require(t.reviewStatus in listOf("draft", "reviewed"))
            }
            q.media.forEach { media ->
                require(media.path.matches(Regex("media/[A-Za-z0-9_-]+\\.[A-Za-z0-9]+")))
                require(media.sha256.matches(Regex("[a-f0-9]{64}")))
                require(media.answerCode == null || media.answerCode in q.answers.map { it.code })
            }
        }
    }
}
