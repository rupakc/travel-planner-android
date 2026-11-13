package io.github.rupakc.travelplanner.core.data.alerts

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AlertsV2Repository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.alerts.AlertTelemetry
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchOutcome
import io.github.rupakc.travelplanner.core.model.alerts.AlertSuggestion
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Watch prices for this plan?" after a plan is saved (`GET /alerts/suggestions?plan_id=`, then one-tap
 * `POST /alerts/quick-watch`). Inert unless one-tap is available (live prices and the v2 API); a plan the user
 * dismissed is not offered again in this process. Telemetry carries only the action and a count.
 */
@Singleton
class PlanWatchOffers
    @Inject
    constructor(
        private val quickWatcher: QuickWatcher,
        private val alerts: AlertsV2Repository,
        private val telemetry: Telemetry,
    ) {
        private val dismissedPlans: MutableSet<Long> = ConcurrentHashMap.newKeySet()
        private val justSaved: MutableSet<Long> = ConcurrentHashMap.newKeySet()

        /** A save of [planId] just finished: the next [takeJustSaved] for it is true (once). */
        fun markSaved(planId: Long) {
            justSaved += planId
        }

        /** True once after [markSaved], so a screen offers watches right after a save and not on every open. */
        fun takeJustSaved(planId: Long): Boolean = justSaved.remove(planId)

        /** Suggestions not yet watched for [planId]; empty when unavailable, dismissed, failed or none. */
        suspend fun load(planId: Long): List<AlertSuggestion> {
            if (planId in dismissedPlans || !quickWatcher.available()) return emptyList()
            val result = alerts.suggestions(planId) as? AppResult.Success ?: return emptyList()
            // The server only offers one-tap while live prices are on; honour its flag as well as the config's.
            if (!result.value.livePrices) return emptyList()
            val items = result.value.suggestions.filter { !it.alreadyWatching && it.kind != null }
            if (items.isNotEmpty()) telemetry.emit(AlertTelemetry.SUGGEST, mapOf("action" to "shown", "count" to items.size))
            return items
        }

        suspend fun accept(planId: Long, suggestion: AlertSuggestion): QuickWatchOutcome {
            val outcome =
                quickWatcher.watch(suggestion.kind.orEmpty(), suggestion.title.orEmpty(), suggestion.subtitle, suggestion.spec, planId, "suggest")
            if (outcome.watching) telemetry.emit(AlertTelemetry.SUGGEST, mapOf("action" to "accepted", "count" to 1))
            return outcome
        }

        /** The user closed the prompt: this plan is not offered again until the app restarts. */
        fun dismiss(planId: Long) {
            dismissedPlans += planId
            telemetry.emit(AlertTelemetry.SUGGEST, mapOf("action" to "dismissed", "count" to 1))
        }
    }
