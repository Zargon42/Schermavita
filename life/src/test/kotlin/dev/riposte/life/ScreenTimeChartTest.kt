package dev.riposte.life

import dev.riposte.core.*
import org.junit.Assert.*
import org.junit.Test

class ScreenTimeChartTest {
    @Test fun screenTimeDiscoverableWithoutSyncOrManualVisibility() {
        val metric=chartableMetrics(emptyList()).single()
        assertEquals("screen_time",metric.id);assertEquals("min",metric.unit);assertTrue(metric.hidden)
        assertEquals(1,chartableMetrics(listOf(metric)).size)
        assertTrue(dailyValues(emptyList(),metric).isEmpty())
    }
    @Test fun storedMinutesExcludePartialAndKeepZeroAndLatestAggregate() {
        val metric=chartableMetrics(emptyList()).single()
        fun event(id: String,value: Double,partial: Boolean=false,created: String="2026-10-01T12:00:00Z") =
            Observation(id,metric.id,Stamp(day="2026-10-01",createdAt=created),value,scope="aggregate",unit="min",scale=metric.scale,origin=Origin(uncertain=partial))
        assertTrue(dailyValues(listOf(event("partial",30.0,true)),metric).isEmpty())
        assertEquals(0.0,dailyValues(listOf(event("zero",0.0)),metric).single().value,0.0)
        val rows=listOf(event("old",45.0),event("fresh",90.0,created="2026-10-01T13:00:00Z"))
        assertEquals(90.0,dailyValues(rows,metric).single().value,0.0)
    }
}
