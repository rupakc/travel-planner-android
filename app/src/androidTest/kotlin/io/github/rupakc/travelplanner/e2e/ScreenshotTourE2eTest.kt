package io.github.rupakc.travelplanner.e2e

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.R as AppR
import io.github.rupakc.travelplanner.core.ui.R as UiR
import io.github.rupakc.travelplanner.feature.chat.R as ChatR
import io.github.rupakc.travelplanner.feature.nearby.R as NearbyR
import io.github.rupakc.travelplanner.feature.onboarding.R as OnboardingR
import io.github.rupakc.travelplanner.feature.plan.R as PlanR
import io.github.rupakc.travelplanner.feature.results.R as ResultsR
import io.github.rupakc.travelplanner.feature.search.R as SearchR
import io.github.rupakc.travelplanner.feature.settings.R as SettingsR
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * README screenshots (light, then dark) of the main screens, as the second account (no e-mail on screen).
 * Run with `-Pe2eScreenshots=true`; a tablet set comes from the same test after `adb shell wm size 1600x2560` /
 * `wm density 320` with `-Pandroid.testInstrumentationRunnerArguments.e2eShotPrefix=tablet-`.
 */
@E2eTest
@RunWith(AndroidJUnit4::class)
class ScreenshotTourE2eTest {
    @get:Rule(order = 0)
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val failureShot = Screenshots.onFailure()
    private val api = BackendApi()
    private lateinit var robot: AppRobot
    private val tripName = "Lisbon long weekend"

    @Before fun setUp() {
        api.ensureSecondUser()
        val user = api.login(E2eConfig.SECOND_USER, E2eConfig.SECOND_PASSWORD)
        api.resetPreferences(user)
        if (api.plans(user).none { it.jsonObject["name"]?.jsonPrimitive?.content == tripName }) {
            // Copy a saved itinerary plan of the admin (from flows 4/5) so Trip detail has something to show.
            val admin = api.adminToken()
            val source = PlanFlows.existingItineraryPlanFor(api, admin, PlanFlows.PLAN_PREFIX)
            api.createPlan(
                user,
                tripName,
                source?.get("search_data") as? JsonObject ?: api.lisbonSearchData(),
                source?.get("selections") as? JsonObject ?: JsonObject(emptyMap()),
            )
        }
        shell("appops set com.android.shell android:mock_location allow")
        shell("cmd location providers add-test-provider gps")
        shell("cmd location providers set-test-provider-enabled gps true")
        shell("cmd location providers set-test-provider-location gps --location 38.7223,-9.1393")
        robot = AppRobot(rule)
    }

    @After fun tearDown() {
        shell("cmd location providers remove-test-provider gps")
    }

    private fun shell(command: String) {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(SETTLE_MS)
        Screenshots.capture(name)
    }

    @Test fun tour() {
        robot.signIn(E2eConfig.SECOND_USER, E2eConfig.SECOND_PASSWORD)
        robot.waitForText(robot.str(OnboardingR.string.onboarding_title, robot.brand))
        shot("onboarding")
        robot.completeOnboarding(
            listOf(
                listOf(OnboardingR.string.onboarding_opt_companions_partner),
                listOf(OnboardingR.string.onboarding_opt_budget_comfort),
                listOf(OnboardingR.string.onboarding_opt_interests_food, OnboardingR.string.onboarding_opt_interests_history_culture),
                listOf(OnboardingR.string.onboarding_opt_pace_balanced),
                emptyList(),
                emptyList(),
            ),
        )
        shot("onboarding-result")
        robot.finishOnboarding()
        shot("home")

        robot.openTab(AppR.string.nav_search)
        robot.waitForText(robot.str(SearchR.string.search_mode_known))
        shot("search")

        robot.searchTrip(AppRobot.Trip())
        robot.waitForText(robot.str(ResultsR.string.results_ease_title), timeoutMs = PlanFlows.STREAM_TIMEOUT)
        PlanFlows(robot, api).awaitTerminal(UiR.string.section_hotels)
        PlanFlows(robot, api).scrollToSection("HOTELS")
        shot("results")
        robot.tap(ResultsR.string.results_open_my_plan)
        robot.waitForText(robot.str(PlanR.string.plan_save))
        shot("my-plan")
        robot.back()
        robot.back()

        robot.openTab(AppR.string.nav_plans)
        robot.tap(tripName)
        robot.waitForText(robot.prefixOf(PlanR.string.plan_full_itinerary), substring = true)
        shot("trip-detail")
        robot.back()

        robot.openTab(AppR.string.nav_chat)
        robot.waitForText(robot.str(ChatR.string.chat_title, robot.brand))
        robot.chatSend("What should I eat in Lisbon? Three dishes, one line each.")
        shot("chat")

        robot.openTab(AppR.string.nav_alerts)
        shot("alerts")

        robot.openTab(AppR.string.nav_home)
        robot.tap(robot.str(UiR.string.feature_near_me_title))
        robot.tap(NearbyR.string.nearby_allow)
        robot.allowSystemPermission()
        robot.waitForText(robot.str(NearbyR.string.nearby_distance, "").trim().ifBlank { "km" }, substring = true, timeoutMs = PlanFlows.STREAM_TIMEOUT)
        shot("near-me")
        robot.back()

        robot.openSettings()
        shot("settings")

        // Dark theme: the same screens again.
        robot.tap(SettingsR.string.settings_appearance)
        robot.tap(SettingsR.string.settings_theme_dark)
        robot.back()
        shot("settings-dark")
        robot.back()
        robot.openTab(AppR.string.nav_home)
        shot("home-dark")
        robot.openTab(AppR.string.nav_search)
        shot("search-dark")
        robot.tap(SearchR.string.search_recent_again)
        robot.waitForText(robot.str(ResultsR.string.results_ease_title), timeoutMs = PlanFlows.STREAM_TIMEOUT)
        shot("results-dark")
        robot.back()
        robot.openTab(AppR.string.nav_chat)
        shot("chat-dark")
        robot.openTab(AppR.string.nav_plans)
        robot.tap(tripName)
        robot.waitForText(robot.prefixOf(PlanR.string.plan_full_itinerary), substring = true)
        shot("trip-detail-dark")
        robot.back()
        robot.openTab(AppR.string.nav_alerts)
        shot("alerts-dark")
    }

    private companion object {
        const val SETTLE_MS = 1_200L
    }
}
