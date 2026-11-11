@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.rupakc.travelplanner.core.data.telemetry

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.datastore.StoredSession
import io.github.rupakc.travelplanner.core.datastore.TelemetryPrefsStore
import io.github.rupakc.travelplanner.core.domain.telemetry.QueuedEvent
import io.github.rupakc.travelplanner.core.domain.telemetry.SendOutcome
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetrySettings
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import io.github.rupakc.travelplanner.core.testing.fake.FakeClientConfigRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeConsentRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BASE_MS = 1_800_000_000_000L
private const val MIN = 60_000L

/** Records every batch; [outcomes] are consumed in order (then [SendOutcome.Sent]); [gate] holds a send open. */
private class FakeSender(private val scope: TestScope) : TelemetrySender {
    val batches = mutableListOf<List<QueuedEvent>>()
    val sessions = mutableListOf<String>()
    val times = mutableListOf<Long>()
    val outcomes = ArrayDeque<SendOutcome>()
    var gate: CompletableDeferred<Unit>? = null
    var throwNext = 0

    val events get() = batches.flatten()
    val names get() = events.map { it.event }

    override suspend fun send(
        sessionId: String,
        events: List<QueuedEvent>,
    ): SendOutcome {
        batches += events.toList()
        sessions += sessionId
        times += scope.testScheduler.currentTime
        gate?.await()
        if (throwNext > 0) {
            throwNext--
            error("network stack exploded")
        }
        return outcomes.removeFirstOrNull() ?: SendOutcome.Sent
    }
}

/** An append/claim store held in memory, recording calls. */
private class FakeStore : TelemetryQueueStore {
    val items = mutableListOf<QueuedEvent>()
    val appends = mutableListOf<List<QueuedEvent>>()
    var claims = 0
    var clears = 0

    override suspend fun append(events: List<QueuedEvent>) {
        appends += events.toList()
        items += events
    }

    override suspend fun claim(nowMs: Long): List<QueuedEvent> {
        claims++
        return items.toList().also { items.clear() }
    }

    override suspend fun clear() {
        clears++
        items.clear()
    }
}

private class FakeScheduler : TelemetryFlushScheduler {
    var scheduled = 0
    var cancelled = 0

    override fun schedule() {
        scheduled++
    }

    override fun cancel() {
        cancelled++
    }
}

private class FakePrefs : TelemetryPrefsStore {
    var stored: StoredSession? = null
    val writes = mutableListOf<StoredSession>()

    override suspend fun anonId(): String = "00000000-0000-4000-8000-000000000000"

    override val consent: Flow<ConsentChoice?> = MutableStateFlow(null)

    override suspend fun setConsent(choice: ConsentChoice) = Unit

    override suspend fun session(): StoredSession? = stored

    override suspend fun setSession(session: StoredSession) {
        stored = session
        writes += session
    }
}

private class VirtualClock(private val scope: TestScope) : AppClock {
    override fun now(): Instant = Instant.ofEpochMilli(BASE_MS + scope.testScheduler.currentTime)

    override fun zone(): ZoneId = ZoneId.of("UTC")
}

class TelemetryEngineTest {
    private class Rig(scope: TestScope, settings: TelemetrySettings?, allowed: Boolean) {
        val sender = FakeSender(scope)
        val store = FakeStore()
        val scheduler = FakeScheduler()
        val prefs = FakePrefs()
        val consent = FakeConsentRepository(initiallyAllowed = allowed)
        val config = FakeClientConfigRepository(settings)
        val engine =
            TelemetryEngine(
                config = config,
                consent = consent,
                prefs = prefs,
                sender = sender,
                store = store,
                scheduler = scheduler,
                clock = VirtualClock(scope),
                scope = scope.backgroundScope,
                confinement = StandardTestDispatcher(scope.testScheduler),
            )
    }

    private fun TestScope.rig(
        settings: TelemetrySettings? = TelemetrySettings(flushIntervalMs = 15_000, maxBatch = 50),
        allowed: Boolean = true,
        start: Boolean = true,
    ): Rig =
        Rig(this, settings, allowed).also {
            if (start) {
                it.engine.start()
                runCurrent()
            }
        }

    @Test
    fun `config off drops queued events and stops`() =
        runTest {
            val r = rig(settings = null, start = false)
            r.engine.emit("ui_tap", mapOf("id" to "early"))
            r.engine.start()
            runCurrent()
            r.engine.emit("ui_tap", mapOf("id" to "late"))
            r.engine.screenView("/search")
            r.engine.onBackground()
            r.engine.onForeground()
            advanceTimeBy(60 * MIN)
            runCurrent()

            assertTrue(r.sender.batches.isEmpty())
            assertTrue(r.store.appends.isEmpty())
            assertTrue(r.store.clears >= 1)
            assertEquals(0, r.scheduler.scheduled)
        }

