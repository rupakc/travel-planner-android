package io.github.rupakc.travelplanner.feature.trips.today

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledSlot
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.Streak
import io.github.rupakc.travelplanner.core.model.trips.Trip
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h2000dp")
class TodayScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<TodayEvent>()
    private val directions = mutableListOf<ScheduledSlot>()
    private var emergency = 0

    private val base = TodayUiState(loading = false, trip = Trip(id = 1, phase = Phase.ON_THE_ROAD, dayIndex = 3), city = "Tokyo")

    private fun show(state: TodayUiState, withQuick: Boolean = true) =
        compose.setContent {
            TravelTheme(ThemeMode.LIGHT) {
                TodayScreen(state, { events += it }, { directions += it }, if (withQuick) ({ emergency++ }) else null, if (withQuick) ({}) else null)
            }
        }

    @Test
    fun `shows the day number, the city, check in and the emergency button`() {
        show(base)
        compose.onNodeWithText("Day 3").assertIsDisplayed()
        compose.onNodeWithText("Tokyo").assertIsDisplayed()
        compose.onNodeWithText("Nothing planned for today.").assertIsDisplayed()
        compose.onNodeWithText("Check in for today").performClick()
        assertEquals(TodayEvent.CheckIn, events.last())
        compose.onNodeWithText("Emergency info").performClick()
        assertEquals(1, emergency)
    }

    @Test
    fun `after check-in the button is disabled and the new country banner shows`() {
        show(base.copy(checkedIn = true, newCountry = true, streak = Streak(current = 4)))
        compose.onNodeWithText("Checked in today").assertIsNotEnabled()
        compose.onNodeWithText("Check-in streak: 4").assertIsDisplayed()
        compose.onNodeWithText("New country unlocked! It's on your passport now.").assertIsDisplayed()
    }

    @Test
    fun `out of trip shows its message and quick actions hide without lambdas`() {
        show(base.copy(failure = CheckinFailure(TodayRules.CheckinProblem.OutOfTrip)), withQuick = false)
        compose.onNodeWithText("Today is outside this trip's dates, so there's nothing to check in to.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTextCount("Emergency info"))
    }

    @Test
    fun `next up offers directions`() {
        show(base.copy(nextUp = ScheduledSlot(ItinerarySlot(activity = "Senso-ji", startTime = "09:00"))))
        compose.onNodeWithText("Next up").assertIsDisplayed()
        compose.onNodeWithText("Directions").performClick()
        assertEquals("Senso-ji", directions.single().slot.activity)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String) =
        onAllNodesWithText(t).fetchSemanticsNodes().size
}
