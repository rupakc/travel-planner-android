package io.github.rupakc.travelplanner.core.data.telemetry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.rupakc.travelplanner.core.domain.telemetry.SendOutcome
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetrySettings
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakeClientConfigRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeConsentRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class TelemetryFlushWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val consent = FakeConsentRepository(initiallyAllowed = true)
    private val config = FakeClientConfigRepository(TelemetrySettings(15_000, 2))
    private val sender = RecordingSender()
    private lateinit var store: TelemetryQueueFile

    @Before fun setUp() {
        File(context.noBackupFilesDir, "telemetry").deleteRecursively()
        store = TelemetryQueueFile(context, TestAppDispatchers(Dispatchers.IO))
    }

    private fun worker(
        attempt: Int = 0,
        using: TelemetrySender = sender,
    ): TelemetryFlushWorker =
        TestListenableWorkerBuilder<TelemetryFlushWorker>(context)
            .setRunAttemptCount(attempt)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = TelemetryFlushWorker(appContext, workerParameters, store, using, consent, config, FixedClock())
                },
            ).build()

    @Test
    fun `sends and succeeds`() =
        runBlocking {
            store.append(listOf(event(sid = "a"), event(sid = "a"), event(sid = "a"), event(sid = "b")))
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            // One session per batch, at most maxBatch (2) each.
            assertEquals(listOf("a" to 2, "a" to 1, "b" to 1), sender.sent.map { it.first to it.second.size })
            assertTrue(store.claim(NOW_MS).isEmpty())
        }

    @Test
    fun `retry keeps the rest on disk`() =
        runBlocking {
            store.append(listOf(event(sid = "a", ts = NOW_MS - 3), event(sid = "a", ts = NOW_MS - 2), event(sid = "b", ts = NOW_MS - 1)))
            val outcomes = ArrayDeque(listOf(SendOutcome.Sent, SendOutcome.Retry))
            sender.outcome = { outcomes.removeFirst() }
            // First batch (a,a) sent; then b fails and stays.
            assertEquals(ListenableWorker.Result.retry(), worker(attempt = 0).doWork())
            assertEquals(listOf("b"), store.claim(NOW_MS).map { it.sessionId })
        }

    @Test
    fun `after the third attempt it succeeds but the events stay`() =
        runBlocking {
            store.append(listOf(event()))
            sender.outcome = { SendOutcome.RetryAfter(30_000) }
            assertEquals(ListenableWorker.Result.success(), worker(attempt = 3).doWork())
            assertEquals(1, store.claim(NOW_MS).size)
        }

    @Test
    fun `not allowed clears and succeeds`() =
        runBlocking {
            store.append(listOf(event()))
            consent.allowedValue = false
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertTrue(sender.sent.isEmpty())
            assertTrue(store.claim(NOW_MS).isEmpty())
        }

    @Test
    fun `config off or failed lookup succeeds and keeps the queue`() =
        runBlocking {
            store.append(listOf(event()))
            config.settings = null
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertTrue(sender.sent.isEmpty())
            assertEquals(1, store.claim(NOW_MS).size)
        }

    @Test
    fun `disabled clears the rest`() =
        runBlocking {
            store.append(listOf(event(sid = "a"), event(sid = "b")))
            sender.outcome = { SendOutcome.Disabled }
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertTrue(store.claim(NOW_MS).isEmpty())
        }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    @Test
    fun `cancellation puts the unsent events back and drops only the in-flight batch`() =
        runBlocking {
            store.append(listOf(event(sid = "a", ts = NOW_MS - 3), event(sid = "a", ts = NOW_MS - 2), event(sid = "b", ts = NOW_MS - 1)))
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val hanging =
                object : TelemetrySender {
                    override suspend fun send(
                        sessionId: String,
                        events: List<io.github.rupakc.travelplanner.core.domain.telemetry.QueuedEvent>,
                    ): SendOutcome {
                        started.complete(Unit)
                        kotlinx.coroutines.awaitCancellation()
                    }
                }
            val w = worker(using = hanging)
            val job = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) { w.doWork() }
            started.await()
            job.cancel()
            job.join()
            // The batch [a, a] was in flight (dropped, at most once); b never reached the sender and is back on disk.
            assertEquals(listOf("b"), store.claim(NOW_MS).map { it.sessionId })
        }
}
