package dev.riposte.life
import dev.riposte.core.*
import org.junit.Assert.*
import org.junit.Test
class LifeTest {
    val m=Metric("mood","Mood")
    fun e(value:Double,day:String="2026-10-02",scope:String="momentary",basis:String="tracking")=Observation(metric="mood",stamp=Stamp(day=day),value=value,scope=scope,scale="1-5",origin=Origin(dateBasis=basis))
    @Test fun reviewTakesPrecedenceWithoutDeletingMoments() {val rows=listOf(e(1.0),e(3.0),e(5.0,scope="daily"));assertEquals(5.0,dailyValues(rows,m).single().value,0.0);assertEquals(3,rows.size)}
    @Test fun datesAndBasesAreNotSilentlyMixed() {val rows=listOf(e(1.0),e(5.0,basis="calendar"));assertEquals(2,dailyValues(rows,m).size)}
    @Test fun missingIsNotZero() {assertTrue(dailyValues(emptyList(),m).isEmpty());assertEquals(0.0,dailyValues(listOf(e(0.0)),m).single().value,0.0)}
}
