package io.github.rupakc.travelplanner.core.domain.telemetry

/** One queued event. A plain class: identity equality, like the web's queue entries (two identical taps are two events). */
class QueuedEvent(
    val event: String,
    val ts: Long,
    val props: Map<String, Any>,
    val screen: String,
    val sessionId: String,
)

/** What happened to a send attempt, and what the flusher should do next. */
sealed interface SendOutcome {
    /** Accepted by the server. */
    data object Sent : SendOutcome

    /** Rejected for good (4xx other than 408/429): discard the batch. */
    data object Dropped : SendOutcome

    /** Transient failure: keep the batch and back off. */
    data object Retry : SendOutcome

    /** Transient failure with a server-specified wait of [ms] milliseconds. */
    data class RetryAfter(val ms: Long) : SendOutcome

    /** Telemetry is off for this server (404): stop sending. */
    data object Disabled : SendOutcome
}

/** Flush cadence from the server config, already clamped. */
data class TelemetrySettings(val flushIntervalMs: Long, val maxBatch: Int) {
    companion object
}

/** Event names; the only ones the server's registry accepts. */
object TelemetryEvents {
    const val APP_OPEN = "app_open"
    const val APP_BACKGROUND = "app_background"
    const val SESSION_START = "session_start"
    const val SCREEN_VIEW = "screen_view"
    const val SCREEN_TIME = "screen_time"
    const val UI_TAP = "ui_tap"
    const val UI_TOGGLE = "ui_toggle"
    const val THEME_CHANGE = "theme_change"
    const val LANGUAGE_CHANGE = "language_change"
    const val CONSENT = "consent"
    const val PERF = "perf"
}
