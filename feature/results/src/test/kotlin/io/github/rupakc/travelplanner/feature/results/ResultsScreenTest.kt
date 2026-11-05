package io.github.rupakc.travelplanner.feature.results

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeFareCalendarRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeFareWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeHealthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePriceWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.feature.results.navigation.ResultsRoute
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Optional
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Compose tests of the results screen on Robolectric, driven by the real ViewModel over fakes and the recorded stream. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class ResultsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val request =
        TravelSearchRequest(
            origin = "NYC",
            destination = "Tokyo",
            departureDate = "2026-12-01",
            returnDate = "2026-12-08",
            nationality = "American",
            budgetUsd = 6000.0,
        )
    private val search = FakeSearchRepository()
    private val plan = FakeCurrentPlanStore()
    private lateinit var vm: ResultsViewModel

    @Before
    fun setUp() {
        val clock =
            object : AppClock {
                override fun now(): Instant = LocalDate.parse("2026-11-05").atStartOfDay().toInstant(ZoneOffset.UTC)

                override fun zone(): ZoneId = ZoneOffset.UTC
            }
        vm =
            ResultsViewModel(
                search,
                plan,
                FakeHealthRepository(Health(status = "ok")),
                FakeFareCalendarRepository(),
                FakeFareWatchesRepository(),
                FakePriceWatchesRepository(),
                FakeProfileRepository(),
                Optional.empty(),
                clock,
                TestAppDispatchers(Dispatchers.Unconfined),
                androidx.lifecycle.SavedStateHandle(),
            )
        search.start(request)
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                ResultsRoute(onBack = {}, onOpenMyPlan = {}, onOpenAlerts = {}, viewModel = vm)
            }
        }
        compose.waitForIdle()
    }

    private fun stateDescription(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun scrollTo(key: Any) = compose.onNodeWithTag("results_list").performScrollToKey(key)

    @Test
    fun `section header exposes expanded state and collapses on tap`() {
        scrollTo("FLIGHTS")
        compose.onNode(stateDescription("Collapse Flights")).performClick()
        compose.waitForIdle()
        compose.onNode(stateDescription("Expand Flights")).assertExists()
    }

    @Test
    fun `add to plan and remove again`() {
        scrollTo("HOTELS")
        compose.onNode(hasContentDescription("Add Park Hyatt Tokyo to My Plan")).performClick()
        compose.waitForIdle()
        assertEquals(1, plan.draft.value.selections.count)
        compose.onNode(hasContentDescription("Remove Park Hyatt Tokyo from My Plan")).performClick()
        compose.waitForIdle()
        assertEquals(0, plan.draft.value.selections.count)
    }

    @Test
    fun `best value apply then undo`() {
        scrollTo("best_value")
        compose.onNodeWithText("Add all to My Plan").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertTrue(plan.draft.value.selections.count > 0)
        compose.onAllNodesWithText("Added to My Plan").onFirst().assertIsNotEnabled()
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, plan.draft.value.selections.count)
    }

    @Test
    fun `flight filter sheet applies the chosen filters`() {
        search.flightsResult = AppResult.Success(FlightsData())
        scrollTo("FLIGHTS")
        compose.onAllNodesWithText("Filters").onFirst().performClick()
        compose.onNodeWithText("Flight Filters").assertExists()
        compose.onNodeWithText("Non-stop").performScrollTo().performClick()
        compose.onNodeWithText("Apply Filters (1)").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, vm.state.value.flightFilters?.maxStops)
        scrollTo("FLIGHTS")
        compose.onNode(hasText("1 filter on")).assertExists()
    }

    @Test
    fun `budget planner show only what fits is a switch`() {
        scrollTo("budget")
        val toggle =
            compose.onNode(
                hasText("Show only what fits").and(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)),
                useUnmergedTree = false,
            )
        toggle.performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        toggle.performClick()
        compose.waitForIdle()
        assertTrue(vm.state.value.budget!!.showOnlyFits)
        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }
}
