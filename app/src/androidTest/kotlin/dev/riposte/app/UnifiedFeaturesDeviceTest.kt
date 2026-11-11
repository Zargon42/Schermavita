package dev.riposte.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
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

@RunWith(AndroidJUnit4::class)
class UnifiedFeaturesDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val repo get()=(compose.activity.application as TrackerApp).repository
    @Before fun reset() {compose.waitUntil(30000) {repo.ready.value};runBlocking {repo.change {Snapshot()};repo.ensureDay();repo.selectTab(0)}}
    @Test fun reflectionPromptPersistsAnswersAndJournalDoesNotActivateSession() {
        val session=Session("review","Goal practice",Stamp(day="2026-10-01"),goals=listOf(SessionGoal("g","Maintain distance")))
        runBlocking {repo.saveSession(session);repo.change {it.copy(reflectionQuestions=listOf(ReflectionQuestion("q","What worked?")))}}
        compose.onNodeWithText("Fencing",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Close session").performScrollTo().performClick()
        compose.onNodeWithText("Reflection notes").performScrollTo().performTextInput("Better distance today")
        compose.onNodeWithText("Answer").performScrollTo().performTextInput("Patient preparation")
        compose.onNodeWithText("Save and close").performScrollTo().performClick()
        compose.waitUntil(10000) {repo.state.value.settings.activeSession.isBlank()}
        assertEquals("Complete",repo.state.value.sessions.single().reflection!!.status)
        val reopened=AppRepository(compose.activity);runBlocking {reopened.load()}
        assertEquals("Patient preparation",reopened.state.value.sessions.single().reflection!!.answers.single().value)
        compose.onNodeWithText("Session journal").performScrollTo().performClick()
        compose.onNodeWithText("Better distance today").assertExists()
        compose.onNodeWithText("Session details").performScrollTo().performClick()
        assertTrue(repo.state.value.settings.activeSession.isBlank())
    }
    @Test fun compatibleChartsCombineAndOfferSeparateAndRollingViews() {
        val old=Metric("m","Mood");val current=old.edited(old.definition().copy(unit="",levels=old.definition().levels.map {it.copy(label="New ${it.value}")}))
        val events=(1..4).map {i->Observation("e$i","m",Stamp(day="2026-10-0$i"),i.toDouble(),scale=if(i<3) old.scale else current.scale)}
        compose.activity.runOnUiThread {compose.activity.setContent {MaterialTheme {InsightsScreen(Snapshot(metrics=listOf(current),observations=events))}}}
        compose.onNodeWithText("Life",substring=false).performClick()
        compose.onNodeWithText("Trend: Choose").performScrollTo().performClick()
        compose.onNode(hasText("Mood",substring=false) and hasClickAction()).performClick()
        compose.onNodeWithText("4 observed days",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("7",substring=false).performScrollTo().performClick()
        compose.onAllNodesWithText("7-day mean",substring=true).onFirst().assertExists()
        compose.onNodeWithText("Mood timeline · Rating").performScrollTo()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(compose.activity.cacheDir,"combined-insights.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        compose.onNodeWithText("Separate",substring=false).performScrollTo().performClick()
        assertEquals(2,compose.onAllNodesWithText("2 observed days · 2 displayed values",substring=false).fetchSemanticsNodes().size)
    }
    @Test fun historicalMoodAppearsWithHappinessAndCanBeSeparated() {
        val current=Metric("mood","Mood (Happiness)")
        val historical=Metric("legacy:unified_mood","Mood · history","Number",min=-1e12,max=1e12,scale="rating-1-5",hidden=true)
        val observations=listOf(
            Observation("old",historical.id,Stamp(day="2026-10-01"),2.0,scale=historical.scale,origin=Origin(dateBasis="calendar")),
            Observation("new",current.id,Stamp(day="2026-10-02"),4.0,scale=current.scale)
        )
        compose.activity.runOnUiThread {compose.activity.setContent {MaterialTheme {InsightsScreen(Snapshot(metrics=listOf(current,historical),observations=observations))}}}
        compose.onNodeWithText("Life",substring=false).performClick()
        compose.onNodeWithText("Mood (Happiness) timeline · Rating").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 observed days · 2 displayed values").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Mood · history",substring=true).onFirst().assertExists()
        compose.onNodeWithText("Separate",substring=false).performScrollTo().performClick()
        assertEquals(2,compose.onAllNodesWithText("1 observed days",substring=true).fetchSemanticsNodes().size)
    }
    @Test fun processRatingAndGoalFlagsStaySeparateFromSelfEvaluation() {
        val session=Session("process","Process practice",Stamp(day="2026-10-01"),goals=listOf(SessionGoal("g","Maintain distance"),SessionGoal("h","Stay patient")))
        runBlocking {repo.saveSession(session)}
        compose.onNodeWithText("Fencing",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Add bout",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Plan/process execution",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Rating, tactics & more").performScrollTo().performClick()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).onFirst().performSemanticsAction(SemanticsActions.SetProgress) {it(4f)}
        compose.onAllNodesWithText("Yes",substring=false).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Hide extra details").performScrollTo().performClick()
        compose.onNodeWithText("Plan/process execution",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Opponent",substring=false).performScrollTo().performTextInput("Alice")
        compose.onNodeWithText("Your score").performScrollTo().performTextInput("5")
        compose.onNodeWithText("Opponent score").performScrollTo().performTextInput("3")
        compose.onNodeWithText("Save bout").performScrollTo().performClick()
        compose.waitUntil(10000) {repo.state.value.bouts.isNotEmpty()}
        val details=repo.state.value.bouts.single().detailsA
        assertEquals(4.0,details.process!!,0.0);assertNull(details.rating)
        assertEquals(true,details.goalAnswers.first {it.id=="g"}.applied)
        assertNull(details.goalAnswers.first {it.id=="h"}.applied)
        runBlocking {repo.saveSession(repo.state.value.sessions.single().copy(goals=listOf(SessionGoal("g","Changed goal"))))}
        assertEquals("Maintain distance",repo.state.value.bouts.single().detailsA.goalAnswers.first {it.id=="g"}.text)
    }
    @Test fun equipmentProfilesRetainBrokenHistoryAndCanCreateConfiguration() {
        val session=Session("equipment","Practice",Stamp(day="2026-10-01"),sword="Club foil")
        runBlocking {repo.saveSession(session);repo.saveFencer(Fencer("op","Opponent"));repo.saveBout(Bout("b",session.id,"me","op",5,2,stamp=session.stamp,detailsA=session.participantEquipment()))}
        compose.onNodeWithText("Fencing",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Equipment",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Club foil",substring=true).performClick()
        compose.onNodeWithText("Edit",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Blade type").performTextInput("BF")
        compose.onNode(hasAnySibling(hasText("Broken")) and isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Save",substring=false).performClick()
        compose.waitUntil(10000) {repo.state.value.equipmentProfiles.any {it.retired}}
        assertEquals(1,repo.state.value.bouts.size)
        compose.onNodeWithText("Create from this foil").performScrollTo().performClick()
        compose.onNodeWithText("Save",substring=false).performClick()
        compose.waitUntil(10000) {repo.state.value.equipmentProfiles.size==2}
        assertEquals("BF",repo.state.value.equipmentProfiles.last().blade)
        assertFalse(repo.state.value.equipmentProfiles.last().retired)
    }
}
