package dev.riposte.app

import dev.riposte.core.*
import dev.riposte.life.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class InsightsPresentationTest {
    @Test fun currentScreenTimeShowsMinutesSoFarAndMeasurementTimeWhenSyncOff() {
        val now=Instant.parse("2026-10-03T12:00:00Z")
        val day=DayClock.day(now,ZoneId.systemDefault(),Settings().boundaryRules).label
        val reading=Observation("screen","screen_time",Stamp(now.toString(),now.toString(),"UTC",day),90.0,scope="aggregate",unit="min",scale="unlocked-screen-minutes",endAt=now.toString())
        val s=Snapshot(observations=listOf(reading))
        val note=screenTimeChartNote(s,now)!!
        assertTrue(note.contains("90"));assertTrue(note.contains("min so far"));assertTrue(note.contains("2026-10-03 12:00"))
        assertNull(screenTimeChartNote(s.copy(observations=listOf(reading.copy(origin=Origin(uncertain=true)))),now))
        assertNull(screenTimeChartNote(s,now.plusSeconds(86400)))
    }
}
