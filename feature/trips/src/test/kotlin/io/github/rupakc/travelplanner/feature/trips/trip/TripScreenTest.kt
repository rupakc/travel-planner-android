package io.github.rupakc.travelplanner.feature.trips.trip

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.journey.PlanCompleteness
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
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
@Config(application = Application::class, qualifiers = "w411dp-h2000dp")
class TripScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<TripEvent>()

    private fun show(state: TripUiState, content: Map<TripTab, TripTabContent> = emptyMap()) =
        compose.setContent {
            TravelTheme(ThemeMode.LIGHT) { TripScreen(state, { events += it }, content, { Text("prep tab") }) }
        }

    private val planning =
        Trip(
            id = 12,
            title = "Lisbon in spring",
            destination = "Lisbon",
            destinations = listOf("Lisbon", "Porto"),
            startDate = "2027-04-01",
            endDate = "2027-04-08",
            phase = Phase.PLANNING,
            daysToStart = 140,
            prep = PrepSummary(3, 9, 33),
        )

    private fun state(trip: Trip = planning, plan: Boolean = true) =
        TripUiState(
            loading = false,
            trip = trip,
            plan = if (plan) Plan(id = 12, name = trip.title) else null,
            planStatus = if (plan) PlanStatus.Ready else PlanStatus.Loading,
            completeness = PlanCompleteness.of(PlanSelections(), null),
        )

    @Test
    fun `overview shows destinations, countdown, prep ring, the meter and open planner`() {
        show(state())
        compose.onNodeWithText("Lisbon in spring").assertIsDisplayed()
        compose.onNodeWithText("Lisbon · Porto").assertIsDisplayed()
        compose.onNodeWithText("in 140 days").assertIsDisplayed()
        compose.onNodeWithText("3 of 9 done").assertIsDisplayed()
        compose.onNodeWithText("Plan completeness").assertIsDisplayed()
        compose.onNodeWithText("Open in planner").performClick()
        assertEquals(TripEvent.OpenPlanner, events.last())
    }

    @Test
    fun `tabs follow the phase and only built-in or plugged tabs exist`() {
        show(state(planning.copy(phase = Phase.ON_THE_ROAD, dayIndex = 2, startDate = "2027-03-31")), mapOf(TripTab.Today to { _, _ -> Text("today tab") }))
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("today tab").assertIsDisplayed()
        compose.onNodeWithText("Day 2 of 9").assertIsDisplayed()
        compose.onNodeWithText("Overview").performClick()
        assertEquals(TripEvent.SelectTab(TripTab.Overview), events.last())
    }

    @Test(timeout = 60_000)
    fun `requesting prep on the road appends a sixth tab without crashing`() {
        val road = state(planning.copy(phase = Phase.ON_THE_ROAD, dayIndex = 2, startDate = "2027-03-31"))
        val body: TripTabContent = { _, _ -> Text("tab body") }
        val content = TripTab.entries.filter { it != TripTab.Prep && it != TripTab.Recap }.associateWith { body }
        compose.setContent {
            var s by remember { mutableStateOf(road) }
            TravelTheme(ThemeMode.LIGHT) {
                TripScreen(s, { e -> if (e is TripEvent.SelectTab) s = s.copy(requestedTab = e.tab) }, content, { Text("prep tab") })
            }
        }
        compose.onNodeWithText("Day 2 of 9").assertIsDisplayed()
        compose.onNodeWithText("Overview").performClick()
        compose.onNodeWithText("Before you go").performClick()
        compose.onNodeWithText("prep tab").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun `the day line is hidden under a memories override`() {
        show(state(planning.copy(phase = Phase.MEMORIES, dayIndex = 3, startDate = "2027-03-31")))
        compose.onNodeWithText("Day 3 of 9").assertDoesNotExist()
    }

    @Test
    fun `phase menu overrides and clears`() {
        show(state())
        compose.onNodeWithContentDescription("Trip stage: Planning. Change it").performClick()
        compose.onNodeWithText("Ready").performClick()
        assertEquals(TripEvent.SetPhase(Phase.READY), events.last())
        compose.onNodeWithContentDescription("Trip stage: Planning. Change it").performClick()
        compose.onNodeWithText("Follow my dates").performClick()
        assertEquals(TripEvent.SetPhase(null), events.last())
    }

    @Test
    fun `a dreaming trip without a search offers plan it`() {
        show(state(Trip(id = 1, title = "Rome", destination = "Rome", phase = Phase.DREAMING)))
        compose.onNodeWithText("Find flights, stays and things to do in Rome.").assertIsDisplayed()
        compose.onNodeWithText("Plan it").performClick()
        assertEquals(TripEvent.PlanIt, events.last())
    }

    @Test
    fun `delete is confirmed with the trip title`() {
        show(state().copy(dialog = TripDialog.ConfirmDelete))
        compose.onNodeWithText("Delete “Lisbon in spring”? Its plan, expenses and journal are deleted too.").assertIsDisplayed()
        compose.onNodeWithText("Delete trip").performClick()
        assertEquals(TripEvent.ConfirmDelete, events.last())
    }

    @Test
    fun `load error offers retry with the backend text`() {
        show(TripUiState(loading = false, error = TripError("Trip not found")))
        compose.onNodeWithText("Trip not found").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(TripEvent.Refresh, events.last())
    }

    @Test
    fun `a failed plan load can be retried`() {
        show(state(plan = false).copy(planStatus = PlanStatus.Failed))
        compose.onNodeWithText("Couldn't load the plan right now.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(TripEvent.RetryPlan, events.last())
    }

    @Test
    fun `a tab can switch to a sibling tab through the provided selector`() {
        val onRoad = planning.copy(phase = Phase.ON_THE_ROAD, dayIndex = 2)
        val today: TripTabContent = { _, _ ->
            val select = LocalTripTabSelector.current
            androidx.compose.material3.Button(onClick = { select?.invoke(TripTab.Money) }) { Text("quick add") }
        }
        show(state(onRoad), mapOf(TripTab.Today to today, TripTab.Money to { _, _ -> Text("money tab") }))
        compose.onNodeWithText("quick add").performClick()
        assertEquals(TripEvent.SelectTab(TripTab.Money), events.last())
    }
}
