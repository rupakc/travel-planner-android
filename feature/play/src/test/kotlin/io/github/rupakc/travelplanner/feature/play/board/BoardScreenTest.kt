package io.github.rupakc.travelplanner.feature.play.board

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.play.BoardRow
import io.github.rupakc.travelplanner.core.model.play.BoardScope
import io.github.rupakc.travelplanner.core.model.play.BoardYou
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class BoardScreenTest {
    @get:Rule val compose = createComposeRule()

    private val rows = listOf(BoardRow(1, "ana", 900, level = 4), BoardRow(2, "bo", 800, you = true))

    @Test(timeout = 60_000)
    fun `rows show handles only, your row is marked and the level only when sent`() {
        val state = BoardUiState(phase = BoardPhase.Loaded, weekLabel = "2026-W46", rows = rows, you = BoardYou(2, 800, true))
        compose.setContent { TravelTheme { BoardScreen(state, {}, {}, {}) } }
        compose.onNodeWithText("Week 2026-W46").assertIsDisplayed()
        compose.onNodeWithText("1. @ana").assertIsDisplayed()
        compose.onNodeWithText("2. @bo (you)").assertExists()
        compose.onNodeWithText("Level 4").assertIsDisplayed()
        compose.onNodeWithText("Your rank this week: 2").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun `the opt-in prompt links to the profile`() {
        var opened = 0
        val state = BoardUiState(phase = BoardPhase.Loaded, rows = rows, you = BoardYou(null, 0, false))
        compose.setContent { TravelTheme { BoardScreen(state, {}, {}, { opened++ }) } }
        compose.onNodeWithText("Join the global board").assertIsDisplayed()
        compose.onNodeWithText("Open my profile").performClick()
        assertEquals(1, opened)
    }

    @Test(timeout = 60_000)
    fun `without a profile lambda there is no profile button`() {
        val state = BoardUiState(phase = BoardPhase.Loaded, rows = rows, you = null)
        compose.setContent { TravelTheme { BoardScreen(state, {}, {}, null) } }
        compose.onNodeWithText("Boards show handles only. Create a profile to take part.").assertIsDisplayed()
        compose.onAllNodesWithText("Open my profile").assertCountEquals(0)
    }

    @Test(timeout = 60_000)
    fun `friends empty state and scope switch`() {
        val sent = mutableListOf<BoardEvent>()
        val state = BoardUiState(scope = BoardScope.FRIENDS, phase = BoardPhase.Loaded, you = BoardYou(null, 0, true))
        compose.setContent { TravelTheme { BoardScreen(state, { sent += it }, {}, {}) } }
        compose.onNodeWithText("None of your friends are on the board yet.").assertIsDisplayed()
        compose.onNodeWithText("Global").performClick()
        assertEquals(listOf<BoardEvent>(BoardEvent.Scope(BoardScope.GLOBAL)), sent)
    }

    @Test(timeout = 60_000)
    fun `an error shows the backend text`() {
        compose.setContent { TravelTheme { BoardScreen(BoardUiState(phase = BoardPhase.Error("Slow down")), {}, {}, {}) } }
        compose.onNodeWithText("Slow down").assertIsDisplayed()
    }
}
