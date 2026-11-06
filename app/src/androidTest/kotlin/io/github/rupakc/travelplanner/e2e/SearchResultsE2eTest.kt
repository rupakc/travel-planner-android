package io.github.rupakc.travelplanner.e2e

import androidx.annotation.StringRes
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.core.ui.R as UiR
import io.github.rupakc.travelplanner.feature.plan.R as PlanR
import io.github.rupakc.travelplanner.feature.results.R as ResultsR
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** Waits until [section]'s header shows the Ready status (errors also end in Ready, with their own copy). */
    private fun awaitTerminal(
        @StringRes section: Int,
    ) {
        val header = header(section) and hasContentDescription(robot.str(UiR.string.section_status_done))
        rule.waitUntil(STREAM_TIMEOUT) { robot.scrollListTo(LIST, header) && robot.existsMerged(header) }
    }

    /** The section card's header row (not the jump chip with the same title): it carries an expand/collapse state. */
    private fun header(
        @StringRes section: Int,
    ) =
        robot.str(section).let { title ->
            hasStateDescription(robot.str(UiR.string.section_collapse, title)) or hasStateDescription(robot.str(UiR.string.section_expand, title))
        }

    /** Adds the first item listed under [section] and returns its name. */
    private fun addFirstUnder(
        @StringRes section: Int,
        key: String,
    ): String {
        val header = header(section)
        // The section card is the lazy item keyed by its SectionId; scrolling to the key puts it at the top so the
        // rows after it (separate items) are composed.
        rule.onNode(androidx.compose.ui.test.hasTestTag(LIST)).performScrollToKey(key)
        rule.waitForIdle()
        val headerTop = rule.onAllNodes(header).onFirst().fetchSemanticsNode().boundsInWindow.bottom
        val (name, _) = robot.addButtons().filter { it.second > headerTop }.minBy { it.second }
        rule.onAllNodes(robot.hasDescription(robot.str(UiR.string.plan_add_a11y, name))).onFirst().performClick()
        robot.waitFor(robot.hasDescription(robot.str(UiR.string.plan_remove_a11y, name)))
        return name
    }

    /**
     * "Watch price" on the first hotel -> Start watching: exactly one POST /api/price-watches per tap, and an AI-named
     * hotel without a live price shows the backend's live suggestions ("Watch this instead"); E2E O4.
     */
    private fun watchFirstHotel() {
        rule.onNode(androidx.compose.ui.test.hasTestTag(LIST)).performScrollToKey("HOTELS")
        val watch = robot.hasDescription(robot.prefixOf(ResultsR.string.results_watch_price_a11y), substring = true)
        rule.waitUntil(AppRobot.DEFAULT_TIMEOUT) { robot.existsMerged(watch) || robot.scrollListTo(LIST, watch) }
        rule.onAllNodes(watch).onFirst().performClick()
        robot.waitForText(robot.str(UiR.string.watch_start))
        // OkHttp BASIC: "--> POST http://…/api/price-watches (123-byte body)".
        val create = Regex("""--> POST \S+/api/price-watches(\s|$)""")
        val posts = { robot.httpRequests().count { create.containsMatchIn(it) } }
        val before = posts()
        robot.tap(UiR.string.watch_start)
        val hotelNoun = robot.str(UiR.string.watch_noun_hotel)
        val outcome =
            robot.waitForAny(
                hasText(robot.str(UiR.string.watch_suggestions_heading)),
                hasText(robot.str(UiR.string.watch_price_title_done, hotelNoun)),
                hasText(robot.str(UiR.string.watch_price_title_already, hotelNoun)),
                timeoutMs = AppRobot.LONG_TIMEOUT,
            )
        if (outcome == 0) assertTrue("suggestions offered", robot.scrollToFind(hasText(robot.str(UiR.string.watch_this_instead))))
        Thread.sleep(POST_SETTLE_MS)
        assertEquals("one POST /api/price-watches per tap", before + 1, posts())
        robot.back() // close the sheet
        robot.waitFor(androidx.compose.ui.test.hasTestTag(LIST))
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

        // The search stores the airport code ("LIS"); the summary names the city (E2E O9).
        robot.waitFor(hasText("Lisbon") and isHeading())
        assertFalse("airport code shown as a heading", robot.exists(hasText("LIS") and isHeading()))

        watchFirstHotel()

        val hotel = addFirstUnder(UiR.string.section_hotels, "HOTELS")
        val activity = addFirstUnder(UiR.string.section_activities, "ACTIVITIES")

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
        val searchData = saved.getValue("search_data").jsonObject
        assertTrue("search_data saved: $searchData", searchData["destination"]?.jsonPrimitive?.content == "LIS")
        assertTrue("2 adults: $searchData", searchData["adults"]?.jsonPrimitive?.content == "2")
    }

    private companion object {
        const val LIST = "results_list"
        const val STREAM_TIMEOUT = 300_000L
        const val POST_SETTLE_MS = 2_000L
    }
}
