package cz.autoskola.data

import cz.autoskola.domain.LicenceGroup

/** Unlike a user preference, a persisted exam is immutable and must never be relabelled. */
internal fun persistedLicenceGroup(code: String): LicenceGroup =
    LicenceGroup.entries.find { it.code == code }
        ?: throw IllegalStateException("Corrupted exam licence group: $code")
