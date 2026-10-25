package dev.riposte.core
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class DayClockTest {
    private val zone = ZoneId.of("Europe/Rome")
    @Test fun lateNightAndExactBoundary() {
        assertEquals("2026-10-02", DayClock.day(ZonedDateTime.of(2026,10,3,2,0,0,0,zone).toInstant(),zone,listOf(BoundaryRule())).label)
        assertEquals("2026-10-03", DayClock.day(ZonedDateTime.of(2026,10,3,4,0,0,0,zone).toInstant(),zone,listOf(BoundaryRule())).label)
    }
    @Test fun daylightSavingsUsesRealInterval() {
        val day = DayClock.day(Instant.parse("2026-03-28T12:00:00Z"),zone,listOf(BoundaryRule()))
        assertEquals(23L, Duration.between(Instant.parse(day.start),Instant.parse(day.end)).toHours())
    }
    @Test fun changedBoundaryDoesNotRewriteSavedDay() {
        val at=Instant.parse("2026-10-02T12:00:00Z"); val old=DayClock.day(at,zone,listOf(BoundaryRule()))
        val rules=DayClock.change(listOf(BoundaryRule()),at,zone,360,listOf(old))
        assertEquals(old,DayClock.day(at,zone,rules,listOf(old)))
        assertEquals(old.end,DayClock.day(Instant.parse(old.end).plusSeconds(1),zone,rules,listOf(old)).start)
        assertEquals("2026-10-03",DayClock.day(Instant.parse(old.end).plusSeconds(1),zone,rules,listOf(old)).label)
        assertEquals(26L,Duration.between(Instant.parse(old.end),Instant.parse(DayClock.day(Instant.parse(old.end).plusSeconds(1),zone,rules,listOf(old)).end)).toHours())
    }
    @Test fun earlierBoundaryAndTravelKeepIntervals() {
        val at=Instant.parse("2026-10-02T12:00:00Z");val old=DayClock.day(at,zone,listOf(BoundaryRule()))
        val rules=DayClock.change(listOf(BoundaryRule()),at,zone,120,listOf(old))
        val next=DayClock.day(Instant.parse(old.end).plusSeconds(1),zone,rules,listOf(old))
        assertEquals(22L,Duration.between(Instant.parse(next.start),Instant.parse(next.end)).toHours())
        assertEquals(old,DayClock.day(at,ZoneId.of("America/New_York"),rules,listOf(old)))
    }
    @Test fun cutoffsInsideDstGapAndOverlapUseInstants() {
        val rules=listOf(BoundaryRule(minutes=150))
        val spring=Instant.parse("2026-03-29T01:15:00Z")
        val fall=Instant.parse("2026-10-25T01:15:00Z")
        assertEquals("2026-03-28",DayClock.day(spring,zone,rules).label)
        assertEquals("2026-10-25",DayClock.day(fall,zone,rules).label)
        for(at in listOf(spring,fall)) {val d=DayClock.day(at,zone,rules);assertTrue(at>=Instant.parse(d.start) && at<Instant.parse(d.end))}
    }
    @Test fun backupAuthenticatesPasswordAndContents() {
        val encrypted=BackupCipher.encrypt("hello".toByteArray(),"long password".toCharArray())
        assertEquals("hello",BackupCipher.decrypt(encrypted,"long password".toCharArray()).decodeToString())
        assertThrows(Exception::class.java) { BackupCipher.decrypt(encrypted,"wrong password".toCharArray()) }
        encrypted[encrypted.lastIndex]=(encrypted.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { BackupCipher.decrypt(encrypted,"long password".toCharArray()) }
    }
}
