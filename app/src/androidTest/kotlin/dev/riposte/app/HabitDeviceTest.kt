package dev.riposte.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    private fun visible(text: String): SemanticsNodeInteraction {
        compose.waitUntil(15000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().size==1}
        return compose.onNodeWithText(text)
    }

    @Test fun createLogUndoCorrectAndReloadFromHabitsTab() {
        val repo=(compose.activity.application as TrackerApp).repository
        compose.waitUntil(30000) {repo.ready.value}
        runBlocking {repo.change {Snapshot()}; repo.ensureDay(); repo.selectTab(0)}
        compose.onNodeWithText("Habits",useUnmergedTree=true).performClick()
        visible("Add habit").performClick()
        visible("Habit name").performTextInput("Stretch")
        visible("Save").performClick()
        visible("Check in").performClick()
        visible("Undo").performClick()
        compose.waitUntil(10000) {repo.state.value.habitEntries.isEmpty()}
        visible("Stretch").performClick()
        visible("Add entry").performScrollTo().performClick()
        visible("Save").performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty()}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Check-in"))
        visible("Check-in").performScrollTo().performClick()
        visible("Remove entry").performScrollTo().performClick()
        compose.waitUntil(10000) {repo.state.value.habitEntries.isEmpty()}
        visible("Add entry").performScrollTo().performClick()
        visible("Save").performClick()
        compose.waitUntil(10000) {repo.state.value.habitEntries.size==1}
        val reopened=AppRepository(compose.activity)
        runBlocking {reopened.load()}
        assertEquals("Stretch",reopened.state.value.habits.single().name)
        assertEquals(repo.state.value.habitEntries,reopened.state.value.habitEntries)
    }
}

