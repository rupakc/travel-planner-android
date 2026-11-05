package io.github.rupakc.travelplanner.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.R as AppR
import io.github.rupakc.travelplanner.feature.plan.R as PlanR
import kotlinx.serialization.json.jsonPrimitive
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Flow 4: My trips -> search finds the plan -> trip detail -> itinerary -> share link (token on the server) -> .ics intent. */
@E2eTest
@RunWith(AndroidJUnit4::class)
class TripsE2eTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val api = BackendApi()
    private lateinit var robot: AppRobot

    @Before fun setUp() {
        api.resetPreferences(api.adminToken())
        robot = AppRobot(rule)
        Intents.init()
        // Nothing leaves the app: external activities (chooser, calendar) get a canned result.
        intending(not(isInternal())).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After fun tearDown() = Intents.release()

    @Test fun myTrips_detail_share_calendar() {
        robot.signInToHome()
        val flows = PlanFlows(robot, api)
        val plan = flows.existingItineraryPlan(PlanFlows.PLAN_PREFIX)
        val (id, name) =
            if (plan != null) {
                plan.getValue("id").jsonPrimitive.content to plan.getValue("name").jsonPrimitive.content
            } else {
                val n = PlanFlows.PLAN_PREFIX + " " + System.currentTimeMillis() % 100_000
                flows.createItineraryPlan(n, note = null).first to n
            }

        robot.openTab(AppR.string.nav_plans)
        robot.waitForText(robot.str(PlanR.string.plan_search_hint))
        robot.typeInFirstField(name)
        robot.waitForText(name)
        robot.tap(name)

        // Trip detail renders the itinerary.
        robot.waitForText(robot.prefixOf(PlanR.string.plan_full_itinerary), substring = true)
        assertTrue(robot.scrollToFind(hasText(robot.str(PlanR.string.plan_full_itinerary, "").substringBefore("(").trim(), substring = true)))
        Screenshots.capture("trip-detail")

        // Share link -> the server holds a token.
        robot.tap(PlanR.string.plan_action_share_link)
        rule.waitUntil(AppRobot.DEFAULT_TIMEOUT) { api.plan(api.adminToken(), id)["share_token"]?.jsonPrimitive?.content?.isNotBlank() == true }

        // Add to calendar -> a chooser for an ACTION_SEND of a text/calendar file.
        // Add to calendar opens a sheet: per-event inserts or one .ics for everything.
        robot.tap(PlanR.string.plan_action_calendar)
        robot.tap(PlanR.string.plan_calendar_export_all)
        val icsShare = allOf(hasAction(Intent.ACTION_CHOOSER), hasExtra(equalTo(Intent.EXTRA_INTENT), allOf(hasAction(Intent.ACTION_SEND), hasType("text/calendar"))))
        val fired = runCatching { rule.waitUntil(AppRobot.DEFAULT_TIMEOUT) { Intents.getIntents().any { icsShare.matches(it) } } }
        assertTrue("recorded intents: " + Intents.getIntents().map { it.toString() + " " + it.extras?.keySet() + " inner=" + (it.extras?.get(Intent.EXTRA_INTENT)) }, fired.isSuccess)
    }
}
