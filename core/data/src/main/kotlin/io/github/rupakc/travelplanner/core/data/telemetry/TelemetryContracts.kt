package io.github.rupakc.travelplanner.core.data.telemetry

import io.github.rupakc.travelplanner.core.domain.telemetry.QueuedEvent
import io.github.rupakc.travelplanner.core.domain.telemetry.SendOutcome
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetrySettings
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Fire-and-forget usage events. Never blocks, never throws, does no I/O on the caller's thread. */
interface Telemetry {
    /** Records [event] with [props]; dropped silently when telemetry is off or consent is missing. */
    fun emit(
        event: String,
        props: Map<String, Any?> = emptyMap(),
    )

    /** Records a screen view for [routeTemplate] (never a concrete id) and makes it the current route. */
    fun screenView(routeTemplate: String)

    /** The route template of the current screen. */
    fun currentRoute(): String
}

/** Process and app lifecycle hooks for telemetry. */
interface TelemetryLifecycle {
    /** Called once at process start. */
    fun start()

    /** The app came to the foreground. */
    fun onForeground()

    /** The app went to the background: persist pending events and schedule the worker. */
    fun onBackground()
}

/** What the consent UI should show. */
sealed interface ConsentPromptState {
    /** Nothing is shown. */
    data object Hidden : ConsentPromptState

    /** The first-run prompt (consent required, no choice stored). */
    data object FirstRun : ConsentPromptState

    /** The choices sheet reopened from Settings, showing the current state [allowed]. */
    data class Reopened(val allowed: Boolean) : ConsentPromptState
}

/** The user's telemetry consent. */
interface ConsentRepository {
    /** Stored choice, else !ConsentRules.required(zone at collection time). */
    val allowed: Flow<Boolean>

    /** The current value of [allowed]. */
    suspend fun allowedNow(): Boolean

    /** Explicit choices only, not replayed. */
    val choices: Flow<ConsentChoice>

    /** Initially [ConsentPromptState.Hidden]. */
    val prompt: StateFlow<ConsentPromptState>

    /** Stores [choice]; a denial clears the queue and sends nothing. */
    suspend fun choose(choice: ConsentChoice)

    /** Shows the choices sheet. */
    fun openChoices()

    /** Hides the choices sheet. */
    fun closeChoices()
}

/** Server-driven telemetry configuration. */
interface ClientConfigRepository {
    /** null = telemetry off for this server (404, error, disabled). */
    suspend fun telemetrySettings(): TelemetrySettings?
}

/** Sends one session's events to the server. */
internal interface TelemetrySender {
    /** Posts [events] for [sessionId] and reports the outcome. */
    suspend fun send(
        sessionId: String,
        events: List<QueuedEvent>,
    ): SendOutcome
}

/** Durable queue for events pending delivery. */
internal interface TelemetryQueueStore {
    /** Adds [events] to the stored queue. */
    suspend fun append(events: List<QueuedEvent>)

    /** Removes and returns the stored events that are still fresh at [nowMs]. */
    suspend fun claim(nowMs: Long): List<QueuedEvent>

    /** Drops everything stored. */
    suspend fun clear()
}

/** Schedules background delivery of the stored queue. */
internal interface TelemetryFlushScheduler {
    /** Enqueues a delivery run. */
    fun schedule()

    /** Cancels pending runs. */
    fun cancel()
}
