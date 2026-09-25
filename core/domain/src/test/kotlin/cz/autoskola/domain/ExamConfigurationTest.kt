package cz.autoskola.domain

import org.junit.Assert.*
import org.junit.Test

class ExamConfigurationTest {
    @Test fun everySupportedGroupHasVersioned25Question50PointRules() {
        LicenceGroup.entries.forEach { group ->
            val config = ExamConfigurationProvider.forGroup(group)
            assertEquals(group, config.licenceGroup)
            assertEquals(25, config.questionCount)
            assertEquals(50, config.maxPoints)
            assertEquals(43, config.passPoints)
            assertEquals(30, config.durationMinutes)
            assertTrue(ExamConfigurationProvider.supports(group, config.blueprintVersion))
        }
        assertFalse(ExamConfigurationProvider.supports(LicenceGroup.A, "B-stage2-v1"))
    }
}
