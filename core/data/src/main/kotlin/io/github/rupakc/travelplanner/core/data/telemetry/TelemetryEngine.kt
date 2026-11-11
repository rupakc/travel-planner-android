package io.github.rupakc.travelplanner.core.data.telemetry

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.di.TelemetryConfinement
import io.github.rupakc.travelplanner.core.datastore.StoredSession
import io.github.rupakc.travelplanner.core.datastore.TelemetryPrefsStore
import io.github.rupakc.travelplanner.core.domain.telemetry.Backoff
import io.github.rupakc.travelplanner.core.domain.telemetry.QueuedEvent
import io.github.rupakc.travelplanner.core.domain.telemetry.SendOutcome
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryProps
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryQueue
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetrySession
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetrySettings
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Background usage telemetry (web `frontend/src/telemetry/client.js`).
 *
 * Callers only `trySend` a command; one consumer on [confinement] (a single-threaded `Dispatchers.IO`
 * view) owns every piece of state, so nothing here needs a lock and nothing blocks the caller. The flush
 * timer and the drain run as separate coroutines on the same confinement, so a send in flight never
 * holds up lifecycle commands.
 *
 * Ownership rule (the store appends, it never overwrites): an event lives either in memory or in the
 * [store], never both. Writing events to the store hands them over (they leave memory), and claiming takes
 * them back. In-flight events are never written. That way a process death loses at most an in-flight batch
 * and nothing is ever sent twice (by the engine and the worker, or by the engine itself).
 */
