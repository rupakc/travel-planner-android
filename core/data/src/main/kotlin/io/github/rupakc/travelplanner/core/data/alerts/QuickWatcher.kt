package io.github.rupakc.travelplanner.core.data.alerts

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AlertsV2Repository
import io.github.rupakc.travelplanner.core.data.repository.FareWatchesRepository
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.data.repository.PriceWatchesRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.alerts.AlertTelemetry
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchOutcome
import io.github.rupakc.travelplanner.core.domain.alerts.toQuickWatchOutcome
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

private const val TITLE_MAX = 120
private const val SUBTITLE_MAX = 160

/**
 * One-tap "Watch" for results cards, chat and trip items (`POST /alerts/quick-watch`), shared by every feature
 * (features cannot depend on each other). [available] is the R3 gate (`live_prices` and the v2 API); when it is
 * false callers keep their existing flow. Telemetry carries only the kind, the action and the surface.
 */
@Singleton
class QuickWatcher
    @Inject
    constructor(
        private val flags: JourneyFlagsRepository,
        private val alerts: AlertsV2Repository,
        private val fareWatches: FareWatchesRepository,
        private val priceWatches: PriceWatchesRepository,
        private val telemetry: Telemetry,
    ) {
        /** True when the one-tap button may show and the route may be called. */
        suspend fun available(): Boolean = flags.flags().alerts.oneTapAvailable

        /** Creates (or finds) the watch. [kind] is `flight|hotel|activity|trip`, [surface] `results|chat|trip|suggest`. */
        suspend fun watch(kind: String, title: String, subtitle: String?, spec: JsonObject, planId: Long?, surface: String): QuickWatchOutcome {
            val request =
                QuickWatchRequest(
                    kind = kind,
                    title = title.trim().take(TITLE_MAX),
                    subtitle = subtitle?.trim()?.takeIf(String::isNotEmpty)?.take(SUBTITLE_MAX),
                    spec = spec,
                    planId = planId,
                    surface = surface,
                )
            val outcome = alerts.quickWatch(request).toQuickWatchOutcome(kind)
            telemetry.emit(AlertTelemetry.QUICK_WATCH, mapOf("kind" to kind, "action" to AlertTelemetry.quickAction(outcome), "surface" to surface))
            return outcome
        }

        /** Removes a watch this tap created (the snackbar Undo). Returns true when it is gone. */
        suspend fun undo(kind: String, watchId: Long, surface: String): Boolean {
            val result =
                if (kind == "flight") fareWatches.delete(watchId) else priceWatches.delete(watchId)
            val ok = result is AppResult.Success
            if (ok) telemetry.emit(AlertTelemetry.QUICK_WATCH, mapOf("kind" to kind, "action" to "undo", "surface" to surface))
            return ok
        }
    }
