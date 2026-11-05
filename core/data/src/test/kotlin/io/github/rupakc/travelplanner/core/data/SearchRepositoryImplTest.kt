package io.github.rupakc.travelplanner.core.data

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.data.impl.SearchRepositoryImpl
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.network.sse.SearchStreamClient
import io.github.rupakc.travelplanner.core.network.sse.SseClient
import io.github.rupakc.travelplanner.core.testing.Fixtures
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchRepositoryImplTest {
    private val server = MockWebServer()
    private val request = TravelSearchRequest(origin = "NYC", destination = "Tokyo", departureDate = "2026-04-01", nationality = "American")

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun repository(): SearchRepositoryImpl {
        val dispatchers = TestAppDispatchers(Dispatchers.IO)
        val net = TestNetwork(server.url("/").toString(), dispatchers = dispatchers)
        val stream = SearchStreamClient(SseClient(net.client, dispatchers), dispatchers)
        return SearchRepositoryImpl(stream, net.api(), net.api(), CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }

    private fun sse(name: String) = MockResponse.Builder().code(200).body(Fixtures.read(name)).build()

    @Test
    fun `full stream folds into DONE with every section and replace-by-type`() = runTest {
        server.enqueue(sse(Fixtures.SEARCH_FULL))
        val state = repository().results(request).last()
        assertEquals(StreamPhase.DONE, state.phase)
        assertEquals(SectionId.entries.toSet(), state.sections.keys)
        assertTrue(state.statuses.values.all { it == SectionStatus.DONE })
        // the enriched re-yield replaced the first hotels event
        assertEquals(true, (state.sections[SectionId.HOTELS]?.data as HotelsData).results.single().linkVerified)
        assertEquals("green", state.confidence?.grade)
        assertEquals("wait", state.pricingAdvisor?.recommendation)
    }

    @Test
    fun `stream without done is retried once then reported interrupted`() = runTest {
        server.enqueue(sse(Fixtures.SEARCH_INTERRUPTED))
        server.enqueue(sse(Fixtures.SEARCH_INTERRUPTED))
        val state = repository().results(request).last()
        assertEquals(StreamPhase.INTERRUPTED, state.phase)
        assertEquals(1, state.retries)
        assertEquals(2, server.requestCount)
        assertEquals(SectionStatus.DONE, state.statuses[SectionId.VISA])
    }

    @Test
    fun `retry that succeeds ends DONE`() = runTest {
        server.enqueue(sse(Fixtures.SEARCH_INTERRUPTED))
        server.enqueue(sse(Fixtures.SEARCH_FULL))
        assertEquals(StreamPhase.DONE, repository().results(request).last().phase)
    }

    @Test
    fun `http error before streaming fails with the mapped error`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "5").body("""{"detail":"Too many requests — please wait 5 seconds and try again."}""").build())
        val state = repository().results(request).last()
        assertEquals(StreamPhase.FAILED, state.phase)
        assertTrue(state.error is AppError.RateLimited)
    }

    @Test
    fun `start drives the app-wide current state`() = runTest {
        server.enqueue(sse(Fixtures.SEARCH_FULL))
        val repo = repository()
        repo.current.test {
            assertEquals(StreamPhase.IDLE, awaitItem().phase)
            repo.start(request)
            var latest = awaitItem()
            while (latest.phase != StreamPhase.DONE) latest = awaitItem()
            assertEquals(request, latest.request)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(StreamPhase.DONE, repo.current.first().phase)
    }
}
