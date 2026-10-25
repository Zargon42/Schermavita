package dev.riposte.fencing
import dev.riposte.core.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
class SessionPreparationTest {
    @Test fun independentSessionsAndEquipmentSnapshots() {
        val definition=defaultSessionTrackers().first()
        val first=Session(name="Morning",stamp=Stamp(day="2026-10-07"),preparation=mapOf("willingness" to TrackerAnswer("1",definition)),lenses=false,equipment=mapOf("shoes" to "First pair"))
        val second=first.copy(id=newId(),preparation=mapOf("willingness" to TrackerAnswer("5",definition)),lenses=true,equipment=mapOf("shoes" to "Second pair"))
        val bout=Bout(sessionId=first.id,a="me",b="other",scoreA=5,scoreB=3,stamp=first.stamp,detailsA=first.participantEquipment())
        assertEquals("1",first.preparation["willingness"]!!.value);assertEquals("5",second.preparation["willingness"]!!.value)
        assertEquals(false,bout.detailsA.lenses);assertEquals("First pair",bout.detailsA.equipment["shoes"])
        assertNull(bout.detailsB.lenses);assertTrue(bout.detailsB.equipment.isEmpty())
        assertEquals("Override",bout.detailsA.copy(equipment=mapOf("shoes" to "Override")).equipment["shoes"])
    }
    @Test fun optionalFieldsRoundTripAndOlderSessionsLoad() {
        val old="""{"id":"old","name":"Old","stamp":{"day":"2026-10-07"}}"""
        val session=codec.decodeFromString<Session>(old)
        assertNull(session.lenses);assertTrue(session.preparation.isEmpty());assertTrue(session.equipment.isEmpty())
        for(lenses in listOf(null,false,true)) {
            val saved=session.copy(lenses=lenses,preparation=mapOf("warmup" to TrackerAnswer("0",defaultSessionTrackers()[1])))
            assertEquals(saved,codec.decodeFromString<Session>(codec.encodeToString(saved)))
        }
    }
}
