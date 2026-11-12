package io.github.rupakc.travelplanner.feature.play

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PlayScreenTest {
    @get:Rule val compose = createComposeRule()

    private val loaded =
        PlayUiState(
            phase = PlayPhase.Loaded,
            level = 2,
            xp = 150,
            xpIntoLevel = 50,
            xpForLevel = 150,
            progress = 1f / 3f,
            remainingXp = 100,
            badges =
                listOf(
                    BadgeUi("first_trip", 1, 1, "2026-05-01T10:00:00Z", BadgeTier.GOLD),
                    BadgeUi("mystery_badge", 2, 5, null, null),
                ),
        )

    @Test fun `tier is shown as text and an unknown badge falls back to its words`() {
        compose.setContent { TravelTheme { PlayScreen(loaded, onEvent = {}) } }
        compose.onNodeWithText("Level 2").assertIsDisplayed()
        compose.onNodeWithText("100 XP to level 3").assertIsDisplayed()
        compose.onNodeWithText("First trip").assertIsDisplayed()
        compose.onNodeWithText("Gold").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Mystery badge"))
        compose.onNodeWithText("Mystery badge").assertIsDisplayed()
    }

    @Test fun `error retry sends the event`() {
        val events = mutableListOf<PlayEvent>()
        compose.setContent { TravelTheme { PlayScreen(PlayUiState(phase = PlayPhase.Error("Too many requests")), onEvent = { events += it }) } }
        compose.onNodeWithText("Too many requests").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf<PlayEvent>(PlayEvent.Retry), events)
    }

    @Test fun `hidden draws no game content`() {
        compose.setContent { TravelTheme { PlayScreen(PlayUiState(phase = PlayPhase.Hidden), onEvent = {}) } }
        compose.onNodeWithText("Level 1").assertDoesNotExist()
    }
}
