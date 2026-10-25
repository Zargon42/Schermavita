package dev.riposte.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.riposte.core.*
import dev.riposte.fencing.*
import dev.riposte.life.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class TrackerDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val repo get() = (compose.activity.application as TrackerApp).repository
    @Before fun resetSyntheticData() {
        compose.waitUntil(30000) {repo.ready.value}
        runBlocking {repo.change {Snapshot()};repo.ensureDay();repo.selectTab(0)}
        compose.waitForIdle()
        compose.waitUntil(10000) {compose.onAllNodes(hasText("Today") and isSelected()).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun openFencing() {
        compose.onNodeWithText("Fencing",useUnmergedTree=true).performClick()
        // Tab selection writes DataStore asynchronously; Compose idleness alone is insufficient.
        compose.waitUntil(10000) {compose.onAllNodesWithText("New session").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun fastSessionAndBoutFlow() {
        openFencing()
        compose.onAllNodesWithText("New session").onFirst().performClick()
        compose.onNodeWithText("Session name").performTextReplacement("Club night")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) {repo.state.value.sessions.isNotEmpty()}
        compose.onNodeWithText("Add bout").performClick()
        compose.onNodeWithText("Opponent").performTextInput("Alice")
        compose.onNodeWithText("Your score").performTextInput("15")
        compose.onNodeWithText("Opponent score").performTextInput("12")
        compose.onNodeWithText("Save bout").performScrollTo().performClick()
        compose.waitUntil(10000) {repo.state.value.bouts.size==1}
        assertEquals(15,repo.state.value.bouts.single().scoreA)
        assertEquals("Alice",repo.state.value.name(repo.state.value.bouts.single().b))
    }
    @Test fun closeSessionPreservesHistoryPersistsAndCanResume() {
        val session=Session("closed-session","Evening practice",Stamp(day="2026-10-01"),sword="Foil 1",notes="Keep these notes")
        val bout=Bout("closed-bout",session.id,"me","alice",5,3,stamp=session.stamp)
        val lesson=Lesson(sessionId=session.id,focus="Distance")
        runBlocking {
            repo.saveFencer(Fencer("alice","Alice"));repo.saveSession(session);repo.saveBout(bout)
            repo.change {it.copy(lessons=listOf(lesson))}
        }
        openFencing()
        compose.onNodeWithText("Close session").performScrollTo().performClick()
        compose.waitUntil(10000) {repo.state.value.settings.activeSession.isBlank()}
        compose.onNodeWithText("No active session.",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add bout").assertDoesNotExist()
        val reopened=AppRepository(compose.activity.applicationContext)
        runBlocking {reopened.load()}
        assertEquals("",reopened.state.value.settings.activeSession)
        assertEquals(listOf(session),reopened.state.value.sessions)
        assertEquals(listOf(bout),reopened.state.value.bouts)
        assertEquals(listOf(lesson),reopened.state.value.lessons)
        compose.onNodeWithText("Evening practice").performScrollTo().performClick()
        compose.waitUntil(10000) {repo.state.value.settings.activeSession==session.id}
        compose.onNodeWithText("Close session").performScrollTo().assertIsDisplayed()
        assertEquals(1,repo.state.value.sessions.size)
    }
    @Test fun storageReloadAndThirdPartyIsolation() = runBlocking {
        val day=repo.ensureDay();val stamp=DayClock.stamp(Instant.now(),repo.state.value.settings,repo.state.value.days)
        val session=Session(name="Test",stamp=stamp,sword="first")
        repo.saveSession(session);repo.saveFencer(Fencer("alice","Alice"));repo.saveFencer(Fencer("bob","Bob"))
        val personal=Bout(sessionId=session.id,a="me",b="alice",scoreA=5,scoreB=3,stamp=stamp,detailsA=ParticipantDetails(sword=session.sword))
        repo.saveBout(personal);repo.saveBout(personal)
        repo.saveBout(Bout(sessionId=session.id,a="alice",b="bob",scoreA=5,scoreB=4,stamp=stamp))
        repo.saveSession(session.copy(sword="second"))
        val reopened=AppRepository(compose.activity.applicationContext);reopened.load()
        assertEquals(2,reopened.state.value.bouts.size)
        assertEquals("first",reopened.state.value.bouts.first {it.id==personal.id}.detailsA.sword)
        assertEquals(1,statistics(reopened.state.value.bouts,"me").bouts)
        val packed=BackupArchive.pack(reopened.state.value,reopened::sourceFile)
        val encrypted=BackupCipher.encrypt(packed,"test-password".toCharArray())
        val restored=BackupArchive.unpack(BackupCipher.decrypt(encrypted,"test-password".toCharArray()))
        assertEquals(2,restored.snapshot.bouts.size)
    }
    @Test fun quickMoodIsSavedAsMomentary() {
        compose.onAllNodesWithText("Mood",useUnmergedTree=true).onFirst().performClick()
        compose.onNodeWithText("4",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) {repo.state.value.observations.any {it.metric=="mood"}}
        assertEquals("momentary",repo.state.value.observations.first {it.metric=="mood"}.scope)
        assertEquals(4.0,repo.state.value.observations.first {it.metric=="mood"}.value!!,0.0)
    }
    @Test fun backupRejectsTraversalAndImportRepeatsDoNotDuplicate() = runBlocking {
        val person=Fencer("alice","Alice");val session=Session("session","Imported",Stamp(day="2026-10-01"))
        val snapshot=Snapshot(fencers=listOf(Fencer("me","Me",true),person),sessions=listOf(session),bouts=listOf(Bout("bout",session.id,"me",person.id,5,3,stamp=session.stamp)))
        repo.change {mergeHistory(it,snapshot)};repo.change {mergeHistory(it,snapshot)}
        assertEquals(1,repo.state.value.bouts.size)
        val out=java.io.ByteArrayOutputStream();java.util.zip.ZipOutputStream(out).use {it.putNextEntry(java.util.zip.ZipEntry("../bad"));it.write(byteArrayOf(1));it.closeEntry()}
        val rejected=assertThrows(Exception::class.java) {BackupArchive.unpack(out.toByteArray())}
        assertTrue(rejected is IllegalArgumentException || rejected is java.util.zip.ZipException)
        Unit
    }
    @Test fun preparationAndCustomEquipmentSurviveSessionAndBoutEntry() {
        openFencing()
        compose.onAllNodesWithText("New session").onFirst().performClick()
        compose.onNodeWithText("1 · Not at all").performScrollTo().performClick()
        compose.onNodeWithText("0 · None").performScrollTo().performClick()
        compose.onNodeWithText("Bike").performScrollTo().performClick()
        compose.onNodeWithText("No",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Manage equipment categories").performScrollTo().performClick()
        compose.onNodeWithText("New category").performTextInput("Shoes")
        compose.onNodeWithText("Add category").performClick()
        compose.onAllNodesWithText("Save").onLast().performClick()
        compose.onNodeWithText("Shoes").performScrollTo().performTextInput("Blue pair")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) {repo.state.value.sessions.isNotEmpty()}
        val session=repo.state.value.sessions.single()
        assertEquals(false,session.lenses);assertEquals("0.0",session.preparation["warmup"]!!.value)
        assertEquals("Bike",session.preparation["transport"]!!.value)
        assertEquals("Blue pair",session.equipment.values.single())
        compose.onNodeWithText("Add bout").performClick()
        compose.onNodeWithText("Opponent").performTextInput("Alice")
        compose.onNodeWithText("Your score").performTextInput("5")
        compose.onNodeWithText("Opponent score").performTextInput("3")
        compose.onNodeWithText("Save bout").performScrollTo().performClick()
        compose.waitUntil(10000) {repo.state.value.bouts.isNotEmpty()}
        assertEquals(session.equipment,repo.state.value.bouts.single().detailsA.equipment)
        assertEquals(false,repo.state.value.bouts.single().detailsA.lenses)
        assertNull(repo.state.value.bouts.single().detailsB.lenses)
        runBlocking {
            repo.saveSession(session.copy(lenses=true,equipment=emptyMap()))
            val reopened=AppRepository(compose.activity.applicationContext);reopened.load()
            assertEquals(false,reopened.state.value.bouts.single().detailsA.lenses)
            assertEquals(session.preparation,reopened.state.value.sessions.single().preparation)
        }
    }

    @Test fun trackerEditPreservesHistoricalEntryAndReviewScale() {
        val old=repo.state.value.metrics.first {it.id=="mood"}
        val day=runBlocking {repo.ensureDay()}.label
        val id="review:$day:mood"
        runBlocking {repo.change {it.copy(observations=listOf(Observation(id,"mood",DayClock.stamp(Instant.now(),it.settings,it.days,day),5.0,"Original scale entry","daily",scale=old.scale)))}}
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Configure trackers").performScrollTo().performClick()
        compose.onAllNodesWithText("Edit").onFirst().performClick()
        compose.onNodeWithText("Levels · one value = label per line").performTextReplacement("1 = Low\n2 = Okay\n3 = High")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) {repo.state.value.metrics.first {it.id=="mood"}.scale!=old.scale}
        val current=repo.state.value.metrics.first {it.id=="mood"}
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onAllNodesWithText("Mood",useUnmergedTree=true).onFirst().performClick()
        compose.onNodeWithText("3 · High").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) {repo.state.value.observations.size==2}
        assertEquals(current.scale,repo.state.value.observations.first {it.id!=id}.scale)
        compose.onNodeWithText("Original scale entry").performScrollTo().performClick()
        compose.onNode(hasText("5") and isSelected()).assertExists()
        compose.onNodeWithText("4").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) {repo.state.value.observations.first {it.id==id}.value==4.0}
        compose.onNodeWithText("Review day").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) {repo.state.value.reviews.isNotEmpty()}
        assertEquals(old.scale,repo.state.value.observations.first {it.id==id}.scale)
        assertEquals(4.0,repo.state.value.observations.first {it.id==id}.value!!,0.0)
    }

}
