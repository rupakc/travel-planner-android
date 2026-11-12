package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.PrepRepositoryImpl
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameEvents
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrepRepositoryImplTest {
    private val server = MockWebServer()
    private val events = FakeGameEvents()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun repository(): PrepRepositoryImpl {
        val net = TestNetwork(server.url("/").toString(), FakeSessionStore(Session("token", TestUser)), TestAppDispatchers(Dispatchers.IO))
        return PrepRepositoryImpl(net.api(), events)
    }

    private fun respond(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `prep sends the local date and decodes items`() =
        runTest {
            respond(
                200,
                """{"items":[{"id":"visa","status":"na","auto":false,"due":null,"overdue":false,"facts":{"visa_type":"visa-free"}},""" +
                    """{"id":"offline_pack","status":"todo","auto":true,"due":"2027-03-31","overdue":false,"facts":{"saved_at":null}}],""" +
                    """"done":0,"total":1,"percent":0,"complete":false}""",
            )
            val value = (repository().prep(12, "2026-11-12") as AppResult.Success).value
            assertEquals("/api/trips/12/prep?today=2026-11-12", server.takeRequest().target)
            assertEquals(listOf("visa", "offline_pack"), value.items.map { it.id })
            assertTrue(value.items[1].auto)
        }

    @Test
    fun `toggle sends done and forwards the trip_ready delta`() =
        runTest {
            respond(
                200,
                """{"items":[],"done":9,"total":9,"percent":100,"complete":true,"item":{"id":"passport","status":"done"},""" +
                    """"game":{"xp":50,"awards":[{"rule":"trip_ready","points":50}],"badges":[],"level_up":null}}""",
            )
            val value = (repository().toggle(12, "passport", true) as AppResult.Success).value
            val request = server.takeRequest()
            assertEquals("/api/trips/12/prep/passport", request.target)
            assertEquals("""{"done":true}""", request.body?.utf8())
            assertTrue(value.complete)
            assertEquals(1, events.published.size)
            assertEquals("trip_ready", events.published.single().awards.single().rule)
        }

    @Test
    fun `a conflict keeps its error code and publishes nothing`() =
        runTest {
            respond(409, """{"detail":"Automatic item","error_code":"prep_item_auto"}""")
            val error = (repository().toggle(12, "offline_pack", true) as AppResult.Failure).error as AppError.Client
            assertEquals("prep_item_auto", error.code)
            assertTrue(events.published.isEmpty())
        }
}
