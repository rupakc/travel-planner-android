package io.github.rupakc.travelplanner.feature.plan.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.PriceWatchesRepository
import io.github.rupakc.travelplanner.core.domain.alerts.PricePreview
import io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogController
import io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogState
import io.github.rupakc.travelplanner.core.domain.alerts.isWatchablePlan
import io.github.rupakc.travelplanner.core.domain.alerts.tripPreviewParts
import io.github.rupakc.travelplanner.core.domain.plan.computePlanCost
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatchKinds
import io.github.rupakc.travelplanner.core.model.alerts.TripWatchSpec
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.Plan
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * "Watch whole trip" on trip detail (web PlanViewModal → PriceWatchDialog kind `trip`): hosts Team G's
 * [PriceWatchDialogController]; the screen shows `PriceWatchSheet` while [sheet] is set.
 */
@HiltViewModel
internal class TripWatchViewModel
    @Inject
    constructor(
        private val prices: PriceWatchesRepository,
        private val health: HealthRepository,
        auth: AuthRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        private val current = MutableStateFlow<PriceWatchDialogController?>(null)
        val sheet: StateFlow<PriceWatchDialogController?> = current.asStateFlow()

        /** Price watches need live prices on the server (`/health.fare_watch_available`, as on Results). */
        val available: StateFlow<Boolean> =
            health.capabilities.map { it?.fareWatchAvailable == true }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                health.capabilities.value?.fareWatchAvailable == true,
            )

        val userEmail: StateFlow<String?> =
            auth.session.map { it?.user?.email }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        init {
            viewModelScope.launch { if (health.capabilities.value == null) health.refresh() }
        }

        /** A saved trip that hasn't started yet can be watched (web `isWatchablePlan`). */
        fun canWatch(plan: Plan?): Boolean = available.value && isWatchablePlan(plan, clock.today())

        fun open(plan: Plan) {
            if (!canWatch(plan)) return
            val spec = TravelJson.encodeToJsonElement(TripWatchSpec.serializer(), TripWatchSpec(plan.id)) as JsonObject
            val preview =
                PricePreview(
                    title = plan.name,
                    planTotal = computePlanCost(plan.selections, plan.searchRequest).takeIf { it > 0 },
                    parts = tripPreviewParts(plan.selections, plan.searchRequest),
                )
            val state = PriceWatchDialogState(PriceWatchKinds.TRIP, spec, preview, emailAvailable = health.capabilities.value?.emailAlertsAvailable == true)
            current.value = PriceWatchDialogController(state, { prices.create(it) }, { prices.delete(it) })
        }

        fun dismiss() {
            current.value = null
        }
    }
