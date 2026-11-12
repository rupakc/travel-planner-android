package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.alerts.AlertsErrorCode
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchResponse
import io.github.rupakc.travelplanner.core.model.alerts.alertsErrorCode

/** What a one-tap watch ended as (contract: `POST /alerts/quick-watch`). The server's [detail] is shown as the fallback copy. */
sealed interface QuickWatchOutcome {
    /** A new, pending watch (first check queued, no price yet). */
    data class Added(val kind: String, val watchId: Long?) : QuickWatchOutcome

    /** An active watch already matched; nothing was created. */
    data class Existing(val kind: String, val watchId: Long?) : QuickWatchOutcome

    /** `503 live_prices_off`: no price source is configured. */
    data object LivePricesOff : QuickWatchOutcome

    /** `409 watch_limit`. */
    data class Limit(val detail: String?) : QuickWatchOutcome

    /** `422 spec_invalid`, or the item has nothing watchable. */
    data class Invalid(val detail: String?) : QuickWatchOutcome

    /** Anything else (network, 429, 5xx); [detail] is the backend text or null. */
    data class Failed(val detail: String?) : QuickWatchOutcome

    /** True for a watch that now exists for the caller. */
    val watching: Boolean get() = this is Added || this is Existing
}

/** Maps the repository result to an outcome; [fallbackKind] is used when the response carries no kind. */
fun AppResult<QuickWatchResponse>.toQuickWatchOutcome(fallbackKind: String): QuickWatchOutcome =
    when (this) {
        is AppResult.Success -> {
            val kind = value.kind ?: fallbackKind
            if (value.existing == true) QuickWatchOutcome.Existing(kind, value.watchId) else QuickWatchOutcome.Added(kind, value.watchId)
        }

        is AppResult.Failure -> {
            error.toQuickWatchOutcome()
        }
    }

private fun AppError.toQuickWatchOutcome(): QuickWatchOutcome =
    when (alertsErrorCode()) {
        AlertsErrorCode.LIVE_PRICES_OFF -> QuickWatchOutcome.LivePricesOff
        AlertsErrorCode.WATCH_LIMIT -> QuickWatchOutcome.Limit(message)
        AlertsErrorCode.SPEC_INVALID -> QuickWatchOutcome.Invalid(message)
        else -> QuickWatchOutcome.Failed(message)
    }

/** Telemetry values of the `alert_quick_watch`, `alert_suggest` and `alert_prefs_change` events (closed enums of the server registry). */
object AlertTelemetry {
    const val QUICK_WATCH = "alert_quick_watch"
    const val SUGGEST = "alert_suggest"
    const val PREFS_CHANGE = "alert_prefs_change"

    val QUICK_ACTIONS = setOf("add", "undo", "exists", "blocked")
    val SURFACES = setOf("results", "chat", "trip", "suggest")
    val PREF_FIELDS = setOf("digest", "quiet_hours", "instant", "cap", "group")

    /** The `action` token of a quick-watch [outcome]. */
    fun quickAction(outcome: QuickWatchOutcome): String =
        when (outcome) {
            is QuickWatchOutcome.Added -> "add"
            is QuickWatchOutcome.Existing -> "exists"
            else -> "blocked"
        }
}
