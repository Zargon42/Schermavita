package dev.riposte.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.riposte.core.Stamp
import dev.riposte.fencing.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FencerRatingsDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun latestRatingsSortByWeaponAndOpenProfile() {
        val repo=(compose.activity.application as TrackerApp).repository
        compose.waitUntil(30000) {repo.ready.value}
        val session=Session("s","Club",Stamp(day="2026-10-01"))
        runBlocking {
            repo.change {Snapshot(fencers=listOf(Fencer("me","Me",true),Fencer("alice","Alice"),Fencer("bob","Bob"),Fencer("cara","Cara")),
                sessions=listOf(session,session.copy(id="e",weapon="Épée")),bouts=listOf(
                    Bout("f","s","alice","bob",5,0,stamp=Stamp(day="2026-10-01")),
                    Bout("e","e","alice","bob",0,5,stamp=Stamp(day="2026-10-01"))))}
            repo.ensureDay();repo.selectTab(0)
        }
        compose.waitUntil(10000) {compose.onAllNodes(hasText("Today") and isSelected()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Fencing",useUnmergedTree=true).performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Sort fencers: Most bouts").fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(10000) {compose.onAllNodesWithText("Foil score rating: 1516",substring=true).fetchSemanticsNodes().isNotEmpty()}
        fun sort(current: String,next: String) {
            compose.onNodeWithText("Sort fencers: $current").performScrollTo().performClick()
            compose.onNode(hasText(next) and hasClickAction()).performClick()
        }
        fun above(a: String,b: String) {
            val nodes=compose.onAllNodes(hasText(a) or hasText(b)).fetchSemanticsNodes()
            assertTrue(nodes.size==2)
            // Clipped semantics bounds collapse to the viewport edge for off-screen cards.
            val first=compose.onNodeWithText(a).getUnclippedBoundsInRoot().top.value
            val second=compose.onNodeWithText(b).getUnclippedBoundsInRoot().top.value
            assertTrue("Expected $a ($first) above $b ($second)",first<second)
        }
        sort("Most bouts","Rating: highest first");above("Alice","Bob");above("Bob","Cara")
        sort("Rating: highest first","Rating: lowest first");above("Bob","Alice");above("Alice","Cara")
        compose.onNodeWithText("Épée").performScrollTo().performClick()
        above("Alice","Bob")
        compose.onNodeWithText("Alice").performScrollTo().performClick()
        compose.onNodeWithText("Latest score ratings").performScrollTo().assertIsDisplayed()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Épée score rating: 1484 · 1 eligible bout · Provisional").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Épée score rating: 1484 · 1 eligible bout · Provisional").assertExists()
        compose.onNodeWithText("Sabre score rating: Not rated").assertExists()
    }
}
