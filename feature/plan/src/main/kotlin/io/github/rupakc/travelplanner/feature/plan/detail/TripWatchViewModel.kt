package io.github.rupakc.travelplanner.feature.plan.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.data.alerts.PlanWatchOffers
import io.github.rupakc.travelplanner.core.data.alerts.QuickWatcher
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.PriceWatchesRepository
import io.github.rupakc.travelplanner.core.domain.alerts.PricePreview
import io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogController
import io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogState
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchOutcome
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchSpecs
import io.github.rupakc.travelplanner.core.domain.alerts.isWatchablePlan
import io.github.rupakc.travelplanner.core.domain.alerts.tripPreviewParts
import io.github.rupakc.travelplanner.core.domain.plan.computePlanCost
import io.github.rupakc.travelplanner.core.model.alerts.AlertSuggestion
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatchKinds
import io.github.rupakc.travelplanner.core.model.alerts.TripWatchSpec
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.ui.watch.WatchOfferRow
import io.github.rupakc.travelplanner.core.ui.watch.WatchOfferState
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
        private val quickWatcher: QuickWatcher,
        private val offers: PlanWatchOffers,
    ) : ViewModel() {
        private val outcomes = Channel<QuickWatchOutcome>(Channel.BUFFERED)

        /** One-tap results to show as a snackbar (`QuickWatchOutcome`). */
        val outcomeFlow: Flow<QuickWatchOutcome> = outcomes.receiveAsFlow()

        private class Offer(val planId: Long, val items: List<AlertSuggestion>, val states: List<WatchOfferState>)

        private val offer = MutableStateFlow<Offer?>(null)

        /** The "watch prices for this trip?" prompt rows; empty (hidden) unless one-tap is available. */
        val offerRows: StateFlow<List<WatchOfferRow>> =
            offer.map { o -> o?.items?.mapIndexed { i, s -> WatchOfferRow(s.title.orEmpty(), s.subtitle, o.states[i]) }.orEmpty() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        /** Loads suggestions for the saved plan [planId] (inert without live prices / Alerts 2.0, or after a dismissal). */
        fun loadOffer(planId: Long) {
            if (offer.value?.planId == planId) return
            viewModelScope.launch {
                val items = offers.load(planId)
                offer.value = items.takeIf { it.isNotEmpty() }?.let { Offer(planId, it, List(it.size) { WatchOfferState.Idle }) }
            }
        }

        /** [loadOffer] only when the plan was saved a moment ago (see [PlanWatchOffers.markSaved]). */
        fun loadOfferIfJustSaved(planId: Long) {
            if (offers.takeJustSaved(planId)) loadOffer(planId)
        }

        fun watchOffer(index: Int) {
            val o = offer.value ?: return
            if (o.states.getOrNull(index) != WatchOfferState.Idle) return
            setOfferState(index, WatchOfferState.Adding)
            viewModelScope.launch {
                val outcome = offers.accept(o.planId, o.items[index])
                setOfferState(index, if (outcome.watching) WatchOfferState.Watching else WatchOfferState.Idle)
                outcomes.trySend(outcome)
            }
        }

        fun dismissOffer() {
            offer.value?.let { offers.dismiss(it.planId) }
            offer.value = null
        }

        private fun setOfferState(index: Int, value: WatchOfferState) {
            offer.update { o -> o?.let { Offer(it.planId, it.items, it.states.mapIndexed { i, st -> if (i == index) value else st }) } }
        }

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
            viewModelScope.launch {
                // One tap (Alerts 2.0 with live prices): the whole trip is watched at once; otherwise the sheet below.
                if (quickWatcher.available()) {
                    val draft = QuickWatchSpecs.forTrip(plan.id, plan.name)
                    outcomes.trySend(quickWatcher.watch(draft.kind, draft.title, draft.subtitle, draft.spec, plan.id, "trip"))
                } else {
                    openSheet(plan)
                }
            }
        }

        private fun openSheet(plan: Plan) {
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
