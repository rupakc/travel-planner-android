package io.github.rupakc.travelplanner.core.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.ExpensesRepositoryImpl
import io.github.rupakc.travelplanner.core.data.offline.fixedClock
import io.github.rupakc.travelplanner.core.data.offline.memoryDb
import io.github.rupakc.travelplanner.core.database.TravelDatabase
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.trips.CreateExpenseRequest
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
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
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ExpensesRepositoryImplTest {
    private val server = MockWebServer()
    private val sessions = FakeSessionStore(Session("token", TestUser))
    private lateinit var db: TravelDatabase

    @Before fun setUp() {
        server.start()
        db = memoryDb()
    }

    @After fun tearDown() {
        server.close()
        db.close()
    }

    private fun repository(): ExpensesRepositoryImpl {
        val net = TestNetwork(server.url("/").toString(), sessions, TestAppDispatchers(Dispatchers.IO))
        return ExpensesRepositoryImpl(net.api(), sessions, db.tripCacheDao(), fixedClock)
    }

    private fun respond(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `list decodes totals, unconverted currencies and the rate`() =
        runTest(timeout = 30.seconds) {
            respond(
                200,
                """{"expenses":[{"id":1,"client_id":"c1","amount":"12.50","currency":"EUR","category":"food","note":null,"spent_on":"2027-04-01"}],""" +
                    """"totals":{"by_currency":{"EUR":"12.50"},"usd_estimate":"13.60","by_category_usd":{"food":"13.60"},"unconverted":["GBP"]},""" +
                    """"rate":{"local":"EUR","usd_to_local":0.92,"as_of":"2027-04-01T00:00:00Z"}}""",
            )
            val value = (repository().list(7) as AppResult.Success).value
            assertEquals("/api/trips/7/expenses", server.takeRequest().target)
            assertEquals("12.50", value.expenses.single().amount)
            assertEquals(listOf("GBP"), value.totals.unconverted)
            assertEquals("EUR", value.rate?.local)
        }

    @Test
    fun `create posts the client id and a 422 keeps its code`() =
        runTest(timeout = 30.seconds) {
            respond(422, """{"detail":{"code":"amount_invalid","message":"Bad amount"}}""")
            val result = repository().create(7, CreateExpenseRequest("11111111-1111-4111-8111-111111111111", "0", "EUR", "food", null, "2027-04-01"))
            assertTrue(server.takeRequest().body!!.utf8().contains("\"client_id\":\"11111111-1111-4111-8111-111111111111\""))
            val error = (result as AppResult.Failure).error as AppError.Client
            assertEquals("amount_invalid", error.code)
        }

    @Test
    fun `delete sends DELETE`() =
        runTest(timeout = 30.seconds) {
            respond(204, "")
            assertTrue(repository().delete(7, 3) is AppResult.Success)
            val req = server.takeRequest()
            assertEquals("DELETE", req.method)
            assertEquals("/api/trips/7/expenses/3", req.target)
        }

    private val body =
        """{"expenses":[{"id":1,"client_id":"c1","amount":"12.50","currency":"EUR","category":"food","note":null,"spent_on":"2027-04-01"}],""" +
            """"totals":{"by_currency":{"EUR":"12.50"},"usd_estimate":"13.60","by_category_usd":{"food":"13.60"},"unconverted":[]},""" +
            """"rate":{"local":"EUR","usd_to_local":0.92,"as_of":"2027-04-01T00:00:00Z"}}"""

    @Test
    fun `a cold start with no network serves the expenses saved earlier`() =
        runTest(timeout = 30.seconds) {
            respond(200, body)
            repository().list(7)
            respond(503, """{"detail":"down"}""")
            val offline = (repository().list(7) as AppResult.Success).value
            assertEquals("12.50", offline.expenses.single().amount)
            assertEquals("EUR", offline.rate?.local)
        }

    @Test
    fun `a trip never opened online stays a failure, and a client error is not masked`() =
        runTest(timeout = 30.seconds) {
            respond(503, """{"detail":"down"}""")
            assertTrue(repository().list(9) is AppResult.Failure)
            respond(200, body)
            repository().list(7)
            respond(401, """{"detail":"expired"}""")
            assertTrue((repository().list(7) as AppResult.Failure).error is AppError.Unauthorized)
        }

    @Test
    fun `clear drops the cache`() =
        runTest(timeout = 30.seconds) {
            respond(200, body)
            val repo = repository()
            repo.list(7)
            repo.clear()
            respond(503, """{"detail":"down"}""")
            assertTrue(repo.list(7) is AppResult.Failure)
        }
}
