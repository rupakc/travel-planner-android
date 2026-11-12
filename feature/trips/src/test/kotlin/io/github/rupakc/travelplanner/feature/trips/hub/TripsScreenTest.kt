package io.github.rupakc.travelplanner.feature.trips.hub

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.PrepSummary
import io.github.rupakc.travelplanner.core.model.trips.Trip
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class TripsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<TripsEvent>()

    private fun show(state: TripsUiState, dark: Boolean = false) =
        compose.setContent { TravelTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { TripsScreen(state, { events += it }) } }

    private val lisbon =
        Trip(1, "Lisbon in spring", "Lisbon", startDate = "2027-04-01", endDate = "2027-04-08", phase = Phase.PLANNING, prep = PrepSummary(3, 9, 33))
    private val rome = Trip(2, "Rome", "Rome", phase = Phase.DREAMING)

    @Test
    fun `empty state offers planning and dreaming`() {
        show(TripsUiState(loading = false))
        compose.onNodeWithText("No trips yet. Plan one or save an idea for later.").assertIsDisplayed()
        compose.onNodeWithText("Plan a trip").performClick()
        compose.onNodeWithText("Save an idea").performClick()
        assertEquals(listOf(TripsEvent.PlanTrip, TripsEvent.OpenCreate), events)
    }

    @Test
    fun `pinned card, phase text and offline notice are shown`() {
        show(TripsUiState(loading = false, pinned = lisbon, groups = groupByPhase(listOf(lisbon, rome), lisbon.id), offline = true), dark = true)
        compose.onNodeWithText("Next trip").assertIsDisplayed()
        compose.onNodeWithText("33 % planned").assertIsDisplayed()
        compose.onNodeWithText("You are offline. Showing your saved trips.").assertIsDisplayed()
    }

    @Test
    fun `tapping a trip opens it and its phase is written out`() {
        show(TripsUiState(loading = false, groups = groupByPhase(listOf(rome), null)))
        compose.onAllNodesWithText("Dreaming").assertCountEquals(2)
        compose.onNodeWithText("No dates yet").assertIsDisplayed()
        compose.onNodeWithText("Rome").performClick()
        assertEquals(listOf<TripsEvent>(TripsEvent.OpenTrip(2)), events)
    }

    @Test
    fun `error shows the backend message with retry`() {
        show(TripsUiState(loading = false, error = HubError("Slow down, try later")))
        compose.onNodeWithText("Slow down, try later").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf<TripsEvent>(TripsEvent.Refresh), events)
    }

    @Test
    fun `sheet shows a title error`() {
        show(TripsUiState(loading = false, sheet = NewTripSheet(error = NewTripError.Title)))
        compose.onNodeWithText("Please give your trip a title.").assertIsDisplayed()
    }
}
