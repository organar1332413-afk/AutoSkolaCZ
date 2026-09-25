package cz.autoskola.domain

import org.junit.Assert.*
import org.junit.Test

class GroupExamStatisticsTest {
    private fun exam(id: String, group: LicenceGroup, at: Long, score: Int) =
        ExamHistoryItem(id, group, "v1", at - 100, at, score, 50, 43, 1)

    @Test fun selectedGroupControlsHomeSummaryAndAllExamMetrics() {
        val snapshot = LearningSnapshot(
            examHistory = listOf(exam("B-new", LicenceGroup.B, 300, 50),
                exam("C-old", LicenceGroup.C, 200, 0), exam("B-old", LicenceGroup.B, 100, 0)),
            attempts = listOf(
                AttemptRecord("b", "v:b", true, null, 300, false, "rules", examLicenceGroup = LicenceGroup.B),
                AttemptRecord("c", "v:c", false, ErrorReason.CZECH_UNCLEAR, 200, false, "situations", examLicenceGroup = LicenceGroup.C),
                AttemptRecord("learning", "v:q", false, ErrorReason.CZECH_UNCLEAR, 400, false, "vehicle")
            )
        )
        assertEquals("C-old", snapshot.lastExamFor(LicenceGroup.C)?.id)
        val b = snapshot.examStatistics(LicenceGroup.B)
        val c = snapshot.examStatistics(LicenceGroup.C)
        assertEquals(2, b.history.size)
        assertEquals(50.0, b.averagePercent!!, 0.01)
        assertEquals(50, b.passRate)
        assertEquals(100, b.correctPercent)
        assertTrue(b.weakTopics.isEmpty())
        assertEquals(0, b.errors(ErrorReason.CZECH_UNCLEAR))
        assertEquals(1, c.history.size)
        assertEquals(0.0, c.averagePercent!!, 0.01)
        assertEquals(0, c.passRate)
        assertEquals(0, c.correctPercent)
        assertEquals(listOf("situations" to 1), c.weakTopics)
        assertEquals(1, c.errors(ErrorReason.CZECH_UNCLEAR))
    }
}
