package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.core.ui.R as UiR
import io.github.rupakc.travelplanner.feature.results.R as ResultsR
import io.github.rupakc.travelplanner.feature.search.R as SearchR
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flow 11 (in-process part): rotation keeps Search and Results state. Process death is checked from the host
 * (`adb shell am kill` after backgrounding), because killing the app also kills this instrumentation; see
 * `tools/e2e/process_death.sh`.
 */
@E2eTest
@RunWith(AndroidJUnit4::class)
class StateRestorationE2eTest {
    @get:Rule(order = 0)
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val failureShot = Screenshots.onFailure()
    private lateinit var robot: AppRobot

    @After fun portrait() {
        if (::robot.isInitialized) robot.device.setOrientationNatural()
    }

    @Test fun rotation_keepsResults() {
        BackendApi().resetPreferences(BackendApi().adminToken())
        robot = AppRobot(rule)
        robot.signInToHome()
        robot.searchTrip(AppRobot.Trip())
        val flows = PlanFlows(robot)
        robot.waitForText(robot.str(ResultsR.string.results_ease_title), timeoutMs = PlanFlows.STREAM_TIMEOUT)
        flows.awaitTerminal(UiR.string.section_hotels)

        robot.device.setOrientationLeft()
        rule.waitForIdle()
        // Landscape on this phone is an expanded width: Results switches to list-detail. The same results are
        // shown (no new stream): the Travel Ease banner and the Hotels entry are there straight away.
        robot.waitFor(hasTestTag("results_list_pane"))
        assertTrue(robot.scrollToFind(hasText(robot.str(UiR.string.section_hotels))))
        Screenshots.capture("results-landscape")
        robot.device.setOrientationNatural()
        rule.waitForIdle()
        flows.awaitTerminal(UiR.string.section_hotels, timeoutMs = AppRobot.DEFAULT_TIMEOUT)
    }

    /**
     * First half of the process-death check driven by `tools/e2e/process_death.sh`: fills the Search form, then
     * leaves the app in the background. The script kills the process and relaunches it from the launcher.
     */
    @Test fun processDeath_prepareSearchForm() {
        BackendApi().resetPreferences(BackendApi().adminToken())
        robot = AppRobot(rule)
        robot.signInToHome()
        robot.openTab(io.github.rupakc.travelplanner.R.string.nav_search)
        robot.waitFor(hasTestTag("search_submit"))
        robot.pickSuggestion(robot.str(SearchR.string.search_from), "New York", "JFK")
        robot.pickSuggestion(robot.str(SearchR.string.search_to), "Lisbon", "LIS")
        robot.device.pressHome()
    }

    /** Same, on Results after a search has finished streaming its first sections. */
    @Test fun processDeath_prepareResults() {
        BackendApi().resetPreferences(BackendApi().adminToken())
        robot = AppRobot(rule)
        robot.signInToHome()
        robot.searchTrip(AppRobot.Trip())
        robot.waitForText(robot.str(ResultsR.string.results_ease_title), timeoutMs = PlanFlows.STREAM_TIMEOUT)
        PlanFlows(robot).awaitTerminal(UiR.string.section_hotels)
        robot.device.pressHome()
    }

    @Test fun rotation_keepsSearchForm() {
        BackendApi().resetPreferences(BackendApi().adminToken())
        robot = AppRobot(rule)
        robot.signInToHome()
        robot.openTab(io.github.rupakc.travelplanner.R.string.nav_search)
        robot.waitFor(hasTestTag("search_submit"))
        robot.pickSuggestion(robot.str(SearchR.string.search_from), "New York", "JFK")
        robot.pickSuggestion(robot.str(SearchR.string.search_to), "Lisbon", "LIS")
        robot.type(robot.str(SearchR.string.search_nationality), "American")
        val origin = hasText("JFK —", substring = true)
        val destination = hasText("LIS —", substring = true)
        assertTrue(robot.exists(origin) && robot.exists(destination))

        robot.device.setOrientationLeft()
        rule.waitForIdle()
        // Landscape: the form is a lazy list, so the fields below the fold are found by scrolling.
        robot.waitForText(robot.str(SearchR.string.search_mode_known))
        assertTrue("origin kept after rotation", robot.scrollToFind(origin))
        assertTrue("destination kept after rotation", robot.scrollToFind(destination))
        assertTrue("nationality kept after rotation", robot.scrollToFind(hasText("American", substring = true)))

        robot.device.setOrientationNatural()
        rule.waitForIdle()
        assertTrue(robot.scrollToFind(origin))
    }
}
