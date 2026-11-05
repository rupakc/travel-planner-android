package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.SystemAppClock
import io.github.rupakc.travelplanner.core.data.impl.ChatRepositoryImpl
import io.github.rupakc.travelplanner.core.database.ChatDao
import io.github.rupakc.travelplanner.core.model.chat.ChatMessage
import io.github.rupakc.travelplanner.core.model.chat.ChatRequest
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.network.sse.ChatStreamClient
import io.github.rupakc.travelplanner.core.network.sse.SseClient
import io.github.rupakc.travelplanner.core.testing.Fixtures
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.mockk.mockk
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

class ChatRepositoryImplTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun repository(): ChatRepositoryImpl {
        val dispatchers = TestAppDispatchers(Dispatchers.IO)
        val net = TestNetwork(server.url("/").toString(), dispatchers = dispatchers)
        val client = ChatStreamClient(SseClient(net.client, dispatchers), dispatchers)
        return ChatRepositoryImpl(client, mockk<ChatDao>(relaxed = true), FakeSessionStore(), SystemAppClock())
    }

    private val request = ChatRequest(messages = listOf(ChatMessage(ChatMessage.ROLE_USER, "Plan Tokyo")))

    @Test
    fun `planning stream terminates on planning_done`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Fixtures.read(Fixtures.CHAT_PLANNING)).build())
        val events = repository().send(request).toList()
        assertEquals(ChatEvent.PlanningDone, events.last())
        assertTrue(events.any { it is ChatEvent.ComprehensiveItinerary })
    }

    @Test
    fun `qa stream ends on done and request carries selections with web keys`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Fixtures.read(Fixtures.CHAT_QA)).build())
        val events = repository().send(request).toList()
        assertEquals(ChatEvent.Done, events.last())
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"selections\":{\"flight\":null,\"flights\":[]"))
        assertTrue(body.contains("\"session_context\":{}"))
    }

    @Test
    fun `stream that just ends completes without error`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("data: {\"type\":\"delta\",\"text\":\"hi\"}\n\n").build())
        assertEquals(listOf<ChatEvent>(ChatEvent.Delta("hi")), repository().send(request).toList())
    }
}
