package dev.riposte.fencing
import dev.riposte.core.*
import org.junit.Assert.*
import org.junit.Test
class FencingTest {
    fun bout(a:String,b:String,x:Int=5,y:Int=3)=Bout(sessionId="session",a=a,b=b,scoreA=x,scoreB=y,stamp=Stamp(day="2026-10-02"))
    @Test fun othersNeverCountAsMyPerformance() {
        val rows=listOf(bout("me","alice"),bout("alice","bob"))
        assertEquals(1,statistics(rows,"me").bouts);assertEquals(2,statistics(rows,"alice").bouts);assertEquals(1,statistics(rows,"alice").wins)
    }
    @Test fun unknownAndPriorityOutcomes() {
        assertNull(bout("me","alice").copy(outcome="Unknown").winner())
        assertEquals("alice",bout("me","alice",4,4).copy(outcome="B").winner())
        assertNull(bout("me","alice").copy(status="Unfinished").winner())
    }
    @Test fun equipmentIsSnapshotAndRematchesRemainSeparate() {
        val a=bout("me","alice").copy(detailsA=ParticipantDetails(sword="first"));val b=bout("me","alice").copy(detailsA=ParticipantDetails(sword="second"))
        assertNotEquals(a.id,b.id);assertEquals(2,statistics(listOf(a,b),"me").bouts);assertEquals("first",a.detailsA.sword)
    }
    @Test fun disallowSelfMatchAndBadScores() {
        assertThrows(IllegalArgumentException::class.java) {bout("me","me").validate()}
        assertThrows(IllegalArgumentException::class.java) {bout("me","alice",-1).validate()}
    }
    @Test fun reportsSelectedFencerPointAndWinLossRatiosAndRatings() {
        val rated=bout("me","alice",5,3).copy(detailsA=ParticipantDetails(rating=4.0))
        val loss=bout("alice","me",5,2)
        val stats=statistics(listOf(rated,loss,bout("alice","bob")), "me")
        assertEquals("7 : 8",stats.pointRatio)
        assertEquals("1 : 1",stats.winLoss)
        assertEquals(4.0,stats.execution!!,0.0)
        assertEquals(1,stats.rated)
        assertEquals("—",statistics(emptyList(),"me").pointRatio)
    }
}