    @Test
    fun `flushes at max_batch without waiting`() =
        runTest {
            val r = rig(settings = TelemetrySettings(flushIntervalMs = 15_000, maxBatch = 3))
            // session_start + app_open are queued by start(); one more event reaches max_batch.
            r.engine.emit("ui_tap", mapOf("id" to "a"))
            runCurrent()

            assertEquals(1, r.sender.batches.size)
            assertEquals(listOf("session_start", "app_open", "ui_tap"), r.sender.names)
            assertEquals(0L, r.sender.times.single())
        }

    @Test
    fun `flushes after flush_interval`() =
        runTest {
            val r = rig()
            r.engine.emit("ui_tap", mapOf("id" to "a"))
            runCurrent()
            advanceTimeBy(14_999)
            runCurrent()
            assertTrue(r.sender.batches.isEmpty())

            advanceTimeBy(2)
            runCurrent()
            assertEquals(listOf("session_start", "app_open", "ui_tap"), r.sender.names)
        }

    @Test
    fun `session_start precedes the first event and again after 30 min idle`() =
        runTest {
            val r = rig()
            advanceTimeBy(16_000)
            runCurrent()
            assertEquals("session_start", r.sender.names.first())
            val firstSession = r.sender.sessions.single()
            assertEquals(firstSession, r.prefs.stored?.id)

            advanceTimeBy(31 * MIN)
            r.engine.emit("ui_tap", mapOf("id" to "b"))
            advanceTimeBy(16_000)
            runCurrent()

            assertEquals(listOf("session_start", "ui_tap"), r.sender.batches[1].map { it.event })
            assertNotEquals(firstSession, r.sender.sessions[1])
            assertEquals(r.sender.sessions[1], r.prefs.stored?.id)
        }

    @Test
    fun `restored session continues without a session_start`() =
        runTest {
            val r = rig(start = false)
            r.prefs.stored = StoredSession("11111111-1111-4111-8111-111111111111", BASE_MS - MIN)
            r.engine.start()
            advanceTimeBy(16_000)
            runCurrent()

            assertEquals(listOf("app_open"), r.sender.names)
            assertEquals("11111111-1111-4111-8111-111111111111", r.sender.sessions.single())
        }

    @Test
    fun `screenView emits screen_time with ms and referrer`() =
        runTest {
            val r = rig()
            r.engine.screenView("/search")
            runCurrent()
            advanceTimeBy(5_000)
            r.engine.screenView("/results")
            r.engine.screenView("/results")
            runCurrent()
            assertEquals("/results", r.engine.currentRoute())
            advanceTimeBy(16_000)
            runCurrent()

            val events = r.sender.events
            val views = events.filter { it.event == "screen_view" }
            assertEquals(listOf(mapOf<String, Any>("route" to "/search"), mapOf("route" to "/results", "referrer_route" to "/search")), views.map { it.props })
            val time = events.single { it.event == "screen_time" }
            assertEquals(mapOf<String, Any>("route" to "/search", "ms" to 5_000L), time.props)
            assertEquals("/results", events.last().screen)
            assertEquals("/", events.first { it.event == "app_open" }.screen)
        }

    @Test
    fun `EU zone without a choice sends and stores nothing`() =
        runTest {
            val r = rig(allowed = false)
            r.engine.emit("ui_tap", mapOf("id" to "a"))
            r.engine.screenView("/search")
            r.engine.onBackground()
            r.engine.onForeground()
            advanceTimeBy(60 * MIN)
            runCurrent()

            assertTrue(r.sender.batches.isEmpty())
            assertTrue(r.store.appends.all { it.isEmpty() })
            assertTrue(r.prefs.writes.isEmpty())
            assertEquals(0, r.scheduler.scheduled)
        }

    @Test
    fun `grant emits consent granted then app_open`() =
        runTest {
            val r = rig(allowed = false)
            r.engine.emit("ui_tap", mapOf("id" to "before"))
            runCurrent()
            r.consent.choose(ConsentChoice.GRANTED)
            runCurrent()
            advanceTimeBy(16_000)
            runCurrent()

            assertEquals(listOf("session_start", "consent", "app_open"), r.sender.names)
            assertEquals(mapOf<String, Any>("choice" to "granted"), r.sender.events[1].props)
        }

    @Test
    fun `deny clears the queue and store and sends nothing, not even the choice`() =
        runTest {
            val r = rig()
            r.engine.emit("ui_tap", mapOf("id" to "a"))
            r.engine.onBackground()
            runCurrent()
            assertTrue(r.store.items.isNotEmpty())
            r.engine.onForeground()
            r.engine.emit("ui_tap", mapOf("id" to "b"))
            runCurrent()

            r.consent.choose(ConsentChoice.DENIED)
            runCurrent()
            r.engine.emit("ui_tap", mapOf("id" to "c"))
            advanceTimeBy(60 * MIN)
            runCurrent()

            assertTrue(r.sender.batches.isEmpty())
            assertTrue(r.store.items.isEmpty())
            assertTrue(r.store.clears >= 1)
            assertTrue(r.scheduler.cancelled >= 2)
            assertTrue(r.store.appends.flatten().none { it.event == "consent" })
        }

