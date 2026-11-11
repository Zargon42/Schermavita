package dev.riposte.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.riposte.core.*
import dev.riposte.fencing.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class QuieterChartsDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val repo get()=(compose.activity.application as TrackerApp).repository
    @Before fun reset() {compose.waitUntil(30000) {repo.ready.value};runBlocking {repo.change {Snapshot()};repo.ensureDay();repo.selectTab(0)}}

    @Test fun rangeNavigationAndIndividualDaysUseVisibleCounts() {
        val start=LocalDate.of(2024,1,1)
        val dots=(0L..90L).map {i->ChartDot(start.plusDays(i).toEpochDay().toDouble(),(i%5+1).toDouble(),"Mood","Day $i")}
        compose.activity.runOnUiThread {compose.activity.setContent {MaterialTheme {ScrollPage {
            ObservationChart(dots,"Recorded date","Mood",dateAxis=true,yBounds=1.0 to 5.0)
        }}}}
        compose.onNodeWithText("Month",substring=false).assertIsSelected()
        compose.onAllNodesWithText("2024-03-01 – 2024-03-31").onFirst().assertExists()
        compose.onNodeWithText("Previous period").performClick()
        compose.onAllNodesWithText("2024-02-01 – 2024-02-29").onFirst().assertExists()
        compose.onNodeWithText("29 observed days · 29 displayed values").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Mood timeline; Month; 29 displayed values; line chart. Use Inspect first point and Next point for values.")
            .performScrollTo().performTouchInput {swipeLeft()}
        compose.onAllNodesWithText("2024-03-01 – 2024-03-31").onFirst().assertExists()
        compose.onNodeWithText("3M",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("91 observed days · 13 displayed values").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Individual days").performScrollTo().performClick()
        compose.onNodeWithText("91 observed days · 91 displayed values").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Mood timeline; 3M; 91 displayed values; individual days. Use Inspect first point and Next point for values.").assertExists()
        compose.onNodeWithText("All time",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Previous period").assertIsNotEnabled()
        compose.onNodeWithText("Next period").assertIsNotEnabled()
        compose.onNodeWithText("91 observed days · 3 displayed values").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Jump to today").performScrollTo().performClick()
        compose.onNodeWithText("Month",substring=false).assertIsSelected()
        val currentMonth=LocalDate.now().withDayOfMonth(1)
        compose.onNodeWithText("$currentMonth – ${currentMonth.plusMonths(1).minusDays(1)}").assertExists()
        compose.onNodeWithText("Last 30 days",substring=false).performScrollTo().performClick()
        val today=LocalDate.now()
        compose.onNodeWithText("${today.minusDays(29)} – $today").assertExists()
    }

    @Test fun savedGoalAnswersSurviveEditingWithDetailsClosed() {
        val session=Session("s","Practice",Stamp(day="2026-10-01"),goals=listOf(SessionGoal("g","Maintain distance")))
        val details=ParticipantDetails(process=4.5,goalAnswers=listOf(GoalAnswer("g","Maintain distance",true)))
        val bout=Bout("b","s","me","op",5,2,stamp=session.stamp,detailsA=details)
        val snapshot=Snapshot(sessions=listOf(session),fencers=listOf(Fencer("me","Me",true),Fencer("op","Opponent")),bouts=listOf(bout))
        var saved: Bout?=null
        compose.activity.runOnUiThread {compose.activity.setContent {MaterialTheme {
            BoutForm(snapshot,bout,{}, {value,_->saved=value},{})
        }}}
        compose.onNodeWithText("Goal: Maintain distance").assertExists()
        compose.onNodeWithText("Plan/process execution",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Save bout").performScrollTo().performClick()
        compose.runOnIdle {assertNotNull(saved);assertEquals(details,saved!!.detailsA)}
    }

    @Test fun manualScoreConfirmationReflectsSavedTrackers() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Add manual score trackers").performScrollTo().performClick()
        compose.waitUntil(10000) {repo.state.value.metrics.count {it.id in setOf("manual_readiness","manual_sleep_score")}==2}
        compose.onNodeWithText("Manual score trackers added").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Readiness and Sleep score trackers are saved. Enter scores in Today > Quick entry.").assertExists()
        val reopened=AppRepository(compose.activity);runBlocking {reopened.load()}
        assertEquals(2,reopened.state.value.metrics.count {it.id in setOf("manual_readiness","manual_sleep_score")})
    }

    @Test fun denseMixedRevisionChartsCaptureBothThemesWithOverlays() {
        val start=LocalDate.of(2024,1,1)
        val dots=(0L..90L).flatMap {i->listOf("tracking","calendar").map {basis->
            val revision=if(i%10<5) "Old Mood 1–5" else "New Mood 1–5"
            ChartDot(start.plusDays(i).toEpochDay().toDouble(),(i%5+1).toDouble(),"$revision · $basis","Day $i",
                basis=basis,trendSeries="Mood",colorKey=revision)
        }}
        for(dark in listOf(false,true)) {
            compose.activity.runOnUiThread {compose.activity.setContent {
                androidx.compose.runtime.key(dark) { MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {Surface {ScrollPage {
                    ObservationChart(dots,"Recorded date","Mood",dateAxis=true,yBounds=1.0 to 5.0)
                }}}}
            }}
            compose.onNodeWithText("3M",substring=false).performScrollTo().performClick()
            compose.onNodeWithText("7",substring=false).performScrollTo().performClick()
            compose.onNodeWithText("Regression line").performScrollTo().performClick()
            compose.onNodeWithText("Multiple compatible revisions").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Inspect first point").performScrollTo().performClick()
            compose.onNodeWithText("Previous point").assertExists()
            compose.onNodeWithText("Y: Mood · 1 to 5").performScrollTo()
            val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
            java.io.File(compose.activity.cacheDir,"quiet-chart-${if(dark) "dark" else "light"}.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        }
    }
}
