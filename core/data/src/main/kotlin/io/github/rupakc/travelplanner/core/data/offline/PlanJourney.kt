package io.github.rupakc.travelplanner.core.data.offline

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.data.repository.PrepRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.journey.PlanCompleteness
import io.github.rupakc.travelplanner.core.domain.journey.PrepRules
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

/**
 * What results, My Plan and chat share of the trips journey, all behind `features.trips` (against a backend without
 * it every call is a no-op): the plan's completeness and the trip snapshot sent after a save.
 */
@Singleton
class PlanJourney
    @Inject
    constructor(
        private val flags: JourneyFlagsRepository,
        private val snapshots: TripSnapshotUploader,
        private val prep: PrepRepository,
        private val clock: AppClock,
        private val telemetry: Telemetry,
    ) {
        suspend fun enabled(): Boolean = flags.flags().trips

        /**
         * After a plan save or update: `PUT snapshot` with the streamed [sections] (error results skipped; only the five
         * allowed keys and no `verification` survive in [TripSnapshotUploader]). Plans are trips server-side already.
         */
        suspend fun onPlanSaved(planId: Long, sections: JsonObject?) {
            if (sections == null || !enabled()) return
            val usable = JsonObject(sections.filterValues { !(it is JsonObject && it.containsKey("error")) })
            snapshots.put(planId, usable)
        }

        private var visaMemo: Triple<Long, Long, Boolean>? = null

        /** The trip's visa prep item is done or not needed; remembered per plan for a minute so edits don't refetch. */
        private suspend fun visaChecked(planId: Long): Boolean {
            val now = clock.now().toEpochMilli()
            visaMemo?.takeIf { it.first == planId && now - it.second < VISA_MEMO_MS }?.let { return it.third }
            val value = (prep.prep(planId, clock.today().toString()) as? AppResult.Success)?.let { PrepRules.visaChecked(it.value.items) } ?: false
            visaMemo = Triple(planId, now, value)
            return value
        }

        /** Completeness of [draft], or null when trips are off. Visa counts as checked from the trip's prep list. */
        suspend fun completeness(draft: PlanDraft): PlanCompleteness.Result? {
            if (!enabled()) return null
            val visa = draft.planId?.let { visaChecked(it) } ?: false
            val request = draft.searchRequest?.let { TravelJson.encodeToJsonElement(TravelSearchRequest.serializer(), it) as? JsonObject }
            return PlanCompleteness.of(draft.selections, request, visa)
        }

        /** `completeness_view` for [surface] (`drawer` or `trip`); the percent only. */
        fun meterViewed(surface: String, percent: Int) {
            telemetry.emit(TelemetryEvents.COMPLETENESS_VIEW, mapOf("surface" to surface, "percent" to percent))
        }

        private companion object {
            const val VISA_MEMO_MS = 60_000L
        }
    }
