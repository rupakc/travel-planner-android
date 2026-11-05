package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
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
