package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppException
import io.github.rupakc.travelplanner.core.model.chat.ChatMessage
import io.github.rupakc.travelplanner.core.model.chat.ChatRequest
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import io.github.rupakc.travelplanner.core.network.sse.ChatStreamClient
import io.github.rupakc.travelplanner.core.network.sse.SearchStreamClient
import io.github.rupakc.travelplanner.core.network.sse.SseClient
import io.github.rupakc.travelplanner.core.testing.Fixtures
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamClientsTest {
    private val server = MockWebServer()
    private val request = TravelSearchRequest(origin = "NYC", destination = "Tokyo", departureDate = "2026-04-01", nationality = "American")

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun clients(): Pair<SearchStreamClient, ChatStreamClient> {
        val dispatchers = TestAppDispatchers(Dispatchers.IO)
        val net = TestNetwork(server.url("/").toString(), dispatchers = dispatchers)
        val sse = SseClient(net.client, dispatchers)
        return SearchStreamClient(sse, dispatchers) to ChatStreamClient(sse, dispatchers)
    }

    private fun sse(name: String) =
        MockResponse.Builder().code(
            200,
        ).addHeader("Content-Type", "text/event-stream").body(Fixtures.read(name)).build()

    @Test
    fun `search stream decodes fixture and stops at done`() =
        runTest {
            server.enqueue(sse(Fixtures.SEARCH_FULL))
            val events = clients().first.stream(request).toList()
            assertEquals(SearchEvent.Done, events.last())
            val recorded = server.takeRequest()
            assertEquals("/api/search", recorded.target)
            assertTrue(recorded.body!!.utf8().contains("\"destination\":\"Tokyo\""))
        }

    @Test
    fun `search stream without done throws Interrupted after emitting what arrived`() =
        runTest {
            server.enqueue(sse(Fixtures.SEARCH_INTERRUPTED))
            val received = mutableListOf<SearchEvent>()
            val error = runCatching { clients().first.stream(request).collect { received += it } }.exceptionOrNull()
            assertTrue((error as AppException).error is AppError.Interrupted)
            assertEquals(2, received.size)
        }

    @Test
    fun `429 before streaming maps to RateLimited`() =
        runTest {
            server.enqueue(
                MockResponse.Builder().code(
                    429,
                ).addHeader("Retry-After", "12").body("""{"detail":"Too many requests — please wait 12 seconds and try again."}""").build(),
            )
            val error = runCatching { clients().first.stream(request).toList() }.exceptionOrNull() as AppException
            assertEquals(12L, (error.error as AppError.RateLimited).retryAfterSeconds)
        }

    @Test
    fun `chat stream ends on planning_done`() =
        runTest {
            server.enqueue(sse(Fixtures.CHAT_PLANNING))
            val events = clients().second.stream(ChatRequest(messages = listOf(ChatMessage("user", "Plan Tokyo")))).toList()
            assertEquals(ChatEvent.PlanningDone, events.last())
            assertEquals("/api/chat", server.takeRequest().target)
        }
}
