package io.github.rupakc.travelplanner.feature.trips.journal

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.trips.Entry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h3000dp")
class JournalScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<JournalEvent>()

    private fun show(state: JournalUiState) = compose.setContent { TravelTheme(ThemeMode.LIGHT) { JournalScreen(state, { events += it }) } }

    private val place = JournalRules.PlaceRow("activity:senso-ji", "Senso-ji", "activity", false, 2, null, null, null, null, pending = false)

    @Test
    fun `entries, a waiting marker, places and stars are shown and send events`() {
        show(
            JournalUiState(
                loading = false,
                rows =
                    listOf(
                        JournalRules.Row(Entry(1, "a", "2026-11-13", "Lovely temple"), false),
                        JournalRules.Row(Entry(-1, "b", "2026-11-13", "Queued words"), true),
                    ),
                places = listOf(place),
            ),
        )
        compose.onNodeWithText("Lovely temple").assertIsDisplayed()
        compose.onNodeWithText("Queued words").assertIsDisplayed()
        compose.onNodeWithText("Waiting to sync").assertExists()
        compose.onNodeWithContentDescription("Rate Senso-ji, 4 of 5 stars").performClick()
        assertEquals(JournalEvent.Rate("activity:senso-ji", 4), events.last())
        compose.onNodeWithContentDescription("Visited Senso-ji").performClick()
        assertEquals(JournalEvent.SetVisited("activity:senso-ji", true), events.last())
    }

    @Test
    fun `save is disabled for an empty draft and the empty states show`() {
        show(JournalUiState(loading = false))
        compose.onNodeWithText("No entries yet. Write the first one.").assertIsDisplayed()
        compose.onNodeWithText("Save entry").assertIsNotEnabled()
    }

    @Test
    fun `a limit message is shown`() {
        show(JournalUiState(loading = false, failure = JournalFailure(JournalRules.Problem.JournalLimit)))
        compose.onNodeWithText("This trip has reached the 500 entry limit.").assertIsDisplayed()
    }
}
