package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.R as AppR
import io.github.rupakc.travelplanner.feature.results.R as ResultsR
import io.github.rupakc.travelplanner.feature.settings.R as SettingsR
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flow 12: Accessibility Test Framework checks (touch targets, labels, contrast, …) on Home, Search, Results and
 * Settings. ATF errors fail the test (nothing is suppressed); warnings are ignored. Once enabled, every robot action
 * also runs the checks.
 */
@E2eTest
@RunWith(AndroidJUnit4::class)
class AccessibilityE2eTest {
    @get:Rule(order = 0)
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val failureShot = Screenshots.onFailure()
    private lateinit var robot: AppRobot

    @Before fun setUp() {
        BackendApi().resetPreferences(BackendApi().adminToken())
        robot = AppRobot(rule)
    }

    private fun check() {
        rule.waitForIdle()
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test fun home_search_results_settings_haveNoAtfErrors() {
        robot.signInToHome()
        rule.enableAccessibilityChecks()
        check() // Home

        robot.openTab(AppR.string.nav_search)
        robot.waitFor(hasTestTag("search_submit"))
        check() // Search

        robot.searchTrip(AppRobot.Trip())
        robot.waitForText(robot.str(ResultsR.string.results_ease_title), timeoutMs = PlanFlows.STREAM_TIMEOUT)
        check() // Results

        robot.back()
        robot.openSettings()
        robot.waitForText(robot.str(SettingsR.string.settings_title))
        check() // Settings
    }
}
