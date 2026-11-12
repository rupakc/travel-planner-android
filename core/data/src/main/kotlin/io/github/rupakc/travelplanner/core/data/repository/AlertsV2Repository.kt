package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.alerts.AdviceTip
import io.github.rupakc.travelplanner.core.model.alerts.AlertKind
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefs
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefsUpdate
import io.github.rupakc.travelplanner.core.model.alerts.PriceStory
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchResponse
import io.github.rupakc.travelplanner.core.model.alerts.Savings
import io.github.rupakc.travelplanner.core.model.alerts.SuggestionsResponse

/**
 * Alerts API 2.0 (price story, savings, one-tap watch, suggestions, preferences). Callers must have checked
 * `AlertsFlags.v2Available` (and `oneTapAvailable` for [quickWatch] and [suggestions]). Failures carry the server's
 * `error_code`; read it with `AppError.alertsErrorCode()`.
 */
interface AlertsV2Repository {
    /** `GET /alerts/summary`. */
    suspend fun summary(): AppResult<Savings>

    /** The story of one watch. `404 watch_not_found`, `422 kind_invalid`. */
    suspend fun story(kind: AlertKind, watchId: Long): AppResult<PriceStory>

    /** AI tip for a flight watch (cached 24 h server side). `422 advice_unsupported|advice_no_data`, `502 advice_failed`, `503 ai_limit|ai_billing`. */
    suspend fun advice(kind: AlertKind, watchId: Long): AppResult<AdviceTip>

    /** One-tap watch. `503 live_prices_off`, `409 watch_limit`, `422 spec_invalid`; an existing match is a success with `existing = true`. */
    suspend fun quickWatch(request: QuickWatchRequest): AppResult<QuickWatchResponse>

    /** Up to five suggestions, optionally for one trip. `404 trip_not_found`. */
    suspend fun suggestions(planId: Long? = null): AppResult<SuggestionsResponse>

    /** `GET /alerts/preferences`. */
    suspend fun preferences(): AppResult<AlertPrefs>

    /** `PUT /alerts/preferences` with any subset of the writable keys. `422 alert_prefs_invalid`. */
    suspend fun updatePreferences(update: AlertPrefsUpdate): AppResult<AlertPrefs>
}
