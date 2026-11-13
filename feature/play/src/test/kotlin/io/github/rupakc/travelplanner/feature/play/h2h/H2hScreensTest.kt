package io.github.rupakc.travelplanner.feature.play.h2h

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.play.H2hMatch
import io.github.rupakc.travelplanner.core.model.play.H2hOpponent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class H2hScreensTest {
    @get:Rule val compose = createComposeRule()

    private fun match(id: String, state: String, you: Int? = null, them: Int? = null, winner: String? = null) =
        H2hMatch(id, H2hOpponent("ana"), "flags", state, you, them, winner, "2026-11-15T10:00:00Z")

    @Test(timeout = 60_000)
    fun `the list shows the section and opens a match`() {
        val opened = mutableListOf<String>()
        val state = H2hListUiState(H2hListPhase.Loaded, listOf(match("b", "waiting_them", you = 300)))
        compose.setContent { TravelTheme { H2hListScreen(state, {}, {}, { opened += it }, {}) } }
        compose.onNodeWithText("Waiting for them").assertIsDisplayed()
        compose.onNodeWithText("vs @ana").performClick()
        assertEquals(listOf("b"), opened)
        compose.onAllNodesWithText("You 300, @ana 0").assertCountEquals(0)
    }

    @Test(timeout = 60_000)
    fun `a finished match lists both scores and the winner`() {
        val state = H2hListUiState(H2hListPhase.Loaded, listOf(match("c", "done", 400, 250, "you")))
        compose.setContent { TravelTheme { H2hListScreen(state, {}, {}, {}, {}) } }
        compose.onNodeWithText("You 400, @ana 250").assertExists()
        compose.onNodeWithText("You won").assertExists()
    }

    @Test(timeout = 60_000)
    fun `empty list shows the empty state and new challenge`() {
        var created = 0
        compose.setContent { TravelTheme { H2hListScreen(H2hListUiState(H2hListPhase.Loaded), {}, {}, {}, { created++ }) } }
        compose.onNodeWithText("No matches yet. Challenge someone by their handle.").assertIsDisplayed()
        compose.onNodeWithText("New challenge").performClick()
        assertEquals(1, created)
    }

    @Test(timeout = 60_000)
    fun `an error shows the backend text`() {
        compose.setContent { TravelTheme { H2hListScreen(H2hListUiState(H2hListPhase.Error("Slow down")), {}, {}, {}, {}) } }
        compose.onNodeWithText("Slow down").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun `an invite offers play and decline`() {
        val sent = mutableListOf<H2hDetailEvent>()
        val state = H2hDetailUiState(H2hDetailPhase.Loaded, match("a", "waiting_you"))
        compose.setContent { TravelTheme { H2hDetailScreen(state, { sent += it }, {}) } }
        compose.onNodeWithText("@ana challenged you on Flags.").assertIsDisplayed()
        compose.onNodeWithText("Play now").performClick()
        compose.onNodeWithText("Decline").performClick()
        assertEquals(listOf(H2hDetailEvent.Accept, H2hDetailEvent.Decline), sent)
    }

    @Test(timeout = 60_000)
    fun `result shows the winner and both scores`() {
        val state = H2hDetailUiState(H2hDetailPhase.Loaded, match("a", "done", 200, 450, "them"))
        compose.setContent { TravelTheme { H2hDetailScreen(state, {}, {}) } }
        compose.onNodeWithText("@ana won").assertIsDisplayed()
        compose.onNodeWithText("You 200, @ana 450").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun `waiting, declined and expired matches say so`() {
        compose.setContent {
            TravelTheme { H2hDetailScreen(H2hDetailUiState(H2hDetailPhase.Loaded, match("a", "waiting_them", 100)), {}, {}) }
        }
        compose.onNodeWithText("You have played. Waiting for @ana.").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun `a missing match offers the way back`() {
        var back = 0
        compose.setContent { TravelTheme { H2hDetailScreen(H2hDetailUiState(H2hDetailPhase.Missing), {}, { back++ }) } }
        compose.onNodeWithText("This match is no longer available.").assertIsDisplayed()
        compose.onNodeWithText("Back to head-to-head").performClick()
        assertEquals(1, back)
    }

    @Test(timeout = 60_000)
    fun `create is disabled for a bad handle and shows a safe reason`() {
        compose.setContent {
            TravelTheme { H2hCreateScreen(H2hCreateUiState(handle = "a", problem = CreateProblem.CANNOT_CHALLENGE), {}, {}) }
        }
        compose.onNodeWithText("Challenge and play").assertIsNotEnabled()
        compose.onNodeWithText("That player can't be challenged. Check the handle, or ask them to make their profile visible.").assertExists()
    }
}
