package io.github.rupakc.travelplanner.core.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.TripsRepositoryImpl
import io.github.rupakc.travelplanner.core.data.offline.memoryDb
import io.github.rupakc.travelplanner.core.database.TravelDatabase
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.trips.CreateTripRequest
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameEvents
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Trips list: per-user Room cache (survives a new repository, i.e. a cold start), offline fallback with the phase recomputed for the device date. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class TripsRepositoryImplTest {
    private val server = MockWebServer()
    private lateinit var db: TravelDatabase
    private val sessions = FakeSessionStore(Session("token", TestUser))

    @Before fun setUp() {
        server.start()
        db = memoryDb()
    }

    @After fun tearDown() {
        server.close()
        db.close()
    }

    private val gameEvents = FakeGameEvents()

    private fun repository(): TripsRepositoryImpl {
        val net = TestNetwork(server.url("/").toString(), sessions, TestAppDispatchers(Dispatchers.IO))
        val clock =
            object : AppClock {
                override fun now(): Instant = Instant.parse("2026-12-20T10:00:00Z")

                override fun zone() = ZoneOffset.UTC
            }
        return TripsRepositoryImpl(net.api(), sessions, clock, gameEvents, db.tripCacheDao())
    }

    private fun ok(body: String) = server.enqueue(MockResponse.Builder().code(200).body(body).build())

    private val planning =
        """{"trips":[{"id":1,"title":"Lisbon","phase":"planning","phase_source":"auto","start_date":"2027-01-05","end_date":"2027-01-10",""" +
            """"days_to_start":60,"prep":{"done":1,"total":9,"percent":11,"complete":false}}],"pinned_id":1,"today":"2026-11-01"}"""

    @Test
    fun `list sends the device date and a failure falls back to the cached list with the phase recomputed`() =
        runTest(timeout = 30.seconds) {
            val repo = repository()
            ok(planning)
            val live = (repo.list("2026-11-01") as AppResult.Success).value
            assertEquals(Phase.PLANNING, live.trips.single().phase)
            assertEquals("/api/trips?today=2026-11-01", server.takeRequest().target)
            assertFalse(repo.isShowingCachedList())

            server.enqueue(MockResponse.Builder().code(503).body("""{"detail":"down"}""").build())
            val offline = (repo.list("2026-12-25") as AppResult.Success).value
            assertTrue(repo.isShowingCachedList())
            assertEquals(Phase.READY, offline.trips.single().phase)
            assertEquals(11, offline.trips.single().daysToStart)
            assertEquals(1L, offline.pinnedId)
        }

    @Test
    fun `a client error is not hidden by the cache`() =
        runTest(timeout = 30.seconds) {
            val repo = repository()
            ok(planning)
            repo.list("2026-11-01")
            server.enqueue(MockResponse.Builder().code(401).body("""{"detail":"expired"}""").build())
            assertTrue((repo.list("2026-11-02") as AppResult.Failure).error is AppError.Unauthorized)
            assertFalse(repo.isShowingCachedList())
        }

    @Test
    fun `without a cache a failure stays a failure`() =
        runTest(timeout = 30.seconds) {
            server.enqueue(MockResponse.Builder().code(503).body("""{"detail":"down"}""").build())
            assertTrue(repository().list("2026-11-01") is AppResult.Failure)
        }

    @Test
    fun `the cache is per user and cleared on sign-out`() =
        runTest(timeout = 30.seconds) {
            val repo = repository()
            ok(planning)
            repo.list("2026-11-01")
            sessions.state.value = Session("token2", TestUser.copy(username = "bob"))
            server.enqueue(MockResponse.Builder().code(503).body("""{"detail":"down"}""").build())
            assertTrue(repo.list("2026-11-02") is AppResult.Failure)

            sessions.state.value = Session("token", TestUser)
            repo.clear()
            server.enqueue(MockResponse.Builder().code(503).body("""{"detail":"down"}""").build())
            assertTrue(repo.list("2026-11-02") is AppResult.Failure)
        }

    @Test
    fun `create and delete keep the cached list in step`() =
        runTest(timeout = 30.seconds) {
            val repo = repository()
            ok(planning)
            repo.list("2026-11-01")
            ok("""{"id":2,"title":"Idea","phase":"dreaming","phase_source":"auto"}""")
            assertTrue(repo.create(CreateTripRequest("Idea")) is AppResult.Success)
            server.enqueue(MockResponse.Builder().code(204).build())
            assertTrue(repo.delete(1) is AppResult.Success)
            server.enqueue(MockResponse.Builder().code(503).body("""{"detail":"down"}""").build())
            val offline = (repo.list("2026-12-25") as AppResult.Success).value
            assertEquals(listOf(2L), offline.trips.map { it.id })
        }

    @Test
    fun `checkin posts the local day and forwards the game delta`() =
        runTest(timeout = 30.seconds) {
            val repo = repository()
            ok(
                """{"day":"2027-01-06","day_index":2,"city":"Lisbon","country_code":"PT","new_country":true,""" +
                    """"streak":{"current":2,"longest":2,"last_day":"2027-01-06","freezes":1},""" +
                    """"game":{"awards":[{"rule":"trip_checkin","points":10}],"badges":[]}}""",
            )
            val r = (repo.checkin(1, "2027-01-06") as AppResult.Success).value
            val req = server.takeRequest()
            assertEquals("/api/trips/1/checkin", req.target)
            assertEquals("""{"day":"2027-01-06"}""", req.body?.utf8())
            assertTrue(r.newCountry)
            assertEquals(1, gameEvents.published.size)
        }

    @Test
    fun `a cold start with no network serves the list saved by the previous process`() =
        runTest(timeout = 30.seconds) {
            ok(planning)
            repository().list("2026-11-01")

            server.enqueue(MockResponse.Builder().code(503).body("""{"detail":"down"}""").build())
            val fresh = repository()
            val offline = (fresh.list("2026-12-25") as AppResult.Success).value
            assertTrue(fresh.isShowingCachedList())
            assertEquals(Phase.READY, offline.trips.single().phase)

            server.enqueue(MockResponse.Builder().code(503).body("""{"detail":"down"}""").build())
            assertEquals(1L, (repository().get(1, "2026-12-25") as AppResult.Success).value.id)
        }
}
