package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.CarbonRepositoryImpl
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonDiet
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeCarbonPrefsStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeJourneyFlagsRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `POST /api/carbon/trip` against a MockWebServer, the `features.carbon` gate and the remembered diet. */
class CarbonRepositoryImplTest {
    private val server = MockWebServer().apply { start() }
    private val flags = FakeJourneyFlagsRepository(JourneyFlags(carbon = true))
    private val prefs = FakeCarbonPrefsStore()
    private val repo = CarbonRepositoryImpl(TestNetwork(server.url("/").toString(), dispatchers = TestAppDispatchers(Dispatchers.IO)).api(), flags, prefs)

    @After fun tearDown() = server.close()

    private fun respond(code: Int, body: String) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    private val body = Json.parseToJsonElement("""{"search":{"origin":"London"},"results":{"flights":{},"hotels":{"results":[]}},"diet":"mixed"}""").jsonObject

    @Test
    fun `posts the body as is and decodes the answer`() =
        runTest(timeout = 10.seconds) {
            respond(200, """{"total_kg":245.1,"travellers":2,"categories":[{"key":"flights","kg":159.4,"share":0.65,"included":true}]}""")
            val result = repo.footprint(body) as AppResult.Success
            assertEquals(245.1, result.value.totalKg!!, 0.0)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/carbon/trip", req.target)
            assertEquals(body, Json.parseToJsonElement(req.body!!.utf8()))
        }

    @Test
    fun `a 404 is NotFound`() =
        runTest(timeout = 10.seconds) {
            respond(404, """{"detail":"Not Found"}""")
            assertTrue((repo.footprint(body) as AppResult.Failure).error is AppError.NotFound)
        }

    @Test
    fun `available follows features carbon`() =
        runTest(timeout = 10.seconds) {
            assertTrue(repo.available())
            flags.flags = JourneyFlags.OFF
            assertFalse(repo.available())
        }

    @Test
    fun `the diet is remembered as its wire value`() =
        runTest(timeout = 10.seconds) {
            assertEquals(CarbonDiet.MIXED, repo.diet.first())
            repo.setDiet(CarbonDiet.VEGETARIAN)
            assertEquals("vegetarian", prefs.diet.value)
            assertEquals(CarbonDiet.VEGETARIAN, repo.diet.first())
        }
}
