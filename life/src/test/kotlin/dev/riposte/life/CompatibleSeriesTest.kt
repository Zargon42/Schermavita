package dev.riposte.life

import dev.riposte.core.*
import org.junit.Assert.*
import org.junit.Test

class CompatibleSeriesTest {
    private val original=Metric(id="m",name="Mood")
    private val renamed=original.edited(original.definition().copy(levels=original.definition().levels.map {it.copy(label="Level ${it.value}")}))
    private fun event(id: String,scale: String,value: Double,basis: String="tracking",scope: String="daily")=
        Observation(id,"m",Stamp(day="2026-10-01",createdAt="2026-10-01T12:00:0${if(id=="new") 2 else 1}Z"),value,scale=scale,scope=scope,origin=Origin(dateBasis=basis))
    @Test fun compatibleRevisionsResolveDailyPrecedenceWithoutDuplicatingDays() {
        val rows=listOf(event("old",original.scale,1.0),event("new",renamed.scale,4.0),event("moment",original.scale,2.0,scope="momentary"))
        val merged=compatibleDailyValues(rows,renamed,true,rows.map {it.scale}.toSet(),setOf("tracking"))
        assertEquals(1,merged.size);assertEquals(4.0,merged.single().value,0.0);assertEquals(renamed.scale,merged.single().revision)
        assertEquals(2,compatibleDailyValues(rows,renamed,false,rows.map {it.scale}.toSet(),setOf("tracking")).size)
    }
    @Test fun rangesUnknownDefinitionsAndDateBasesRemainDistinct() {
        val wide=renamed.edited(renamed.definition().copy(max=10.0))
        assertNotEquals(wide.compatibleScale(wide.scale),wide.compatibleScale(original.scale))
        assertNotEquals(wide.compatibleScale("missing"),wide.compatibleScale(wide.scale))
        val rows=listOf(event("old",original.scale,1.0),event("new",renamed.scale,4.0,"calendar"))
        assertEquals(2,compatibleDailyValues(rows,renamed,true,rows.map {it.scale}.toSet(),setOf("tracking","calendar")).size)
        assertEquals(1,compatibleDailyValues(rows,renamed,true,rows.map {it.scale}.toSet(),setOf("tracking")).size)
    }
    @Test fun rollingMeansUseObservedCalendarDaysAndRequireTwoDays() {
        val points=listOf(1.0 to 2.0,1.0 to 4.0,3.0 to 5.0,20.0 to 9.0)
        val result=rollingMean(points,7)
        assertEquals(1,result.size);assertEquals(4.0,result.single().value,0.0);assertEquals(2,result.single().count)
        assertTrue(rollingMean(emptyList(),14).isEmpty())
    }
}
