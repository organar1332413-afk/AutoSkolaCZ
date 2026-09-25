package cz.autoskola.domain

import java.time.LocalDate
import java.time.Instant

object PackageValidator {
    fun validate(pack: QuestionPackage) {
        val m = pack.manifest
        require(m.formatVersion in 1..2) { "Unsupported content format" }
        require(m.databaseVersion.isNotBlank() && m.source.startsWith("https://etesty.md.gov.cz/"))
        Instant.parse(m.retrievedAt)
        m.publicationDate?.let { LocalDate.parse(it) }
        require(m.sample || m.publicationDate != null) { "Production publication date is required" }
        require(!m.sample || (!m.completeForB && m.groupReadiness.none { it.eligibilityComplete || it.contentComplete || it.mediaComplete }))
        require(m.formatVersion != 2 || !m.completeForB)
        val claims = GroupReadinessPolicy.claims(m)
        require(claims.map { it.licenceGroup }.distinct().size == claims.size)
        claims.forEach { claim ->
            require(LicenceGroup.entries.any { it.code == claim.licenceGroup })
            require(claim.source.isNotBlank() && claim.blueprintVersion.isNotBlank())
            require(!claim.eligibilityComplete || claim.blueprintVersion == ExamConfigurationProvider.CURRENT_VERSION ||
                (m.formatVersion == 1 && claim.licenceGroup == "B" && claim.blueprintVersion == "B-stage2-v1"))
        }
        require(pack.questions.isNotEmpty())
        require(pack.questions.map { it.officialId }.distinct().size == pack.questions.size) { "Duplicate official IDs" }
        pack.questions.forEach { q ->
            require(q.officialId.matches(Regex("[A-Za-z0-9_-]+")))
            require(q.source.startsWith("https://etesty.md.gov.cz/"))
            require(q.category in ExamConfigurationProvider.categories)
            require(q.textCs.isNotBlank() && q.answers.size in 2..3)
            require(q.answers.map { it.code } == listOf("A", "B", "C").take(q.answers.size))
            require(q.answers.all { it.textCs.isNotBlank() } && q.answers.count { it.correct } == 1)
            require(q.points == null && m.sample || q.points in listOf(1, 2, 4))
            require(q.licenceGroups.all { code -> LicenceGroup.entries.any { it.code == code } })
            require(q.licenceGroups.distinct().size == q.licenceGroups.size)
            require(q.eligibility.all { e -> e.licenceGroup in q.licenceGroups && e.source.isNotBlank() })
            require(m.formatVersion != 2 || q.eligibility.map { it.licenceGroup } == q.licenceGroups)
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
                require(media.mimeType.matches(Regex("(image|video)/[a-z0-9.+-]+")))
                require(media.answerCode == null || media.answerCode in q.answers.map { it.code })
            }
        }
        claims.filter { it.eligibilityComplete && it.contentComplete && it.mediaComplete }.forEach { claim ->
            val config = ExamConfigurationProvider.forGroup(LicenceGroup.entries.single { it.code == claim.licenceGroup })
            require(GroupReadinessPolicy.availability(pack, config) == ExamAvailability.READY) { "Claimed ready pool is incomplete" }
        }
    }
}
