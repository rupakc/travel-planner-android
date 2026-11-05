package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.R as AppR
import io.github.rupakc.travelplanner.feature.results.R as ResultsR
import io.github.rupakc.travelplanner.feature.settings.R as SettingsR
import org.hamcrest.Description
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flow 12: Accessibility Test Framework checks (touch targets, labels, contrast, …) on Home, Search, Results and
 * Settings. ATF errors fail the test; warnings are ignored. Once enabled, every robot action also runs the checks.
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
        // Known issue (see the E2E report): ATF measures the *middle* top-bar action 48 x 32 dp on every top-level
        // screen, whichever action sits there (checked by reordering them); Compose's own touch bounds are 48 x 48 dp.
        // Only that touch-target result inside the top app bar is suppressed; everything else fails the test.
        val topBarBottom = rule.onAllNodes(robot.hasDescription(robot.str(AppR.string.nav_settings))).fetchSemanticsNodes().first().touchBoundsInRoot.bottom
        rule.enableAccessibilityChecks(
            AccessibilityValidator().setSuppressingResultMatcher(
                allOf(
                    AccessibilityCheckResultUtils.matchesCheck(TouchTargetSizeCheck::class.java),
                    object : TypeSafeMatcher<AccessibilityViewCheckResult>() {
                        override fun matchesSafely(item: AccessibilityViewCheckResult): Boolean =
                            (item.element?.boundsInScreen?.bottom ?: Int.MAX_VALUE) <= topBarBottom

                        override fun describeTo(description: Description) {
                            description.appendText("inside the top app bar")
                        }
                    },
                ),
            ),
        )
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
