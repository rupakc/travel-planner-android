package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.RecapRepositoryImpl
import io.github.rupakc.travelplanner.core.model.settings.Session
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

class RecapRepositoryImplTest {
    private val server = MockWebServer()
    private val events = FakeGameEvents()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun repository(): RecapRepositoryImpl {
        val net = TestNetwork(server.url("/").toString(), FakeSessionStore(Session("token", TestUser)), TestAppDispatchers(Dispatchers.IO))
        return RecapRepositoryImpl(net.api(), events)
    }

    private fun respond(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `recap sends the local date and decodes a null distance`() =
        runTest(timeout = 30.seconds) {
            respond(200, """{"trip_id":12,"title":"Lisbon","distance_km":null,"countries":[{"code":"PT","new":true}],"can_complete":true}""")
            val value = (repository().recap(12, "2027-04-09") as AppResult.Success).value
            assertEquals("/api/trips/12/recap?today=2027-04-09", server.takeRequest().target)
            assertEquals(null, value.distanceKm)
            assertTrue(value.countries.single().new)
        }

    @Test
    fun `complete posts, and a 409 recap_not_ready keeps its code`() =
        runTest(timeout = 30.seconds) {
            respond(200, """{"completed_at":"2027-04-09T10:00:00Z","new_countries":["PT"]}""")
            val ok = (repository().complete(12, "2027-04-09") as AppResult.Success).value
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/trips/12/recap/complete?today=2027-04-09", request.target)
            assertEquals(listOf("PT"), ok.newCountries)

            respond(409, """{"detail":"Not yet","error_code":"recap_not_ready"}""")
            val failure = repository().complete(12, "2027-04-01") as AppResult.Failure
            assertEquals("recap_not_ready", (failure.error as AppError.Client).code)
        }
}