@Singleton
internal class TelemetryEngine
    @Inject
    constructor(
        private val config: ClientConfigRepository,
        private val consent: ConsentRepository,
        private val prefs: TelemetryPrefsStore,
        private val sender: TelemetrySender,
        private val store: TelemetryQueueStore,
        private val scheduler: TelemetryFlushScheduler,
        private val clock: AppClock,
        @param:ApplicationScope private val scope: CoroutineScope,
        @param:TelemetryConfinement private val confinement: CoroutineDispatcher,
    ) : Telemetry, TelemetryLifecycle {
        private sealed interface Command {
            class Emit(val event: String, val props: Map<String, Any?>, val ts: Long) : Command

            class ScreenView(val route: String, val ts: Long) : Command

            class Consent(val choice: ConsentChoice) : Command

            data object Foreground : Command

            data object Background : Command
        }

        private enum class State { PENDING, ON, OFF }

        private val commands = Channel<Command>(capacity = COMMAND_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        private val started = AtomicBoolean(false)

        @Volatile private var off = false

        @Volatile private var lastRoute: String? = null

        // ── confined state (touched only on [confinement]) ──
        private var state = State.PENDING
        private var settings = TelemetrySettings(DEFAULT_INTERVAL_MS, DEFAULT_BATCH)
        private val queue = TelemetryQueue()
        private val inFlight: MutableSet<QueuedEvent> = Collections.newSetFromMap(IdentityHashMap())
        private val session = TelemetrySession { UUID.randomUUID().toString() }
        private val backoff = Backoff()
        private var screenRoute: String? = null
        private var screenSince = 0L
        private var active = false
        private var foreground = true
        private var wasBackgrounded = false
        private var handedOver = false
        private var sending = false
        private var epoch = 0
        private var timer: Job? = null
        private var timerDue = 0L

        override fun emit(
            event: String,
            props: Map<String, Any?>,
        ) {
            if (off) return
            try {
                commands.trySend(Command.Emit(event, props.toMap(), nowMs()))
            } catch (e: Exception) {
                AppLogger.w(TAG, "emit dropped", e)
            }
        }

        override fun screenView(routeTemplate: String) {
            try {
                if (off) {
                    lastRoute = routeTemplate
                    return
                }
                commands.trySend(Command.ScreenView(routeTemplate, nowMs()))
            } catch (e: Exception) {
                AppLogger.w(TAG, "screen view dropped", e)
            }
        }

        override fun currentRoute(): String = lastRoute ?: ROOT

        override fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch(confinement) {
                safely { resolveConfig() }
                for (command in commands) safely { handle(command) }
            }
        }

        override fun onForeground() {
            if (!off) commands.trySend(Command.Foreground)
        }

        override fun onBackground() {
            if (!off) commands.trySend(Command.Background)
        }

        // ── consumer ──

        private suspend fun resolveConfig() {
            val resolved =
                try {
                    config.telemetrySettings()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "config failed", e)
                    null
                }
            if (resolved == null) {
                turnOff()
                return
            }
            settings = resolved
            state = State.ON
            prefs.session()?.let { session.restore(it.id, it.lastMs) }
            scope.launch(confinement) {
                consent.choices.collect { commands.trySend(Command.Consent(it)) }
            }
            if (consent.allowedNow()) activate()
        }

        private suspend fun handle(command: Command) {
            // The route is tracked in every state (currentRoute, screen_time); events only when on.
            if (command is Command.ScreenView) return onScreenView(command)
            if (state != State.ON) return
            when (command) {
                // `consent` comes only from [ConsentRepository.choices], never from a caller.
                is Command.Emit -> if (command.event != TelemetryEvents.CONSENT) enqueue(command.event, command.props, command.ts)

                is Command.Consent -> onConsent(command.choice)

                Command.Foreground -> onForegroundCommand()

                Command.Background -> onBackgroundCommand()

                is Command.ScreenView -> Unit
            }
        }

        private suspend fun onScreenView(command: Command.ScreenView) {
            val previous = screenRoute
            if (previous == command.route) return
            screenRoute = command.route
            lastRoute = command.route
            val since = screenSince
            screenSince = command.ts
            if (state != State.ON) return
            if (previous != null) {
                enqueue(TelemetryEvents.SCREEN_TIME, mapOf("route" to previous, "ms" to (command.ts - since).coerceAtLeast(0L)), command.ts)
            }
            val props = if (previous != null) mapOf("route" to command.route, "referrer_route" to previous) else mapOf("route" to command.route)
            enqueue(TelemetryEvents.SCREEN_VIEW, props, command.ts)
        }

        private suspend fun onConsent(choice: ConsentChoice) {
            when (choice) {
                ConsentChoice.GRANTED -> {
                    enqueue(TelemetryEvents.CONSENT, mapOf("choice" to choice.wire), nowMs())
                    activate()
                }

                ConsentChoice.DENIED -> {
                    dropEverything()
                    active = false
                    scheduler.cancel()
                    store.clear()
                }
            }
        }

        private suspend fun activate() {
            if (active) return
            active = true
            reclaim()
            enqueue(TelemetryEvents.APP_OPEN, emptyMap(), nowMs())
        }

        private suspend fun onForegroundCommand() {
            foreground = true
            screenSince = nowMs()
            if (!active) return
            scheduler.cancel()
            reclaim()
            // Cold start already sent app_open on activation; this is a return from the background.
            if (wasBackgrounded) enqueue(TelemetryEvents.APP_OPEN, emptyMap(), nowMs())
            scheduleFlush()
        }

        private suspend fun onBackgroundCommand() {
            foreground = false
            if (!active) return
            wasBackgrounded = true
            val now = nowMs()
            screenRoute?.let { enqueue(TelemetryEvents.SCREEN_TIME, mapOf("route" to it, "ms" to (now - screenSince).coerceAtLeast(0L)), now) }
            enqueue(TelemetryEvents.APP_BACKGROUND, emptyMap(), now)
            handOver()
            scheduler.schedule()
        }

        private suspend fun enqueue(
            event: String,
            props: Map<String, Any?>,
            ts: Long,
        ) {
            if (!TelemetryProps.isToken(event) || !consent.allowedNow()) return
            val touch = session.touch(ts)
            val screen = screenRoute ?: ROOT
            if (touch.isNew) queue.push(QueuedEvent(TelemetryEvents.SESSION_START, ts, emptyMap(), screen, touch.id))
            queue.push(QueuedEvent(event, ts, TelemetryProps.clean(props), screen, touch.id))
            scheduleFlush()
            if (touch.persist) prefs.setSession(StoredSession(touch.id, ts))
        }

        // ── store hand-over ──

        /** Moves the pending (not in-flight) events from memory to the store. */
        private suspend fun handOver() {
            val pending = queue.pending(inFlight)
            if (pending.isEmpty()) return
            queue.remove(pending)
            if (foreground) handedOver = true
            store.append(pending)
        }

        private suspend fun reclaim() {
            handedOver = false
            val now = nowMs()
            val saved = store.claim(now)
            queue.prepend(saved, now)
        }

        // ── sending ──

        private fun scheduleFlush() {
            if (state != State.ON) return
            val waiting = queue.pending(inFlight).size
            if (waiting == 0 && !handedOver) return
            val wait = (backoff.blockedUntil - nowMs()).coerceAtLeast(0L)
            schedule(if (waiting >= settings.maxBatch) wait else maxOf(wait, settings.flushIntervalMs))
        }

        private fun schedule(delayMs: Long) {
            val due = nowMs() + delayMs
            if (timer?.isActive == true && timerDue <= due) return
            timer?.cancel()
            timerDue = due
            timer =
                scope.launch(confinement) {
                    delay(delayMs)
                    timer = null
                    safely { drain() }
                }
        }

        private suspend fun drain() {
            if (sending || state != State.ON) return
            sending = true
            try {
                if (handedOver && foreground) reclaim()
                while (state == State.ON && nowMs() >= backoff.blockedUntil) {
                    val batch = queue.takeBatch(settings.maxBatch, inFlight)
                    if (batch.isEmpty() || !sendOne(batch)) break
                }
            } finally {
                sending = false
                scheduleFlush()
            }
        }

        /** Sends [batch]; true to keep draining. */
        private suspend fun sendOne(batch: List<QueuedEvent>): Boolean {
            inFlight.addAll(batch)
            val sentEpoch = epoch
            val outcome =
                try {
                    sender.send(batch.first().sessionId, batch)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "send failed", e)
                    SendOutcome.Retry
                } finally {
                    batch.forEach(inFlight::remove)
                }
            // Denied or switched off while the request ran: the batch is already gone.
            if (sentEpoch != epoch || state != State.ON) return false
            return when (outcome) {
                SendOutcome.Sent, SendOutcome.Dropped -> {
                    queue.remove(batch)
                    backoff.onSuccess()
                    true
                }

                SendOutcome.Retry -> {
                    onFailure()
                    false
                }

                is SendOutcome.RetryAfter -> {
                    backoff.block(nowMs() + outcome.ms)
                    onFailure()
                    false
                }

                SendOutcome.Disabled -> {
                    turnOff()
                    false
                }
            }
        }

        private suspend fun onFailure() {
            backoff.onFailure(settings.flushIntervalMs, nowMs())
            handOver()
            if (!foreground) scheduler.schedule()
        }

        // ── off / clear ──

        private fun dropEverything() {
            epoch++
            queue.clear()
            inFlight.clear()
            handedOver = false
            timer?.cancel()
            timer = null
        }

        private suspend fun turnOff() {
            off = true
            state = State.OFF
            active = false
            dropEverything()
            scheduler.cancel()
            store.clear()
        }

        private fun nowMs(): Long = clock.now().toEpochMilli()

        private inline fun safely(block: () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "telemetry command failed", e)
            }
        }

        private companion object {
            const val TAG = "Telemetry"
            const val ROOT = "/"
            const val COMMAND_CAPACITY = 1000
            const val DEFAULT_INTERVAL_MS = 15_000L
            const val DEFAULT_BATCH = 50
        }
    }
