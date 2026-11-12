package io.github.rupakc.travelplanner.core.data.offline

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.OutboxRequest
import io.github.rupakc.travelplanner.core.database.TravelDatabase
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class OutboxImplTest {
    private lateinit var db: TravelDatabase
    private val alice = Session("t", User("alice"), userId = "u1")
    private val bob = Session("t", User("bob"), userId = "u2")
    private val sessions = FakeSessionStore(alice)
    private val telemetry = FakeTelemetry()
    private val scheduler = RecordingScheduler()
    private val sent = Collections.synchronizedList(mutableListOf<String>())
    private var status: (String) -> Int = { 201 }
    private var failure: IOException? = null

    @Before fun setUp() {
        db = memoryDb()
    }

    @After fun tearDown() = db.close()

    private fun outbox() =
        OutboxImpl(
            db.outboxDao(),
            sessions,
            { _, path, _ ->
                delay(20)
                failure?.let { throw it }
                sent += path
                status(path)
            },
            scheduler,
            telemetry,
            fixedClock,
        )

    private fun req(key: String, path: String = "api/trips/1/expenses/$key") = OutboxRequest("POST", path, "{}", key)

    @Test
    fun `concurrent flushes send each item once`() =
        runBlocking {
            val outbox = outbox()
            repeat(5) { outbox.enqueue(req("k$it")) }
            val results = (1..4).map { async { outbox.flush() } }.awaitAll()
            assertEquals(5, sent.size)
            assertEquals(5, sent.toSet().size)
            assertEquals(5, results.sumOf { it.sent })
            assertEquals(0, db.outboxDao().count("u1"))
        }

    @Test
    fun `a repeated key replaces the earlier request`() =
        runBlocking {
            val outbox = outbox()
            outbox.enqueue(OutboxRequest("PUT", "api/trips/1/snapshot", "{\"a\":1}", "snapshot:1"))
            outbox.enqueue(OutboxRequest("PUT", "api/trips/1/snapshot", "{\"a\":2}", "snapshot:1"))
            assertEquals(1, db.outboxDao().count("u1"))
            assertEquals("{\"a\":2}", db.outboxDao().next("u1", 0)!!.body)
            assertEquals(setOf("snapshot:1"), outbox.pendingKeys().first())
            assertEquals(2, scheduler.scheduled)
        }

    @Test
    fun `2xx and 409 are sent, 4xx are dropped, 408 429 and 5xx are kept`() =
        runBlocking {
            val outbox = outbox()
            val codes = mapOf("a" to 200, "b" to 409, "c" to 422, "d" to 500, "e" to 404)
            status = { path -> codes.getValue(path.substringAfterLast('/')) }
            codes.keys.forEach { outbox.enqueue(req(it)) }
            val r = outbox.flush()
            assertEquals(2, r.sent)
            assertEquals(2, r.dropped)
            assertEquals(1, r.failed)
            assertEquals(1, db.outboxDao().count("u1"))
            assertEquals(
                listOf("outbox_flush" to mapOf<String, Any?>("sent" to 2, "failed" to 1, "dropped" to 2)),
                telemetry.events,
            )
            for (code in listOf(408, 429)) {
                status = { code }
                val again = outbox.flush()
                assertEquals(0, again.sent + again.dropped)
                assertEquals(1, db.outboxDao().count("u1"))
            }
            status = { 201 }
            assertEquals(1, outbox.flush().sent)
            assertTrue(scheduler.cancelled > 0)
        }

    @Test
    fun `a network failure keeps everything and stops`() =
        runBlocking {
            val outbox = outbox()
            outbox.enqueue(req("a"))
            outbox.enqueue(req("b"))
            failure = IOException("offline")
            val r = outbox.flush()
            assertEquals(1, r.failed)
            assertEquals(2, db.outboxDao().count("u1"))
            failure = null
            assertEquals(2, outbox.flush().sent)
        }

    @Test
    fun `users are isolated and a purge removes everything`() =
        runBlocking {
            val outbox = outbox()
            outbox.enqueue(req("mine"))
            sessions.state.value = bob
            outbox.enqueue(req("bobs"))
            assertEquals(setOf("bobs"), outbox.pendingKeys().first())
            assertEquals(0, outbox.flush("u1").sent)
            assertEquals(1, outbox.flush().sent)
            assertEquals(listOf("api/trips/1/expenses/bobs"), sent.toList())
            assertEquals(1, db.outboxDao().count("u1"))
            OfflinePurger(db.offlinePackDao(), db.outboxDao(), scheduler).clear()
            assertEquals(0, db.outboxDao().countAll())
        }

    @Test
    fun `signed out enqueue fails and flush does nothing`() =
        runBlocking {
            sessions.state.value = null
            val outbox = outbox()
            assertTrue(outbox.enqueue(req("a")) is AppResult.Failure)
            assertEquals(0, outbox.flush().sent)
        }
}
