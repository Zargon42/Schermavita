package dev.riposte.core
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScreenTimeTest {
    private fun e(at: Long,k: ScreenTime.Kind)=ScreenTime.Event(at,k)
    @Test fun countsUnlockedInteractiveTimeOnly() {
        val events=listOf(e(0,ScreenTime.Kind.OFF),e(10,ScreenTime.Kind.ON),e(20,ScreenTime.Kind.UNLOCK),e(50,ScreenTime.Kind.LOCK),e(70,ScreenTime.Kind.OFF))
        assertEquals(ScreenTime.Reading(30,10),ScreenTime.measure(events,0,100))
    }
    @Test fun warmupStateClipsAcrossBoundaryAndDuplicateEvents() {
        val events=listOf(e(0,ScreenTime.Kind.UNLOCK),e(25,ScreenTime.Kind.UNLOCK),e(75,ScreenTime.Kind.OFF))
        assertEquals(ScreenTime.Reading(25,0),ScreenTime.measure(events,50,100))
        assertEquals(ScreenTime.Reading(50,0),ScreenTime.measure(events,0,50))
    }
    @Test fun missingAndRebootIntervalsStayUnknown() {
        assertEquals(ScreenTime.Reading(0,100),ScreenTime.measure(emptyList(),0,100))
        assertEquals(ScreenTime.Reading(30,50),ScreenTime.measure(listOf(e(0,ScreenTime.Kind.UNLOCK),e(30,ScreenTime.Kind.RESET),e(80,ScreenTime.Kind.OFF)),0,100))
    }
    @Test fun knownOffDayReallyIsZero() {
        assertEquals(ScreenTime.Reading(0,0),ScreenTime.measure(listOf(e(0,ScreenTime.Kind.OFF)),50,100))
    }
    @Test fun realDstIntervalAndEarlyMorningAssignment() {
        val zone=ZoneId.of("Europe/Rome")
        val day=DayClock.day(Instant.parse("2026-03-29T00:00:00Z"),zone,listOf(BoundaryRule()))
        val start=Instant.parse(day.start).toEpochMilli();val end=Instant.parse(day.end).toEpochMilli()
        assertEquals("2026-03-28",day.label)
        assertEquals(23*3600000L,ScreenTime.measure(listOf(e(start-1,ScreenTime.Kind.UNLOCK)),start,end).milliseconds)
    }
    @Test fun oldSettingsDecodeWithoutChangingExistingPreferences() {
        val old=codec.decodeFromString<Settings>("""{"reminderEnabled":false,"activeSession":"old-session","lastHealthSync":"2026-10-01T12:00:00Z"}""")
        assertFalse(old.screenTimeEnabled);assertFalse(old.reminderEnabled)
        assertEquals("old-session",old.activeSession)
        assertEquals("2026-10-01T12:00:00Z",old.lastHealthSync)
    }
}
