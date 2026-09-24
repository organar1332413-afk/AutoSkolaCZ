package cz.autoskola.domain
import org.junit.Assert.*
import org.junit.Test
class ReviewPolicyTest {
    @Test fun masteryRequiresThreeSeparatedSessions() {
        val gap=ReviewPolicy.separationMs
        val one=ReviewPolicy.next(null,"v1:q",true,100,0)
        val two=ReviewPolicy.next(one,"v1:q",true,100+gap,101)
        val three=ReviewPolicy.next(two,"v1:q",true,100+gap*2,101+gap)
        assertEquals(3,three.streak);assertEquals(100+gap*2,three.masteredAt)
    }
    @Test fun repetitionInSameSessionDoesNotInflateMastery() {
        val one=ReviewPolicy.next(null,"v:q",true,100,0)
        assertEquals(one,ReviewPolicy.next(one,"v:q",true,100+ReviewPolicy.separationMs,0))
    }
    @Test fun rapidNewSessionDoesNotInflateMastery() {
        val one=ReviewPolicy.next(null,"v:q",true,100,0)
        assertEquals(one,ReviewPolicy.next(one,"v:q",true,100+ReviewPolicy.separationMs-1,101))
    }
    @Test fun wrongAnswerClearsMastery() {
        val mastered=ReviewState("v:q",3,100,100)
        assertEquals(ReviewState("v:q"),ReviewPolicy.next(mastered,"v:q",false,200,101))
    }
    @Test fun newRevisionStartsFreshStreak() { assertEquals(1,ReviewPolicy.next(ReviewState("v1:q",3,100,100),"v2:q",true,200,101).streak) }
    @Test fun counterIsCappedAndMasteryDateStable() { val s=ReviewState("v:q",3,100,100);val n=ReviewPolicy.next(s,"v:q",true,100+ReviewPolicy.separationMs,101);assertEquals(3,n.streak);assertEquals(s.masteredAt,n.masteredAt) }
    @Test fun revisionChangeRetainsMistakeAndUnseenFilter() {
        val q=QuestionCard("v2:q","q","rules","TEST ONLY",2,emptyList(),null)
        val state=LearningSnapshot(listOf(AttemptRecord("a","v1:q",false,null,1,false,"rules")),setOf("q"),mapOf("q" to ReviewState("v1:q",3,100,100)))
        assertTrue(state.matches(q,QuestionFilter.MISTAKES));assertTrue(state.matches(q,QuestionFilter.UNSEEN));assertTrue(state.matches(q,QuestionFilter.FAVORITES))
        assertFalse(state.matches(q,QuestionFilter.KNOWN));assertFalse(state.matches(q,QuestionFilter.DOUBTFUL))
    }
    @Test fun knownAndDoubtfulUseCurrentRevisionProgress() {
        val q=QuestionCard("v:q","q","rules","TEST ONLY",2,emptyList(),null)
        val attempted=LearningSnapshot(listOf(AttemptRecord("a","v:q",true,null,1,false,"rules")),reviews=mapOf("q" to ReviewState("v:q",1,1,null)))
        assertTrue(attempted.matches(q,QuestionFilter.DOUBTFUL));assertFalse(attempted.matches(q,QuestionFilter.KNOWN))
        val mastered=attempted.copy(reviews=mapOf("q" to ReviewState("v:q",3,1,2)))
        assertTrue(mastered.matches(q,QuestionFilter.KNOWN));assertFalse(mastered.matches(q,QuestionFilter.DOUBTFUL))
    }
}
