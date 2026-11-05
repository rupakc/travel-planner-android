package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
import io.github.rupakc.travelplanner.core.ui.R as UiR
import io.github.rupakc.travelplanner.feature.chat.R as ChatR
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Flow 6: a question streams a reply that ends; a plan command adds an item to My Plan. */
@E2eTest
@RunWith(AndroidJUnit4::class)
class ChatE2eTest {
    @get:Rule(order = 0)
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val failureShot = Screenshots.onFailure()
    private lateinit var robot: AppRobot
    private var sent = 0

    @Before fun setUp() {
        BackendApi().resetPreferences(BackendApi().adminToken())
        robot = AppRobot(rule)
    }

    private fun send(text: String, timeoutMs: Long = CHAT_TIMEOUT) {
        robot.chatSend(text, timeoutMs)
        sent++
        Screenshots.capture("debug-chat-$sent")
    }

    private fun planItems(): Int {
        val prefix = robot.str(ChatR.string.chat_my_plan) + ","
        robot.waitFor(robot.hasDescription(prefix, substring = true))
        val node = rule.onAllNodes(robot.hasDescription(prefix, substring = true), useUnmergedTree = true).fetchSemanticsNodes().first()
        val description = node.config[SemanticsProperties.ContentDescription].first()
        return Regex("""\d+""").find(description)?.value?.toInt() ?: 0
    }

    /** Names of result rows that can be added to My Plan ("Add <name> to My Plan"). */
    private fun addableItems(): List<String> {
        val (prefix, suffix) = robot.str(UiR.string.plan_add_a11y, MARK).split(MARK)
        val names = {
            rule.onAllNodes(hasContentDescription(prefix, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } }
                .filter { it.startsWith(prefix) && it.endsWith(suffix) }
                .map { it.removePrefix(prefix).removeSuffix(suffix) }
        }
        rule.waitUntil(AppRobot.DEFAULT_TIMEOUT) { names().isNotEmpty() }
        return names()
    }

    @Test fun question_streamsReply_planCommandAddsItem() {
        robot.signInToHome()
        robot.openTab(io.github.rupakc.travelplanner.R.string.nav_chat)
        robot.waitForText(robot.str(ChatR.string.chat_title))

        send("What is the capital city of Portugal? Answer in one short sentence.")
        robot.waitForText("Lisbon", substring = true)
        assertTrue(robot.exists(hasText(robot.str(ChatR.string.chat_error_stream))).not())
        Screenshots.capture("chat")

        val before = planItems()
        // Chat is knowledge-first: a trip request plans (itinerary card); a modification ("make it cheaper") re-runs
        // the affected specialists and streams their sections; a plan command naming one of those results emits a
        // plan_action. The backend's plan-command prompt only sees hotel counts, not names, so the command names one.
        send("Plan a trip to Lisbon, Portugal from December 1 to December 4, 2026 for 2 adults, flying from New York. We are American citizens.", PLAN_TIMEOUT)
        send("Make it cheaper.", PLAN_TIMEOUT)
        val hotel = addableItems().let { names -> names.firstOrNull { HOTEL_WORDS.containsMatchIn(it) } ?: names.last() }
        send("Add the $hotel to my plan.", PLAN_TIMEOUT)
        rule.waitUntil(AppRobot.DEFAULT_TIMEOUT) { planItems() > before }
        assertTrue(robot.scrollToFind(robot.hasDescription(robot.str(UiR.string.plan_remove_a11y, hotel))))
    }

    private companion object {
        const val CHAT_TIMEOUT = 120_000L
        const val PLAN_TIMEOUT = 300_000L
        const val MARK = "\u0000"
        val HOTEL_WORDS = Regex("(?i)hotel|hostel|inn|suites|palace|residence|apartments|guest")
    }
}