    @Test
    fun `429 Retry-After blocks sends until it passes`() =
        runTest {
            val r = rig(settings = TelemetrySettings(flushIntervalMs = 1_000, maxBatch = 50))
            r.sender.outcomes += SendOutcome.RetryAfter(30_000)
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(1, r.sender.batches.size)

            r.engine.emit("ui_tap", mapOf("id" to "a"))
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(1, r.sender.batches.size)

            advanceTimeBy(15_000)
            runCurrent()
            assertEquals(2, r.sender.batches.size)
            assertTrue(r.sender.times[1] - r.sender.times[0] >= 30_000)
            // The rejected events were kept and go out once, before the new one.
            assertEquals(listOf("session_start", "app_open", "ui_tap"), r.sender.batches[1].map { it.event })
        }

    @Test
    fun `three failures back off`() =
        runTest {
            val r = rig(settings = TelemetrySettings(flushIntervalMs = 1_000, maxBatch = 50))
            repeat(4) { r.sender.outcomes += SendOutcome.Retry }
            advanceTimeBy(30_000)
            runCurrent()

            // Failures 1 and 2 retry after the interval; the 3rd backs off 2x, the 4th 4x.
            assertEquals(listOf(1_000L, 2_000L, 3_000L, 5_000L, 9_000L), r.sender.times)
            assertEquals(5, r.sender.batches.size)
            assertEquals(1, r.sender.batches.last().count { it.event == "app_open" })
        }

    @Test
    fun `onBackground persists pending events and schedules the worker`() =
        runTest {
            val r = rig()
            r.engine.screenView("/search")
            r.engine.emit("ui_tap", mapOf("id" to "a"))
            runCurrent()
            advanceTimeBy(2_000)
            r.engine.onBackground()
            runCurrent()

            assertEquals(
                listOf("session_start", "app_open", "screen_view", "ui_tap", "screen_time", "app_background"),
                r.store.appends.flatten().map { it.event },
            )
            assertEquals(1, r.scheduler.scheduled)
            // The worker owns them now: the engine never sends them as well.
            advanceTimeBy(60 * MIN)
            runCurrent()
            assertTrue(r.sender.batches.isEmpty())
        }

    @Test
    fun `in-flight events are never persisted`() =
        runTest {
            val r = rig(settings = TelemetrySettings(flushIntervalMs = 15_000, maxBatch = 2), start = false)
            val gate = CompletableDeferred<Unit>()
            r.sender.gate = gate
            r.engine.start()
            runCurrent()
            assertEquals(listOf("session_start", "app_open"), r.sender.names)

            r.engine.emit("ui_tap", mapOf("id" to "a"))
            r.engine.onBackground()
            runCurrent()
            assertEquals(listOf("ui_tap", "app_background"), r.store.appends.flatten().map { it.event })

            gate.complete(Unit)
            runCurrent()
            assertEquals(1, r.store.appends.size)

            r.sender.gate = null
            r.engine.onForeground()
            advanceTimeBy(16_000)
            runCurrent()
            val sent = r.sender.events
            assertEquals(sent.size, sent.toSet().size)
            assertEquals(listOf("session_start", "app_open", "ui_tap", "app_background", "app_open"), r.sender.names)
        }

    @Test
    fun `onForeground cancels the worker and reclaims stored events`() =
        runTest {
            val r = rig()
            r.engine.emit("ui_tap", mapOf("id" to "a"))
            r.engine.onBackground()
            runCurrent()
            val stored = r.store.items.toList()
            assertEquals(4, stored.size)

            r.engine.onForeground()
            runCurrent()
            assertEquals(1, r.scheduler.cancelled)
            assertTrue(r.store.items.isEmpty())

            advanceTimeBy(16_000)
            runCurrent()
            assertEquals(stored, r.sender.events.take(4))
            assertEquals(listOf("session_start", "app_open", "ui_tap", "app_background", "app_open"), r.sender.names)
        }

    @Test
    fun `emit never throws when the sender throws`() =
        runTest {
            val r = rig()
            r.sender.throwNext = 1
            r.engine.emit("ui_tap", mapOf("id" to "a"))
            advanceTimeBy(16_000)
            runCurrent()
            assertEquals(1, r.sender.batches.size)

            r.engine.emit("ui_tap", mapOf("id" to "b"))
            r.engine.emit("bad event name!", mapOf("id" to "c"))
            advanceTimeBy(16_000)
            runCurrent()
            assertEquals(2, r.sender.batches.size)
            assertEquals(listOf("session_start", "app_open", "ui_tap", "ui_tap"), r.sender.batches[1].map { it.event })
        }

    @Test
    fun `free text props never reach the sender`() =
        runTest {
            val r = rig()
            r.engine.emit(
                "ui_tap",
                mapOf(
                    "id" to "search_button",
                    "query" to "hotels in Paris",
                    "email" to "someone@example.com",
                    "count" to 3,
                    "on" to true,
                    "missing" to null,
                    "list" to listOf(1, 2),
                ),
            )
            advanceTimeBy(16_000)
            runCurrent()

            val tap = r.sender.events.single { it.event == "ui_tap" }
            assertEquals(mapOf<String, Any>("id" to "search_button", "count" to 3L, "on" to true), tap.props)
        }
}
