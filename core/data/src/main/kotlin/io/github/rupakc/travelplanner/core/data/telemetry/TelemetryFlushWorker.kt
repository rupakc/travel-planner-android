package io.github.rupakc.travelplanner.core.data.telemetry

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.domain.telemetry.QueuedEvent
import io.github.rupakc.travelplanner.core.domain.telemetry.SendOutcome
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Delivers the events left on disk (the app went to the background, or the process was killed).
 * `claim` removes them from disk first, so nothing is sent twice by a later run; what could not be
 * sent goes back with `append`.
 */
@HiltWorker
internal class TelemetryFlushWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val store: TelemetryQueueStore,
        private val sender: TelemetrySender,
        private val consent: ConsentRepository,
        private val config: ClientConfigRepository,
        private val clock: AppClock,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val settings = config.telemetrySettings()
            if (!consent.allowedNow() || settings == null) {
                store.clear()
                return Result.success()
            }
            val rest = ArrayDeque(store.claim(clock.now().toEpochMilli()))
            // The batch handed to the sender whose outcome is unknown if this run is interrupted.
            var inFlight: List<QueuedEvent> = emptyList()
            try {
                while (rest.isNotEmpty()) {
                    val sessionId = rest.first().sessionId
                    val batch = rest.filter { it.sessionId == sessionId }.take(settings.maxBatch)
                    inFlight = batch
                    val outcome = sender.send(sessionId, batch)
                    inFlight = emptyList()
                    when (outcome) {
                        SendOutcome.Sent, SendOutcome.Dropped -> removeBatch(rest, batch)
                        SendOutcome.Disabled -> {
                            store.clear()
                            return Result.success()
                        }
                        is SendOutcome.Retry, is SendOutcome.RetryAfter -> {
                            store.append(rest.toList())
                            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
                        }
                    }
                }
            } catch (t: Throwable) {
                // Cancelled (the app came to the foreground, the network went away) or failed: claim already
                // removed everything from disk, so put back what was not sent. The in-flight batch may or may
                // not have arrived; it is dropped (at most once) rather than risk sending it twice.
                withContext(NonCancellable) {
                    val gone = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<QueuedEvent, Boolean>())
                    gone.addAll(inFlight)
                    runCatching { store.append(rest.filter { it !in gone }) }
                }
                throw t
            }
            return Result.success()
        }

        private fun removeBatch(
            rest: ArrayDeque<QueuedEvent>,
            batch: List<QueuedEvent>,
        ) {
            val gone = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<QueuedEvent, Boolean>())
            gone.addAll(batch)
            rest.removeAll { it in gone }
        }

        private companion object {
            const val MAX_ATTEMPTS = 3
        }
    }
