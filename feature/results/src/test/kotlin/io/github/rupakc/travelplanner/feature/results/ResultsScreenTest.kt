package io.github.rupakc.travelplanner.feature.results

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeFareCalendarRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeFareWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeFeatureVisibility
import io.github.rupakc.travelplanner.core.testing.fake.FakeHealthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePriceWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchLauncher
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
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
                FakeSearchLauncher(plan, search),
                plan,
                FakeHealthRepository(Health(status = "ok")),
                FakeFareCalendarRepository(),
                FakeFareWatchesRepository(),
                FakePriceWatchesRepository(),
                FakeFeatureVisibility(),
                FakeAuthRepository(Session("t", TestUser)),
                clock,
                TestAppDispatchers(Dispatchers.Unconfined),
                androidx.lifecycle.SavedStateHandle(),
                io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup(search),
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

    private fun chip(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasContentDescription(title))

    private fun selected(value: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, value)

    /** Brings [title]'s chip into the jump row's viewport and returns it. */
    private fun jumpChip(title: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("results_jump_row").performScrollToNode(chip(title))
        return compose.onNode(chip(title))
    }

    private fun sectionHeading(title: String) = compose.onNode(hasText(title).and(isHeading()))

    private fun top(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().top

    @Test
    fun `section jump row stays pinned above the list while the sections scroll`() {
        val navTop = top("results_section_nav")
        val navBottom = compose.onNodeWithTag("results_section_nav").getBoundsInRoot().bottom
        assertTrue(top("results_list") >= navBottom - 1.dp)
        for (key in listOf("VISA", "PACKING_LIST", "summary")) {
            scrollTo(key)
            compose.onNodeWithTag("results_section_nav").assertIsDisplayed()
            assertEquals(navTop.value, top("results_section_nav").value, 1f)
        }
    }

    @Test
    fun `tapping a chip jumps to its section below the pinned row and selects the chip`() {
        jumpChip("Visa & Entry").assert(selected(false)).performClick()
        compose.waitForIdle()
        val navBottom = compose.onNodeWithTag("results_section_nav").getBoundsInRoot().bottom
        sectionHeading("Visa & Entry").assertIsDisplayed()
        assertTrue(sectionHeading("Visa & Entry").getBoundsInRoot().top >= navBottom - 1.dp)
        compose.onNode(chip("Visa & Entry")).assert(selected(true))
        jumpChip("Flights").assert(selected(false)).performClick()
        compose.waitForIdle()
        sectionHeading("Flights").assertIsDisplayed()
        compose.onNode(chip("Flights")).assert(selected(true))
        jumpChip("Visa & Entry").assert(selected(false))
    }

    @Test
    fun `the last section stays selected after a jump until the user scrolls`() {
        jumpChip("Packing List").performClick()
        compose.waitForIdle()
        sectionHeading("Packing List").assertIsDisplayed()
        compose.onNode(chip("Packing List")).assert(selected(true))
        compose.onNodeWithTag("results_list").performTouchInput { swipeDown() }
        compose.waitForIdle()
        jumpChip("Packing List").assert(selected(false))
    }

    @Test
    fun `dragging during a jump animation hands the highlight back to the scroll spy`() {
        // Start deep in the list and jump to the last section; the drag pulls the list back up by about a screen,
        // so wherever the animation was cut, the list ends among the sections before Packing List.
        scrollTo("EMERGENCY_CARD")
        val target = jumpChip("Packing List")
        compose.mainClock.autoAdvance = false
        target.performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("results_list").performTouchInput {
            down(topCenter)
            repeat(DRAG_STEPS) { moveBy(Offset(0f, height / DRAG_STEPS.toFloat()), delayMillis = 0) }
            up()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.onAllNodes(tab.and(selected(true))).assertCountEquals(1)
        compose.onNode(tab.and(selected(true))).assert(hasContentDescription("Packing List").not())
    }

    @Test
    fun `scroll spy selects the chip of the section in view and scrolls it into the row`() {
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(selected(true))).assertCountEquals(0)
        scrollTo("HOTELS")
        compose.onNode(chip("Hotels")).assertIsDisplayed().assert(selected(true))
        jumpChip("Flights").assert(selected(false))
        scrollTo("EMERGENCY_CARD")
        // The selected chip is brought into the row's viewport without touching the row.
        compose.onNode(chip("Emergency Card")).assertIsDisplayed().assert(selected(true))
    }

    @Test
    fun `jump chips follow the in-results search filter`() {
        vm.onEvent(ResultsEvent.Query("Park Hyatt"))
        compose.waitForIdle()
        compose.onNode(chip("Visa & Entry")).assertDoesNotExist()
        jumpChip("Hotels").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Park Hyatt Tokyo").assertIsDisplayed()
        compose.onNode(chip("Hotels")).assert(selected(true))
    }

    @Test
    fun `jump chips are a group of tabs that say where they jump`() {
        compose.onNodeWithTag("results_jump_row").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        jumpChip("Hotels").assert(SemanticsMatcher("onClick label") { it.config[SemanticsActions.OnClick].label == "Jump to Hotels" })
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp")
    fun `expanded layout chips select the detail section`() {
        jumpChip("Visa & Entry").performClick()
        compose.waitForIdle()
        assertEquals(SectionId.VISA, vm.state.value.selectedSection)
        compose.onNode(chip("Visa & Entry")).assert(selected(true))
        compose.onNodeWithTag("results_detail_pane").assertIsDisplayed()
        sectionHeading("Visa & Entry").assertIsDisplayed()
    }

    private companion object {
        const val DRAG_STEPS = 12
    }
}
