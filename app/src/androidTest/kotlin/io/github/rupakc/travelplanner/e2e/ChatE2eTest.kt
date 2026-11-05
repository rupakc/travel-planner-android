package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
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
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var robot: AppRobot

    @Before fun setUp() {
        BackendApi().resetPreferences(BackendApi().adminToken())
        robot = AppRobot(rule)
    }

    private fun send(text: String, timeoutMs: Long = CHAT_TIMEOUT) {
        val input = hasTestTag("chat_input")
        robot.waitFor(input)
        rule.onNode(input).performTextInput(text)
        rule.onAllNodes(robot.hasDescription(robot.str(ChatR.string.chat_send))).onFirst().performClick()
        val stop = robot.hasDescription(robot.str(ChatR.string.chat_stop))
        // Streaming: Stop shows while the reply streams, then Send comes back.
        robot.waitFor(stop, AppRobot.DEFAULT_TIMEOUT)
        rule.waitUntil(timeoutMs) { !robot.exists(stop) }
    }

    private fun planItems(): Int {
        val prefix = robot.str(ChatR.string.chat_my_plan) + ","
        robot.waitFor(robot.hasDescription(prefix, substring = true))
        val node = rule.onAllNodes(robot.hasDescription(prefix, substring = true), useUnmergedTree = true).fetchSemanticsNodes().first()
        val description = node.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].first()
        return Regex("""\d+""").find(description)?.value?.toInt() ?: 0
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
        send("Find me hotels in Lisbon, Portugal from December 1 to December 4, 2026 for 2 adults.", PLAN_TIMEOUT)
        send("Add the best rated hotel from those results to my plan.", PLAN_TIMEOUT)
        rule.waitUntil(AppRobot.DEFAULT_TIMEOUT) { planItems() > before }
    }

    private companion object {
        const val CHAT_TIMEOUT = 120_000L
        const val PLAN_TIMEOUT = 300_000L
    }
}
