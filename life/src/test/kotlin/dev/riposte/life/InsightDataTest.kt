package dev.riposte.life

import dev.riposte.core.*
import org.junit.Assert.*
import org.junit.Test

class InsightDataTest {
    private val current=Metric("mood","Mood (Happiness)")
    private val historical=Metric("legacy:unified_mood","Mood · history","Number",min=-1e12,max=1e12,scale="rating-1-5",hidden=true)
    private fun event(metric: Metric,basis: String,value: Double)=Observation(metric.id,metric.id,Stamp(day="2026-10-01"),value,scope="aggregate",scale=metric.scale,origin=Origin(dateBasis=basis))

    @Test fun historicalMoodSharesHappinessChartWithoutChangingStoredData() {
        val original=listOf(event(current,"tracking",4.0),event(historical,"calendar",2.0))
        val data=insightData(listOf(current,historical),original)
        val metric=data.metrics.single()
        assertEquals(current.id,metric.id)
        val values=compatibleDailyValues(data.observations,metric,true,data.observations.map {it.scale}.toSet(),setOf("tracking","calendar"))
        assertEquals(2,values.size);assertEquals(1,values.map {it.scale}.distinct().size)
        assertEquals(setOf("tracking","calendar"),values.map {it.basis}.toSet())
        assertEquals(setOf(2.0,4.0),values.map {it.value}.toSet())
        assertEquals("legacy:unified_mood",original.last().metric);assertEquals("rating-1-5",original.last().scale)
        assertEquals(historical.name,metric.revisions.single().name)
        assertEquals(data,insightData(data.metrics,data.observations))
        assertEquals(2,compatibleDailyValues(data.observations,metric,false,data.observations.map {it.scale}.toSet(),setOf("tracking","calendar")).map {it.scale}.distinct().size)
    }

    @Test fun unknownScalesAndUnrelatedHistoryAreNotSilentlyMerged() {
        val unknown=event(historical,"calendar",42.0).copy(id="unknown",scale="unidentified")
        val stress=historical.copy(id="legacy:unified_stress",name="Stress · history")
        val data=insightData(listOf(current,historical,stress),listOf(event(historical,"calendar",3.0),unknown,event(stress,"calendar",2.0)))
        assertEquals(historical.id,data.observations.single {it.id=="unknown"}.metric)
        assertTrue(data.metrics.any {it.id==historical.id});assertTrue(data.metrics.any {it.id==stress.id})
        val wide=current.copy(max=10.0,scale="rating-1-10")
        val different=insightData(listOf(wide,historical),listOf(event(wide,"tracking",8.0),event(historical,"calendar",3.0)))
        val values=compatibleDailyValues(different.observations,different.metrics.single(),true,different.observations.map {it.scale}.toSet(),setOf("tracking","calendar"))
        assertEquals(2,values.map {it.scale}.distinct().size)
    }
}
