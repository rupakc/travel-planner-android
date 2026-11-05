package io.github.rupakc.travelplanner.e2e

import androidx.annotation.StringRes
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.core.ui.R as UiR
import io.github.rupakc.travelplanner.feature.plan.R as PlanR
import io.github.rupakc.travelplanner.feature.results.R as ResultsR
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flow 3: Search (Lisbon, 3 nights, 2 adults) -> Results streams (Travel Ease + every section reaches a terminal
 * state) -> add a hotel and an activity -> My Plan -> Save -> the plan exists on the backend with the web's keys.
 */
@E2eTest
@RunWith(AndroidJUnit4::class)
class SearchResultsE2eTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val api = BackendApi()
    private lateinit var robot: AppRobot

    @Before fun setUp() {
        api.resetPreferences(api.adminToken())
        robot = AppRobot(rule)
    }

    /** Waits until [section]'s header shows the Ready status (errors also end in Ready, with their own copy). */
    private fun awaitTerminal(@StringRes section: Int) {
        val header = hasText(robot.str(section)) and hasContentDescription(robot.str(UiR.string.section_status_done))
        rule.waitUntil(STREAM_TIMEOUT) { robot.scrollListTo(LIST, header) && robot.existsMerged(header) }
    }

    /** Adds the first item listed under [section] and returns its name. */
    private fun addFirstUnder(@StringRes section: Int): String {
        val header = hasText(robot.str(section))
        robot.scrollListTo(LIST, header)
        val headerTop = rule.onAllNodes(header).onFirst().fetchSemanticsNode().boundsInWindow.bottom
        val (name, _) = robot.addButtons().filter { it.second > headerTop }.minBy { it.second }
        rule.onAllNodes(robot.hasDescription(robot.str(UiR.string.plan_add_a11y, name))).onFirst().performClick()
        robot.waitFor(robot.hasDescription(robot.str(UiR.string.plan_remove_a11y, name)))
        return name
    }

    @Test fun searchStreamsResults_addToPlan_save() {
        val token = api.adminToken()
        val plansBefore = api.plans(token).size
        robot.signInToHome()
        robot.searchTrip(AppRobot.Trip())

        robot.waitForText(robot.str(ResultsR.string.results_ease_title), timeoutMs = STREAM_TIMEOUT)
        Screenshots.capture("results")
        listOf(
            UiR.string.section_flights,
            UiR.string.section_hotels,
            UiR.string.section_activities,
            UiR.string.section_weather,
            UiR.string.section_visa,
        ).forEach(::awaitTerminal)

        val hotel = addFirstUnder(UiR.string.section_hotels)
        val activity = addFirstUnder(UiR.string.section_activities)

        robot.tap(ResultsR.string.results_open_my_plan)
        robot.waitForText(hotel, substring = true)
        Screenshots.capture("my-plan")
        robot.tap(PlanR.string.plan_save)
        robot.waitForText(robot.str(PlanR.string.plan_saved))

        val plans = api.plans(token)
        assertNotEquals(plansBefore, plans.size)
        val id = plans.map { it.jsonObject }.maxBy { it.getValue("id").jsonPrimitive.content.toLong() }.getValue("id").jsonPrimitive.content
        val saved = api.plan(token, id)
        val selections = saved.getValue("selections").jsonObject
        // The web's selection keys: a single hotel object and an activities list.
        assertTrue("hotel saved: $selections", selections["hotel"] != null && selections["hotel"] != JsonNull)
        assertTrue(selections.getValue("hotel").jsonObject["name"]?.jsonPrimitive?.content == hotel)
        val activities = selections.getValue("activities") as JsonArray
        assertTrue("activity saved", activities.any { it.jsonObject["name"]?.jsonPrimitive?.content == activity })
        assertTrue("search_data saved", saved.getValue("search_data").jsonObject["destination"] != null)
    }

    private companion object {
        const val LIST = "results_list"
        const val STREAM_TIMEOUT = 300_000L
    }
}
