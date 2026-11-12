package io.github.rupakc.travelplanner.core.data.offline

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.database.OutboxEntity
import io.github.rupakc.travelplanner.core.database.TravelDatabase
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.api.TripsApi
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class OfflinePackRepositoryImplTest {
    private val server = MockWebServer()
    private lateinit var db: TravelDatabase
    private val alice = Session("t", User("alice"), userId = "u1")
    private val sessions = FakeSessionStore(alice)
    private val telemetry = FakeTelemetry()

    @Before fun setUp() {
        server.start()
        db = memoryDb()
    }

    @After fun tearDown() {
        server.close()
        db.close()
    }

    private fun repo(): OfflinePackRepositoryImpl {
        val net = TestNetwork(server.url("/").toString(), sessions, TestAppDispatchers(Dispatchers.IO))
        return OfflinePackRepositoryImpl(net.api<TripsApi>(), db.offlinePackDao(), db.outboxDao(), sessions, telemetry, fixedClock)
    }

    private val body =
        """{"version":1,"trip_id":12,"title":"Lisbon","emergency":{"source":"static","as_of":null,"cities":[]},"checkins":["2027-04-01"]}"""

    @Test
    fun `a saved pack is read back with the network off and only for its user`() =
        runBlocking {
            val repo = repo()
            server.enqueue(MockResponse.Builder().code(200).body(body).build())
            val saved = repo.save(12, "2027-04-01")
            assertTrue(saved is AppResult.Success)
            assertEquals("/api/trips/12/offline-pack?today=2027-04-01", server.takeRequest().target)
            server.close()
            assertEquals("Lisbon", repo.saved(12).first()!!.title)
            assertNull(repo.saved(13).first())
            sessions.state.value = Session("t", User("bob"), userId = "u2")
            assertNull(repo.saved(12).first())
            assertEquals(
                mapOf<String, Any?>("action" to "save", "result" to "ok", "kb" to 0),
                telemetry.events.single().second,
            )
        }

    @Test
    fun `a second save is a refresh`() =
        runBlocking {
            val repo = repo()
            repeat(2) { server.enqueue(MockResponse.Builder().code(200).body(body).build()) }
            repo.save(12, "2027-04-01")
            repo.save(12, "2027-04-01")
            assertEquals(listOf("save", "refresh"), telemetry.events.map { it.second["action"] })
        }

    @Test
    fun `404 offline_pack_disabled purges packs and the outbox`() =
        runBlocking {
            val repo = repo()
            server.enqueue(MockResponse.Builder().code(200).body(body).build())
            repo.save(12, "2027-04-01")
            db.outboxDao().enqueue(OutboxEntity(userId = "u1", method = "POST", path = "p", body = null, key = "k", createdAt = 1))
            server.enqueue(
                MockResponse.Builder().code(404).body("""{"detail":{"code":"offline_pack_disabled","message":"off"}}""").build(),
            )
            val result = repo.save(12, "2027-04-01")
            assertTrue((result as AppResult.Failure).error is AppError.NotFound)
            assertNull(repo.saved(12).first())
            assertEquals(0, db.outboxDao().countAll())
            assertEquals("unsupported", telemetry.events.last().second["result"])
        }

    @Test
    fun `a network failure keeps the old pack and reports an error`() =
        runBlocking {
            val repo = repo()
            server.enqueue(MockResponse.Builder().code(200).body(body).build())
            repo.save(12, "2027-04-01")
            server.enqueue(MockResponse.Builder().code(503).body("""{"detail":"down"}""").build())
            assertTrue(repo.save(12, "2027-04-01") is AppResult.Failure)
            assertNotNull(repo.saved(12).first())
            assertEquals("error", telemetry.events.last().second["result"])
        }

    @Test
    fun `remove deletes only that pack`() =
        runBlocking {
            val repo = repo()
            repeat(2) { server.enqueue(MockResponse.Builder().code(200).body(body).build()) }
            repo.save(12, "2027-04-01")
            repo.save(13, "2027-04-01")
            repo.remove(12)
            assertNull(repo.saved(12).first())
            assertNotNull(repo.saved(13).first())
        }
}
