package dev.riposte.app
import dev.riposte.core.*
import dev.riposte.fencing.*
import dev.riposte.life.*
import dev.riposte.imports.Csv
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.*
class SessionArchiveTest {
    private fun archive(s: Snapshot)=BackupArchive.pack(s) {error("No attachments")}
    @Test fun screenTimeAndSessionSettingsSurviveBackupTogether() {
        val reading=Observation(id="usage-screen-day",metric="screen_time",stamp=Stamp(day="2026-10-07"),value=42.0,
            unit="min",scale="unlocked-screen-minutes",origin=Origin(source="Android Usage Access"))
        val session=Session(name="Evening",stamp=reading.stamp,lenses=false,
            preparation=mapOf("warmup" to TrackerAnswer("0",defaultSessionTrackers()[1])))
        val settings=Settings(screenTimeEnabled=true,lastScreenTimeSync="2026-10-07T18:00:00Z",
            equipmentCategories=listOf(EquipmentCategory("shoes","Shoes")))
        val metric=Metric("screen_time","Screen time","Duration","min",0.0,2880.0,"unlocked-screen-minutes",hidden=true)
        val snapshot=Snapshot(settings=settings,sessions=listOf(session),observations=listOf(reading),metrics=defaultMetrics()+metric)
        assertEquals(snapshot,BackupArchive.unpack(archive(snapshot)).snapshot)
        val upgraded=BackupArchive.unpack(archive(snapshot.copy(version=1))).snapshot
        assertEquals(snapshot,upgraded)
        // The regressed release omitted screen-time settings, but retained generic readings.
        val missingSettings=codec.encodeToString(snapshot).replace("\"screenTimeEnabled\":true,","")
        val loaded=codec.decodeFromString<Snapshot>(missingSettings)
        assertFalse(loaded.settings.screenTimeEnabled)
        assertEquals(listOf(reading),loaded.observations)
        assertEquals(listOf(session),loaded.sessions)
    }
    @Test fun oldBackupPreservesDailyWillingnessAndUnknownSessionValues() {
        val day=TrackingDay("day","2026-10-07","2026-10-07T02:00:00Z","2026-10-08T02:00:00Z","Europe/Rome",willingness=4)
        val old=Snapshot(version=1,days=listOf(day),sessions=listOf(Session(name="Old session",stamp=Stamp(day=day.label))))
        val restored=BackupArchive.unpack(archive(old)).snapshot
        assertEquals(2,restored.version);assertEquals(4,restored.days.single().willingness)
        assertTrue(restored.sessions.single().preparation.isEmpty());assertNull(restored.sessions.single().lenses)
    }
    @Test fun definitionsCategoriesAndAnswersSurviveBackupAndCsv() {
        val original=Metric("mood","Mood")
        val changed=original.edited(original.definition().copy(max=2.0,levels=listOf(RatingLevel(1.0,"Low"),RatingLevel(2.0,"High"))))
        val category=EquipmentCategory("shoes","Shoes, training",archived=true)
        val session=Session(name="Club, evening",stamp=Stamp(day="2026-10-07"),lenses=false,equipment=mapOf("shoes" to "Pair \"A\"\nBlue"),preparation=mapOf("warmup" to TrackerAnswer("0",defaultSessionTrackers()[1])))
        val snapshot=Snapshot(metrics=listOf(changed),sessions=listOf(session),settings=Settings(equipmentCategories=listOf(category)))
        val restored=BackupArchive.unpack(archive(snapshot)).snapshot
        assertEquals(snapshot,restored);assertEquals(original.definition(),restored.metrics.single().atScale(original.scale).definition())
        val csv=Csv.read(sessionsCsv(restored));assertEquals(csv[0].size,csv[1].size)
        assertEquals("false",csv[1][csv[0].indexOf("lenses")]);assertEquals("0",csv[1][csv[0].indexOf("warmup")])
        assertTrue(csv[1][csv[0].indexOf("warmup_definition")].contains("Light warm-up"))
        assertTrue(csv[1][csv[0].indexOf("equipment")].contains("Shoes, training"))
        assertEquals(session.name,csv[1][1])
    }
    @Test fun exportsParticipantOverridesAndUnknownSeparately() {
        val session=Session(name="Test",stamp=Stamp(day="2026-10-07"),lenses=true)
        val bout=Bout(sessionId=session.id,a="me",b="alice",scoreA=5,scoreB=3,stamp=session.stamp,detailsA=session.participantEquipment().copy(lenses=false))
        val s=Snapshot(sessions=listOf(session),fencers=listOf(Fencer("me","Me",true),Fencer("alice","Alice")),bouts=listOf(bout))
        val rows=Csv.read(boutsCsv(s));assertEquals(rows[0].size,rows[1].size)
        assertEquals("false",rows[1][rows[0].indexOf("lenses_a")]);assertEquals("",rows[1][rows[0].indexOf("lenses_b")])
    }
    @Test fun lessonEntriesStayLinkedThroughBackupAndCsvExport() {
        val session=Session(name="Tuesday lesson",stamp=Stamp(day="2026-10-07"))
        val lesson=Lesson(sessionId=session.id,coach="Alex",focus="Distance and timing",notes="Wait for the opening.\nFinish with a lunge.")
        val snapshot=Snapshot(sessions=listOf(session),lessons=listOf(lesson))
        val restored=BackupArchive.unpack(archive(snapshot)).snapshot
        assertEquals(listOf(lesson),restored.lessons)
        assertEquals(snapshot,restored)
        val rows=Csv.read(lessonsCsv(restored))
        assertEquals(rows[0].size,rows[1].size)
        assertEquals(session.id,rows[1][rows[0].indexOf("session_id")])
        assertEquals(session.name,rows[1][rows[0].indexOf("session_name")])
        assertEquals("Alex",rows[1][rows[0].indexOf("coach")])
        assertEquals(lesson.focus,rows[1][rows[0].indexOf("focus")])
        assertEquals(lesson.notes,rows[1][rows[0].indexOf("notes")])
        val oldBackup=BackupArchive.unpack(archive(Snapshot(version=1,sessions=listOf(session)))).snapshot
        assertTrue(oldBackup.lessons.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {snapshot.copy(lessons=listOf(lesson.copy(sessionId="missing"))).validate()}
        assertThrows(IllegalArgumentException::class.java) {snapshot.copy(lessons=listOf(lesson.copy(focus=" "))).validate()}
    }
    @Test fun futureBackupIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {BackupArchive.unpack(archive(Snapshot(version=99)))}
    }
}
