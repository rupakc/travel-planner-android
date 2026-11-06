package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.feature.home.R as HomeR
import io.github.rupakc.travelplanner.feature.onboarding.R as OnboardingR
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Flow 1: login -> onboarding (all six questions) -> Home; the quiz answers reach the server's Preferences. */
@E2eTest
@RunWith(AndroidJUnit4::class)
class OnboardingE2eTest {
    @get:Rule(order = 0)
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val failureShot = Screenshots.onFailure()
    private val api = BackendApi()
    private lateinit var robot: AppRobot

    @Before fun setUp() {
        api.resetPreferences(api.adminToken())
        robot = AppRobot(rule)
    }

    @Test fun loginOnboardingHome_updatesServerPreferences() {
        robot.signIn()
        // Admin has no forced password change: the gate goes straight to onboarding.
        robot.waitForText(robot.str(OnboardingR.string.onboarding_title, robot.brand))
        robot.completeOnboarding(
            listOf(
                listOf(OnboardingR.string.onboarding_opt_companions_solo),
                listOf(OnboardingR.string.onboarding_opt_budget_luxury),
                listOf(OnboardingR.string.onboarding_opt_interests_food, OnboardingR.string.onboarding_opt_interests_art_museums),
                listOf(OnboardingR.string.onboarding_opt_pace_packed),
                listOf(OnboardingR.string.onboarding_opt_planning_loose),
                listOf(OnboardingR.string.onboarding_opt_needs_dietary),
            ),
        )
        // Result names a traveller type.
        robot.waitForText(robot.str(OnboardingR.string.onboarding_result_heading))
        Screenshots.capture("onboarding-result")
        robot.finishOnboarding(allowNotifications = true)

        // Home: the type chip names a traveller type (not "Choose your traveller type") and the tools grid has tiles.
        val unset = robot.str(HomeR.string.home_type_unset)
        robot.waitFor(robot.hasDescription(robot.str(HomeR.string.home_type_a11y, "").substringBefore(":"), substring = true))
        assertFalse(robot.exists(robot.hasDescription(robot.str(HomeR.string.home_type_a11y, unset))))
        assertTrue(robot.scrollToFind(hasText(robot.str(HomeR.string.home_tools_heading))))
        assertFalse(robot.exists(hasText(robot.str(HomeR.string.home_tools_empty_title))))
        Screenshots.capture("home")

        val prefs = api.preferences(api.adminToken())
        assertEquals("high", prefs.getValue("budget_category").jsonPrimitive.content)
        val interests = prefs.getValue("interests").jsonArray.map { it.jsonPrimitive.content }
        assertTrue("interests=$interests", "food" in interests)
    }
}
