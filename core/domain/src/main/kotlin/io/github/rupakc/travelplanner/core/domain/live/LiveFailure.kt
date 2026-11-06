package io.github.rupakc.travelplanner.core.domain.live

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.userFacingDetail

/**
 * Why a live (grounded) call failed — Near me v2 and "Search the web now" share these. Screens show
 * [LiveError.message] (the backend's words) when present, else their own copy for the [LiveFailure].
 */
enum class LiveFailure {
    /** No connection or timeout. */
    OFFLINE,

    /** 429: the backend's message says how long to wait. */
    RATE_LIMITED,

    /** 503 `ai_limit`: this month's AI budget is spent. */
    AI_LIMIT,

    /** 503 `ai_billing`: billing is off on the server. */
    AI_BILLING,

    /** 503 `grounding_unavailable`: the server runs a model without Google grounding. */
    GROUNDING_UNAVAILABLE,

    /** 404 `no_place` (Near me) or "Watch not found" (live check). */
    NOT_FOUND,

    /** 409 `watch_expired` (live check): the watch expired or its dates passed; nothing to retry. */
    WATCH_EXPIRED,

    /** 502 (`agent_error`, `live_check_failed`) and other 5xx. */
    SERVER,

    /** Session expired, a bad response or anything else. */
    OTHER,
}

/** A classified failure with the backend's user-facing text (null for client-side failures). */
data class LiveError(val failure: LiveFailure, val message: String?, val code: String? = null)

/** Backend error codes of the grounded endpoints. */
object LiveErrorCodes {
    const val AI_LIMIT = "ai_limit"
    const val AI_BILLING = "ai_billing"
    const val GROUNDING_UNAVAILABLE = "grounding_unavailable"
    const val NO_PLACE = "no_place"
    const val AGENT_ERROR = "agent_error"
    const val LIVE_CHECK_FAILED = "live_check_failed"
    const val WATCH_EXPIRED = "watch_expired"
}

/** Maps an [AppError] from `/api/nearby` or `/api/live-check` to a [LiveError]. */
fun AppError.toLiveError(): LiveError {
    val text = userFacingDetail()
    return when (this) {
        is AppError.Network -> {
            LiveError(LiveFailure.OFFLINE, null)
        }

        is AppError.RateLimited -> {
            LiveError(LiveFailure.RATE_LIMITED, text)
        }

        is AppError.NotFound -> {
            LiveError(LiveFailure.NOT_FOUND, text, code)
        }

        is AppError.Server -> {
            val failure =
                when (code) {
                    LiveErrorCodes.AI_LIMIT -> LiveFailure.AI_LIMIT
                    LiveErrorCodes.AI_BILLING -> LiveFailure.AI_BILLING
                    LiveErrorCodes.GROUNDING_UNAVAILABLE -> LiveFailure.GROUNDING_UNAVAILABLE
                    else -> LiveFailure.SERVER
                }
            LiveError(failure, text, code)
        }

        is AppError.Client -> {
            if (code == LiveErrorCodes.WATCH_EXPIRED) LiveError(LiveFailure.WATCH_EXPIRED, text, code) else LiveError(LiveFailure.OTHER, text, code)
        }

        else -> {
            LiveError(LiveFailure.OTHER, text)
        }
    }
}
