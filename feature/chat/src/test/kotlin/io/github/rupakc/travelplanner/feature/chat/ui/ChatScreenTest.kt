package io.github.rupakc.travelplanner.feature.chat.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.data.repository.ChatThread
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.testing.Fixtures
import io.github.rupakc.travelplanner.feature.chat.ChatUiEvent
import io.github.rupakc.travelplanner.feature.chat.ChatUiState
import io.github.rupakc.travelplanner.feature.chat.ReplyAnnouncement
import io.github.rupakc.travelplanner.feature.chat.model.ChatStreamReducer
import io.github.rupakc.travelplanner.feature.chat.model.ChatTurn
import io.github.rupakc.travelplanner.feature.chat.model.TurnError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ChatScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<ChatUiEvent>()

    private fun show(state: ChatUiState, theme: ThemeMode = ThemeMode.LIGHT, modifier: Modifier = Modifier) {
        compose.setContent {
            TravelTheme(themeMode = theme) {
                Box(modifier) { ChatScreen(state = state, onEvent = { events += it }) }
            }
        }
    }

    private fun user(id: Long, text: String) = ChatTurn(id = id, role = "user", content = text)

    private fun assistant(id: Long, text: String) = ChatTurn(id = id, role = "assistant", content = text)

    @Test
    fun `empty state shows the web starters and sends one`() {
        show(ChatUiState())
        compose.onNodeWithText("Travel Planner Chat").assertExists()
        compose.onNodeWithText("Plan a 5-day trip to Tokyo on a \$2,000 budget").performClick()
        assertEquals(ChatUiEvent.Send("Plan a 5-day trip to Tokyo on a \$2,000 budget"), events.single())
    }

    @Test
    fun `typing and send emits the text and clears the input`() {
        show(ChatUiState())
        compose.onNodeWithTag(ChatTags.INPUT).performTextInput("Best time for Kyoto?")
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals(ChatUiEvent.Send("Best time for Kyoto?"), events.single())
        compose.onNodeWithText("Best time for Kyoto?").assertDoesNotExist()
    }

    @Test
    fun `while streaming the stop button cancels and the input is disabled`() {
        val streaming = ChatStreamReducer.start().turn.copy(content = "Kyoto is lovely")
        show(ChatUiState(currentThreadId = 1, turns = listOf(user(1, "Kyoto?")), streaming = streaming))
        compose.onNodeWithTag(ChatTags.INPUT).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Send").assertDoesNotExist()
        compose.onNodeWithContentDescription("Stop").performClick()
        assertEquals(ChatUiEvent.Stop, events.single())
    }

    @Test
    fun `suggestion chips and follow-up lines send themselves`() {
        val reply = assistant(2, "Thailand is visa-free for 60 days.\n— Want a 7-day plan?").copy(suggestions = listOf("Best time to visit?"))
        show(ChatUiState(currentThreadId = 1, turns = listOf(user(1, "Visa?"), reply)))
        compose.onNodeWithText("Want a 7-day plan?").performClick()
        compose.onNodeWithText("Best time to visit?").performClick()
        assertEquals(listOf(ChatUiEvent.Send("Want a 7-day plan?"), ChatUiEvent.Send("Best time to visit?")), events)
    }

    @Test
    fun `failed reply shows the friendly copy and retries`() {
        val failed = ChatTurn(id = 2, role = "assistant", error = TurnError.EMPTY)
        show(ChatUiState(currentThreadId = 1, turns = listOf(user(1, "hi"), failed)))
        compose.onNodeWithText("Sorry — I didn't get a response there. Please try asking again.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(ChatUiEvent.Retry(2), events.single())
    }

    @Test
    fun `rate limit detail is shown as the backend sent it`() {
        val failed = ChatTurn(id = 2, role = "assistant", error = TurnError.REQUEST, errorDetail = "Too many requests — please wait 30 seconds and try again.")
        show(ChatUiState(currentThreadId = 1, turns = listOf(user(1, "hi"), failed)))
        compose.onNodeWithText("Too many requests — please wait 30 seconds and try again.").assertExists()
    }

    @Test
    fun `jump to latest appears after scrolling up and returns to the newest message`() {
        val turns = (1L..30L).map { if (it % 2 == 1L) user(it, "Question $it") else assistant(it, "Answer $it") }
        show(ChatUiState(currentThreadId = 1, turns = turns))
        compose.onNodeWithContentDescription("Jump to latest").assertDoesNotExist()
        compose.onNodeWithTag(ChatTags.MESSAGES).performScrollToIndex(25)
        compose.onNodeWithContentDescription("Jump to latest").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Jump to latest").assertDoesNotExist()
        compose.onNodeWithText("Answer 30").assertExists()
    }

    @Test
    fun `finished replies are announced in a polite live region`() {
        show(
            ChatUiState(
                currentThreadId = 1,
                turns = listOf(user(1, "Visa?"), assistant(2, "Visa-free for 60 days.")),
                announcement = ReplyAnnouncement.Text(1, "Visa-free for 60 days."),
            ),
        )
        compose.onNodeWithTag(ChatTags.ANNOUNCER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(hasContentDescription("Assistant: Visa-free for 60 days."))
    }

    @Test
    fun `returning to the screen does not re-announce the last reply`() {
        val restoration = StateRestorationTester(compose)
        val state =
            ChatUiState(
                currentThreadId = 1,
                turns = listOf(user(1, "Visa?"), assistant(2, "Visa-free for 60 days.")),
                announcement = ReplyAnnouncement.Text(1, "Visa-free for 60 days."),
            )
        restoration.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { ChatScreen(state = state, onEvent = { events += it }) } }
        compose.onNodeWithTag(ChatTags.ANNOUNCER).assert(hasContentDescription("Assistant: Visa-free for 60 days."))
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(ChatTags.ANNOUNCER).assert(hasContentDescription(""))
    }

    @Test
    fun `my plan button reads its item count and opens the plan`() {
        val selections = PlanSelections(activities = listOf(Activity(name = "Sumo"), Activity(name = "Tea")))
        show(ChatUiState(selections = selections))
        compose.onNodeWithContentDescription("My Plan, 2 items").performClick()
        assertEquals(ChatUiEvent.OpenMyPlan, events.single())
    }

    @Test
    fun `trip plan renders the route and day cards and saves`() {
        val turn = Fixtures.chatEvents(Fixtures.CHAT_PLANNING).fold(ChatStreamReducer.start(), ChatStreamReducer::reduce).turn.copy(id = 2)
        show(ChatUiState(currentThreadId = 1, turns = listOf(user(1, "Plan Tokyo"), turn)), theme = ThemeMode.DARK)
        compose.onNodeWithText("New York → Tokyo").assertExists()
        compose.onNodeWithContentDescription("Open Tokyo in Maps").assertExists()
        compose.onNodeWithText("Day 1 · Wed, Apr 1").assertExists()
        compose.onNodeWithText("Save as plan").performClick()
        assertEquals(ChatUiEvent.SaveItinerary(2), events.single())
    }

    @Test
    fun `expanded width shows threads beside the conversation`() {
        val threads = listOf(ChatThread(1, "Tokyo food", 0, 0), ChatThread(2, "Lisbon", 0, 0))
        show(ChatUiState(threads = threads, currentThreadId = 1), modifier = Modifier.requiredSize(1000.dp, 700.dp))
        compose.onNodeWithText("Lisbon").performClick()
        compose.onNodeWithText("Ask about your next trip…").assertExists()
        assertTrue(events.contains(ChatUiEvent.SelectThread(2)))
    }

    @Test
    fun `compact width opens the threads list from the header`() {
        val threads = listOf(ChatThread(1, "Tokyo food", 0, 0))
        show(ChatUiState(threads = threads, currentThreadId = 1))
        compose.onNodeWithText("Tokyo food").assertExists() // header title
        compose.onNodeWithContentDescription("Show chats").performClick()
        compose.onNodeWithText("Search chats").assertExists()
        compose.onNodeWithContentDescription("More options for Tokyo food").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(ChatUiEvent.DeleteThread(1), events.single())
    }
}
