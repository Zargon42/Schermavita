package dev.riposte.insights
import dev.riposte.life.*
import org.junit.Assert.*
import org.junit.Test
class ComparisonTest {
    @Test fun precedingMeansCalendarDayNotPreviousObservedRow() {
        val x=listOf(DailyValue("2026-10-01",1.0,"tracking","x","daily",1),DailyValue("2026-10-03",2.0,"tracking","x","daily",1))
        val y=listOf(DailyValue("2026-10-04",4.0,"tracking","y","daily",1))
        assertEquals(listOf(2.0 to 4.0),compare(x,y,true).pairs)
    }
    @Test fun differentBasesNeverPair() {assertTrue(compare(listOf(DailyValue("2026-10-01",1.0,"calendar","x","daily",1)),listOf(DailyValue("2026-10-01",2.0,"tracking","y","daily",1)),false).pairs.isEmpty())}
    @Test fun computesRegressionLineFromObservedPoints() {
        assertEquals(listOf(1.0 to 3.0,3.0 to 7.0),regressionLine(listOf(1.0 to 3.0,2.0 to 5.0,3.0 to 7.0)))
    }
    @Test fun regressionNeedsTwoDistinctDates() {
        assertTrue(regressionLine(emptyList()).isEmpty())
        assertTrue(regressionLine(listOf(1.0 to 2.0)).isEmpty())
        assertTrue(regressionLine(listOf(1.0 to 2.0,1.0 to 3.0)).isEmpty())
    }
}
