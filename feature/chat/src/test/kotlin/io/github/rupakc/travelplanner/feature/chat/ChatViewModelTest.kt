package io.github.rupakc.travelplanner.feature.chat

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.chat.ChatMessage
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.testing.Fixtures
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakeChatRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.feature.chat.model.TurnError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val repo = FakeChatRepository()
    private val plans = FakePlansRepository()
    private val store = FakeCurrentPlanStore(plans)

    private fun viewModel() = ChatViewModel(repo, store, TestAppDispatchers(main.testDispatcher))

    @Test
    fun `send streams a reply, persists both turns and creates a titled thread`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                vm.onEvent(ChatUiEvent.Send("Do I need a visa for Thailand?"))
                val done = expectMostRecentItemMatching { it.turns.size == 2 && it.streaming == null }
                assertEquals("Do I need a visa for Thailand?", done.currentTitle)
                assertEquals(ChatMessage.ROLE_USER, done.turns[0].role)
                val reply = done.turns[1]
                assertTrue(reply.content.contains("visa-free"))
                assertEquals(listOf("Plan a week in Bangkok", "Best time to visit?"), reply.suggestions)
                assertFalse(done.isStreaming)
                assertTrue(done.announcement is ReplyAnnouncement.Text)
                cancelAndIgnoreRemainingEvents()
            }
            val request = repo.sent.single()
            assertEquals(listOf(ChatMessage("user", "Do I need a visa for Thailand?")), request.messages)
        }

    @Test
    fun `session context round-trips and history includes earlier turns`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                vm.onEvent(ChatUiEvent.Send("Visa for Thailand?"))
                expectMostRecentItemMatching { it.turns.size == 2 && it.streaming == null }
                vm.onEvent(ChatUiEvent.Send("And the best time?"))
                expectMostRecentItemMatching { it.turns.size == 4 && it.streaming == null }
                cancelAndIgnoreRemainingEvents()
            }
            val second = repo.sent[1]
            assertEquals("Thailand", (second.sessionContext["destination"] as JsonPrimitive).content)
            assertEquals(listOf("user", "assistant", "user"), second.messages.map { it.role })
            assertEquals("And the best time?", second.messages.last().content)
        }

    @Test
    fun `planning stream ends on planning_done and keeps the itinerary`() =
        runTest {
            repo.replies = Fixtures.chatEvents(Fixtures.CHAT_PLANNING)
            val vm = viewModel()
            vm.uiState.test {
                vm.onEvent(ChatUiEvent.Send("Plan 5 days in Tokyo"))
                val done = expectMostRecentItemMatching { it.turns.size == 2 && it.streaming == null }
                val reply = done.turns[1]
                assertTrue(reply.planningDone)
                assertNotNull(reply.itinerary)
                assertTrue(done.announcement is ReplyAnnouncement.PlanReady)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `plan actions are applied to My Plan and selections are sent each turn`() =
        runTest {
            repo.replies =
                listOf(
                    ChatEvent.PlanAction(
                        io.github.rupakc.travelplanner.core.model.stream.PlanActionType.ADD,
                        "activities",
                        kotlinx.serialization.json.buildJsonObject { put("name", JsonPrimitive("Sumo")) },
                    ),
                    ChatEvent.Delta("Added Sumo."),
                    ChatEvent.Done,
                )
            store.toggle(PlanItem.ActivityItem(Activity(name = "Tea ceremony")))
            val vm = viewModel()
            vm.uiState.test {
                vm.onEvent(ChatUiEvent.Send("add sumo"))
                val done = expectMostRecentItemMatching { it.turns.size == 2 && it.streaming == null }
                assertEquals(listOf("Tea ceremony", "Sumo"), done.selections.activities.map { it.name })
                assertEquals(2, done.planCount)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("Tea ceremony"), repo.sent.single().selections.activities.map { it.name })
        }

    @Test
    fun `request failure shows the backend detail and retry re-asks`() =
        runTest {
            repo.failure = AppError.RateLimited("Too many requests — please wait 30 seconds and try again.", 30)
            val vm = viewModel()
            vm.uiState.test {
                vm.onEvent(ChatUiEvent.Send("hello"))
                val failed = expectMostRecentItemMatching { it.turns.size == 2 && it.streaming == null }
                val reply = failed.turns[1]
                assertEquals(TurnError.REQUEST, reply.error)
                assertEquals("Too many requests — please wait 30 seconds and try again.", reply.errorDetail)
                assertTrue(failed.announcement is ReplyAnnouncement.Failed)

                repo.failure = null
                vm.onEvent(ChatUiEvent.Retry(reply.id))
                val retried = expectMostRecentItemMatching { it.turns.size == 2 && it.turns[1].error == null && it.streaming == null }
                assertTrue(retried.turns[1].content.contains("visa-free"))
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(ChatMessage("user", "hello")), repo.sent.last().messages)
        }

    @Test
    fun `stop cancels the stream and keeps the partial reply`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val slow =
                object : FakeChatRepositoryDelegate(repo) {
                    override fun send(request: io.github.rupakc.travelplanner.core.model.chat.ChatRequest): Flow<ChatEvent> =
                        flow {
                            emit(ChatEvent.Delta("Half an answer"))
                            gate.await()
                            emit(ChatEvent.Done)
                        }
                }
            val vm = ChatViewModel(slow, store, TestAppDispatchers(main.testDispatcher))
            vm.uiState.test {
                vm.onEvent(ChatUiEvent.Send("tell me about Rome"))
                val streaming = expectMostRecentItemMatching { it.isStreaming && it.streaming?.content == "Half an answer" }
                assertTrue(streaming.isStreaming)
                vm.onEvent(ChatUiEvent.Stop)
                val stopped = expectMostRecentItemMatching { it.turns.size == 2 && it.streaming == null }
                assertTrue(stopped.turns[1].stopped)
                assertEquals("Half an answer", stopped.turns[1].content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `stop before any event clears the placeholder`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val silent =
                object : FakeChatRepositoryDelegate(repo) {
                    override fun send(request: io.github.rupakc.travelplanner.core.model.chat.ChatRequest): Flow<ChatEvent> =
                        flow { gate.await() }
                }
            val vm = ChatViewModel(silent, store, TestAppDispatchers(main.testDispatcher))
            vm.uiState.test {
                vm.onEvent(ChatUiEvent.Send("hello"))
                expectMostRecentItemMatching { it.isStreaming }
                vm.onEvent(ChatUiEvent.Stop)
                val stopped = expectMostRecentItemMatching { !it.isStreaming && it.turns.size == 2 }
                assertTrue(stopped.turns[1].stopped)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `delete hides a thread until undo or commit`() =
        runTest {
            val first = repo.createThread("Tokyo")
            repo.createThread("Lisbon")
            val vm = viewModel()
            vm.uiState.test {
                expectMostRecentItemMatching { it.threads.size == 2 }
                vm.onEvent(ChatUiEvent.DeleteThread(first))
                expectMostRecentItemMatching { it.threads.map { t -> t.title } == listOf("Lisbon") }
                vm.onEvent(ChatUiEvent.UndoDelete(first))
                expectMostRecentItemMatching { it.threads.size == 2 }
                vm.onEvent(ChatUiEvent.DeleteThread(first))
                vm.onEvent(ChatUiEvent.CommitDelete(first))
                expectMostRecentItemMatching { it.threads.map { t -> t.title } == listOf("Lisbon") }
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(repo.threads.first().none { it.id == first })
        }

    @Test
    fun `search filters threads and rename updates the title`() =
        runTest {
            val id = repo.createThread("Tokyo food")
            repo.createThread("Lisbon")
            val vm = viewModel()
            vm.uiState.test {
                vm.onEvent(ChatUiEvent.SearchThreads("tok"))
                expectMostRecentItemMatching { it.threads.map { t -> t.title } == listOf("Tokyo food") }
                vm.onEvent(ChatUiEvent.RenameThread(id, "  Tokyo ramen  "))
                expectMostRecentItemMatching { it.threads.singleOrNull()?.title == "Tokyo ramen" }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `open without a thread id opens the most recent thread`() =
        runTest {
            repo.createThread("Older")
            val newest = repo.createThread("Newest")
            val vm = viewModel()
            vm.uiState.test {
                vm.open(null)
                expectMostRecentItemMatching { it.currentThreadId == newest }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `import link and My Plan become navigation effects`() =
        runTest {
            val vm = viewModel()
            vm.effectFlow.test {
                vm.onEvent(ChatUiEvent.OpenImport("https://example.com/trip"))
                assertEquals(ChatEffect.OpenImport("https://example.com/trip"), awaitItem())
                vm.onEvent(ChatUiEvent.OpenMyPlan)
                assertEquals(ChatEffect.OpenMyPlan, awaitItem())
            }
        }

    @Test
    fun `save itinerary creates a plan from the chat itinerary and opens it`() =
        runTest {
            repo.replies = Fixtures.chatEvents(Fixtures.CHAT_PLANNING)
            val vm = viewModel()
            var turnId = -1L
            vm.uiState.test {
                vm.onEvent(ChatUiEvent.Send("Plan 5 days in Tokyo"))
                turnId = expectMostRecentItemMatching { it.turns.size == 2 && it.streaming == null }.turns[1].id
                cancelAndIgnoreRemainingEvents()
            }
            vm.effectFlow.test {
                vm.onEvent(ChatUiEvent.SaveItinerary(turnId))
                val effect = awaitItem() as ChatEffect.OpenPlan
                val saved = plans.plans.first().single { it.id == effect.planId }
                assertEquals("Tokyo", saved.name)
                assertTrue(saved.selections.itinerarySlots.isNotEmpty())
                assertNotNull(saved.selections.itinerarySnapshot)
            }
        }

    @Test
    fun `save asks before replacing an unsaved plan`() =
        runTest {
            repo.replies = Fixtures.chatEvents(Fixtures.CHAT_PLANNING)
            store.toggle(PlanItem.ActivityItem(Activity(name = "Tea ceremony")))
            val vm = viewModel()
            vm.uiState.test {
                vm.onEvent(ChatUiEvent.Send("Plan 5 days in Tokyo"))
                val turnId = expectMostRecentItemMatching { it.turns.size == 2 && it.streaming == null }.turns[1].id
                vm.onEvent(ChatUiEvent.SaveItinerary(turnId))
                expectMostRecentItemMatching { it.confirmReplaceTurnId == turnId }
                vm.onEvent(ChatUiEvent.DismissReplacePlan)
                assertNull(expectMostRecentItemMatching { it.confirmReplaceTurnId == null }.confirmReplaceTurnId)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

/** Awaits items until one matches (the state flow may emit intermediate states). */
private suspend fun app.cash.turbine.ReceiveTurbine<ChatUiState>.expectMostRecentItemMatching(predicate: (ChatUiState) -> Boolean): ChatUiState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

/** Delegates everything to [base] so a test can override one method. */
private open class FakeChatRepositoryDelegate(private val base: FakeChatRepository) :
    io.github.rupakc.travelplanner.core.data.repository.ChatRepository by base
