package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.JournalRepositoryImpl
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.trips.CreateEntryRequest
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceRequest
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameEvents
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JournalRepositoryImplTest {
    private val server = MockWebServer()
    private val events = FakeGameEvents()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun repository(): JournalRepositoryImpl {
        val net = TestNetwork(server.url("/").toString(), FakeSessionStore(Session("token", TestUser)), TestAppDispatchers(Dispatchers.IO))
        return JournalRepositoryImpl(net.api(), events)
    }

    private fun respond(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private val place = """{"place_key":"activity:senso-ji","name":"Senso-ji","kind":"activity","visited":true,"stars":4,"visited_at":"2026-11-13T01:00:00Z"}"""

    @Test
    fun `entries decode and a create carries the client id`() =
        runTest(timeout = 30.seconds) {
            respond(201, """{"id":5,"client_id":"c1","day":"2026-11-13","body":"hi","mood":"good"}""")
            val entry = (repository().createEntry(12, CreateEntryRequest("c1", "hi", "2026-11-13", "good")) as AppResult.Success).value
            val request = server.takeRequest()
            assertEquals("/api/trips/12/journal", request.target)
            assertTrue(request.body!!.utf8().contains("\"client_id\":\"c1\""))
            assertEquals(5L, entry.id)
        }

    @Test
    fun `putPlace sends the body, forwards the delta and decodes the place`() =
        runTest(timeout = 30.seconds) {
            respond(200, """{"place":$place,"game":{"xp":5,"awards":[{"rule":"place_visited","points":5}],"badges":[],"level_up":null}}""")
            val result = repository().putPlace(12, "activity:senso-ji", PutPlaceRequest("Senso-ji", "activity", visited = true, stars = 4)) as AppResult.Success
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/trips/12/places/activity:senso-ji", request.target)
            val body = request.body!!.utf8()
            assertTrue(body.contains("\"visited\":true") && body.contains("\"stars\":4") && !body.contains("note"))
            assertTrue(result.value.place.visited)
            assertEquals(1, events.published.size)
        }

    @Test
    fun `clearing stars sends an explicit null`() =
        runTest(timeout = 30.seconds) {
            respond(200, """{"place":{"place_key":"activity:senso-ji","name":"Senso-ji","kind":"activity","visited":true,"stars":null},"game":null}""")
            repository().putPlace(12, "activity:senso-ji", PutPlaceRequest("Senso-ji", "activity", clearStars = true))
            assertTrue(server.takeRequest().body!!.utf8().contains("\"stars\":null"))
            assertTrue(events.published.isEmpty())
        }

    @Test
    fun `a limit error carries its code`() =
        runTest(timeout = 30.seconds) {
            respond(409, """{"detail":{"code":"places_limit","message":"too many"}}""")
            val error = (repository().putPlace(12, "place:x", PutPlaceRequest("X", "place")) as AppResult.Failure).error as AppError.Client
            assertEquals("places_limit", error.code)
        }
}
