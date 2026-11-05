package io.github.rupakc.travelplanner.feature.plan

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.feature.plan.detail.DetailConfirm
import io.github.rupakc.travelplanner.feature.plan.detail.PlanDetailContent
import io.github.rupakc.travelplanner.feature.plan.detail.PlanDetailEvent
import io.github.rupakc.travelplanner.feature.plan.detail.PlanDetailScreen
import io.github.rupakc.travelplanner.feature.plan.detail.PlanDetailUiState
import io.github.rupakc.travelplanner.feature.plan.list.PlansEvent
import io.github.rupakc.travelplanner.feature.plan.list.PlansScreen
import io.github.rupakc.travelplanner.feature.plan.list.PlansUiState
import io.github.rupakc.travelplanner.feature.plan.list.PlansViewModel
import io.github.rupakc.travelplanner.feature.plan.model.SelectionGroups
import io.github.rupakc.travelplanner.feature.plan.model.TripSummary
import io.github.rupakc.travelplanner.feature.plan.model.plan
import io.github.rupakc.travelplanner.feature.plan.myplan.MyPlanContent
import io.github.rupakc.travelplanner.feature.plan.myplan.MyPlanEvent
import io.github.rupakc.travelplanner.feature.plan.myplan.MyPlanScreen
import io.github.rupakc.travelplanner.feature.plan.myplan.MyPlanUiState
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PlanScreensTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 5)

    @Test
    fun `empty My trips shows the web copy and a plan-a-trip action`() {
        var planTrip = false
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) { PlansScreen(PlansUiState(isLoading = false), {}, {}, { planTrip = true }) }
        }
        compose.onNodeWithText("No saved plans yet").assertIsDisplayed()
        compose.onNodeWithText("Plan a trip").performClick()
        assertTrue(planTrip)
    }

    @Test
    fun `typing in search filters the list through the real ViewModel`() {
        val repo =
            FakePlansRepository(
                listOf(
                    plan(1, "Spring in Japan", "Tokyo", "2027-04-01", "2027-04-08"),
                    plan(2, "Andalucía", "Málaga, Spain", "2026-12-01", "2026-12-05"),
                ),
            )
        val vm =
            PlansViewModel(androidx.lifecycle.SavedStateHandle(), repo, FixedClock(), TestAppDispatchers(Dispatchers.Main), CoroutineScope(SupervisorJob()))
        compose.setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            TravelTheme(themeMode = ThemeMode.DARK) { PlansScreen(state, vm::onEvent, {}, {}) }
        }
        compose.onNodeWithText("Spring in Japan").assertIsDisplayed()
        compose.onNode(hasText("Search trips by name or destination") and androidx.compose.ui.test.hasSetTextAction()).performTextInput("malaga")
        compose.waitForIdle()
        compose.onNodeWithText("Andalucía").assertIsDisplayed()
        compose.onNodeWithText("Spring in Japan").assertDoesNotExist()
        compose.onNodeWithText("Showing 1 trip").assertExists()
    }

    @Test
    fun `trip rows expose delete as an accessibility action`() {
        val events = mutableListOf<PlansEvent>()
        val trip = TripSummary.from(plan(5, "Lisbon", "Lisbon", "2026-12-01", "2026-12-03"), today)
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                PlansScreen(PlansUiState(isLoading = false, trips = listOf(trip), totalCount = 1), { events += it }, {}, {})
            }
        }
        val node = compose.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNode()
        compose.runOnUiThread { node.config[SemanticsActions.CustomActions].first { it.label == "Delete" }.action() }
        assertEquals(PlansEvent.Delete(5), events.single())
    }

    private fun detailState(confirm: DetailConfirm? = null): PlanDetailUiState {
        val p =
            plan(
                7,
                "Japan",
                "Tokyo",
                "2027-04-01",
                "2027-04-04",
                shareToken = "tok",
                selections = PlanSelections(activities = listOf(Activity(name = "Sushi", priceUsd = 80.0))),
            )
        val summary = TripSummary.from(p, today)
        return PlanDetailUiState(
            isLoading = false,
            content = PlanDetailContent(summary, SelectionGroups.of(p.selections), emptyList(), emptyList(), null, false),
            confirm = confirm,
        )
    }

    @Test
    fun `stop sharing asks for confirmation`() {
        val events = mutableListOf<PlanDetailEvent>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) { PlanDetailScreen(detailState(), { events += it }, {}, onBack = {}) }
        }
        compose.onNodeWithText("Shared: anyone with the link can view this trip.").assertExists()
        compose.onNodeWithText("Stop sharing").performClick()
        assertEquals(PlanDetailEvent.RevokeShare, events.last())
    }

    @Test
    fun `revoke confirmation dialog confirms and cancels`() {
        val events = mutableListOf<PlanDetailEvent>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.DARK) {
                PlanDetailScreen(detailState(DetailConfirm.REVOKE_SHARE), { events += it }, {}, onBack = {})
            }
        }
        compose.onNodeWithText("Stop sharing this trip?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(PlanDetailEvent.DismissConfirm, events.last())
    }

    @Test
    fun `detail lists selections with headings and opens the overflow`() {
        val events = mutableListOf<PlanDetailEvent>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) { PlanDetailScreen(detailState(), { events += it }, {}, onBack = {}) }
        }
        compose.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToNode(hasText("Sushi"))
        compose.onNodeWithText("Activities (1)").assertExists()
        compose.onNodeWithText("Sushi").assertExists()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Delete trip").performClick()
        assertEquals(PlanDetailEvent.Delete, events.last())
    }

    @Test
    fun `My Plan remove buttons are labelled and empty copy matches the web`() {
        val events = mutableListOf<MyPlanEvent>()
        val selections = PlanSelections(activities = listOf(Activity(name = "Sushi", priceUsd = 80.0)))
        val content =
            MyPlanContent(null, "My Trip Plan", true, "Tokyo", null, SelectionGroups.of(selections), emptyList(), emptyList(), 0.0, null, false, 1)
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) { MyPlanScreen(MyPlanUiState(content = content), { events += it }, {}, {}, {}) }
        }
        compose.onNodeWithContentDescription("Remove Sushi").performClick()
        assertTrue(events.last() is MyPlanEvent.Remove)
        compose.onNodeWithText("Save Plan").performClick()
        assertEquals(MyPlanEvent.Save, events.last())
    }

    @Test
    fun `empty My Plan shows the web empty copy`() {
        val content = MyPlanContent(null, "My Trip Plan", false, null, null, emptyList(), emptyList(), emptyList(), 0.0, null, false, 0)
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.DARK) { MyPlanScreen(MyPlanUiState(content = content), {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Select flights, hotels & activities to build your plan").assertIsDisplayed()
    }

    @Test
    fun `offline detail shows the cached-copy banner and disables sharing`() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                PlanDetailScreen(detailState().copy(staleError = io.github.rupakc.travelplanner.core.common.AppError.Network()), {}, {}, onBack = {})
            }
        }
        compose.onNodeWithText("You're offline. Showing the copy saved on this phone; sharing and deleting are off until you reconnect.").assertExists()
        compose.onNodeWithText("Share link").assertIsNotEnabled()
        compose.onNodeWithText("Stop sharing").assertIsNotEnabled()
        compose.onNodeWithText("Open in results").assertIsEnabled()
    }
}
