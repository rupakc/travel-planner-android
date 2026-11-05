package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.R as AppR
import io.github.rupakc.travelplanner.feature.chat.R as ChatR
import io.github.rupakc.travelplanner.feature.home.R as HomeR
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Flow 9: global search finds a saved trip (synced from the server) and a chat thread (kept on the device). */
@E2eTest
@RunWith(AndroidJUnit4::class)
class GlobalSearchE2eTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val api = BackendApi()
    private lateinit var robot: AppRobot
    private val tripName = "E2E Global Search Lisbon " + System.currentTimeMillis() % 100_000

    @Before fun setUp() {
        val token = api.adminToken()
        api.resetPreferences(token)
        api.createPlan(token, tripName, api.lisbonSearchData())
        robot = AppRobot(rule)
    }

    @After fun cleanUp() {
        val token = api.adminToken()
        api.plans(token).map { it.jsonObject }
            .filter { it["name"]?.jsonPrimitive?.content == tripName }
            .forEach { api.deletePlan(token, it.getValue("id").jsonPrimitive.content) }
    }

    @Test fun findsSavedTripAndChatThread() {
        robot.signInToHome()
        robot.openTab(AppR.string.nav_plans)
        robot.waitForText(tripName, timeoutMs = AppRobot.LONG_TIMEOUT)

        robot.openTab(AppR.string.nav_chat)
        robot.waitForText(robot.str(ChatR.string.chat_title))
        robot.chatSend("Which currency do they use in Portugal?")

        robot.tapDescription(robot.str(AppR.string.nav_global_search))
        // Regression: the search field drew under the status bar (no window insets on this full-screen route).
        robot.assertBelowStatusBar(robot.hasDescription(robot.str(HomeR.string.home_search_back)))
        robot.typeInFirstField("Global Search Lisbon")
        // Results are debounced; the trips group lists the saved trip.
        robot.waitForText(tripName)
        Screenshots.capture("global-search")

        robot.typeInFirstField("currency")
        robot.waitForText("Which currency do they use in Portugal", substring = true)
    }
}
