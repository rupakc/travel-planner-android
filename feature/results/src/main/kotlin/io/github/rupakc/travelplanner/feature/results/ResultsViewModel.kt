package io.github.rupakc.travelplanner.feature.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.SearchResultsReducer
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.repository.FareCalendarRepository
import io.github.rupakc.travelplanner.core.data.repository.FareWatchesRepository
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.PriceWatchesRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.results.BestValuePackage
import io.github.rupakc.travelplanner.core.domain.results.BudgetAllocation
import io.github.rupakc.travelplanner.core.domain.results.BudgetPlanner
import io.github.rupakc.travelplanner.core.domain.results.FareCalendarRules
import io.github.rupakc.travelplanner.core.domain.results.Remix
import io.github.rupakc.travelplanner.core.domain.results.RemixPreset
import io.github.rupakc.travelplanner.core.domain.results.WatchSpecs
import io.github.rupakc.travelplanner.core.domain.results.searchKey
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.alerts.ALERT_ANY_DROP
import io.github.rupakc.travelplanner.core.model.alerts.ActivityWatchSpec
import io.github.rupakc.travelplanner.core.model.alerts.CreatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.HotelWatchSpec
import io.github.rupakc.travelplanner.core.model.alerts.NoLivePriceError
import io.github.rupakc.travelplanner.core.model.alerts.PriceSuggestion
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatchKinds
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.search.HotelFilters
import io.github.rupakc.travelplanner.core.model.search.LayoverRequest
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.search.SectionResult
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.ConfidenceData
import io.github.rupakc.travelplanner.core.model.section.FlightLayover
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.PricingAdvisorData
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/**
 * Results screen state: the app-wide search stream (SearchRepository, which already
 * applies the web's static→AI, keep-static-on-AI-error, replace-by-type and
 * retry-once rules) merged with filters, the plan draft and traveller-type order.
 * The latest sections are written to the plan draft, so after process death the
 * screen restores them from the draft instead of streaming again.
 */
@Suppress("LongParameterList", "TooManyFunctions")
@HiltViewModel
class ResultsViewModel
    @Inject
    constructor(
        private val search: SearchRepository,
        private val plan: CurrentPlanStore,
        private val health: HealthRepository,
        private val fareCalendar: FareCalendarRepository,
        private val fareWatches: FareWatchesRepository,
        private val priceWatches: PriceWatchesRepository,
        profile: ProfileRepository,
        catalogue: Optional<FeatureCatalogue>,
        private val clock: AppClock,
        dispatchers: AppDispatchers,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val local = MutableStateFlow(LocalState())
        private val builder = ResultsStateBuilder()
        private val messageChannel = Channel<ResultsMessage>(Channel.BUFFERED)

        /** One job per section (filter / retry); a new call or a new search cancels the previous one. */
        private val jobs = mutableMapOf<SectionId, Job>()

        /** One-shot snackbar messages. */
        val messages: Flow<ResultsMessage> = messageChannel.receiveAsFlow()

        /** Last decoded snapshot, keyed by the draft's searchData identity (decoded only when shown). */
        @Volatile private var snapshotMemo: Pair<Any?, SearchResultsState?> = null to null

        private fun snapshotOf(draft: PlanDraft): SearchResultsState? {
            val memo = snapshotMemo
            if (memo.first === draft.searchData && memo.second?.request == draft.searchRequest) return memo.second
            return ResultsStateBuilder.snapshot(draft)?.let(::withSavedExtras).also { snapshotMemo = draft.searchData to it }
        }

        private val visibleFeatures: Flow<Set<FeatureId>?> =
            if (catalogue.isPresent) {
                combine(profile.profile, profile.featureOverrides) { p, overrides ->
                    val types = (listOfNotNull(p.primaryType) + p.secondaryTypes).mapNotNull { name -> TravellerType.entries.firstOrNull { it.name == name } }
                    if (!p.onboardingCompleted || types.isEmpty()) null else catalogue.get().visible(types, overrides)
                }
            } else {
                flowOf(null)
            }

        private val source: Flow<SourcedResults> =
            combine(search.current, plan.draft) { live, draft -> sourceOf(live, draft) }.flowOn(dispatchers.default)

        private fun sourceOf(live: SearchResultsState, draft: PlanDraft): SourcedResults =
            when (val chosen = ResultsStateBuilder.selectSource(live, draft)) {
                ResultsSource.LIVE -> SourcedResults(live, chosen)
                ResultsSource.SNAPSHOT -> SourcedResults(snapshotOf(draft) ?: SearchResultsState(request = draft.searchRequest), chosen)
                ResultsSource.RUN_SAVED -> SourcedResults(SearchResultsState(request = draft.searchRequest), chosen)
                ResultsSource.NONE -> SourcedResults(SearchResultsState(), chosen)
            }

        val state: StateFlow<ResultsUiState> =
            combine(source, plan.draft, local, health.capabilities, visibleFeatures) { sourced, draft, l, caps, visible ->
                builder.build(sourced, draft, l, caps, visible, clock.today())
            }.flowOn(dispatchers.default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ResultsUiState())

        init {
            viewModelScope.launch { if (health.capabilities.value == null) health.refresh() }
            viewModelScope.launch { resetOnNewSearch() }
            viewModelScope.launch { attachToDraft() }
            viewModelScope.launch { rememberExtras() }
        }

        /** A different search on screen: cancel its filter/retry jobs and reset the per-search state. */
        private suspend fun resetOnNewSearch() {
            source.map { searchKey(it.results.request) }.distinctUntilChanged().collect { key ->
                if (key != local.value.key) {
                    jobs.values.forEach { it.cancel() }
                    jobs.clear()
                    local.update { it.forNewSearch(key) }
                }
            }
        }

        /**
         * Keeps the draft's `searchData` = the sections on screen (plan snapshot, chat context,
         * process death). Only for the draft's own search: a stream for another trip is never written.
         */
        private suspend fun attachToDraft() {
            combine(search.current, plan.draft, local) { live, draft, l -> Triple(live, draft, l.overrides) }
                .collect { (live, draft, overrides) ->
                    val target =
                        when (ResultsStateBuilder.selectSource(live, draft)) {
                            ResultsSource.LIVE -> {
                                if (live.phase == StreamPhase.FAILED) return@collect
                                SearchResultsReducer.sectionsJson(SearchResultsState(request = live.request, sections = live.sections + overrides))
                            }

                            ResultsSource.SNAPSHOT -> {
                                if (overrides.isEmpty()) return@collect
                                JsonObject(draft.searchData.orEmpty() + overrides.map { (id, r) -> id.wire to r.raw })
                            }

                            else -> {
                                return@collect
                            }
                        }
                    val request =
                        (if (live.request != null && searchKey(live.request) == searchKey(draft.searchRequest)) live.request else null)
                            ?: draft.searchRequest ?: live.request ?: return@collect
                    if (draft.searchData != target || draft.searchRequest != request) plan.attachSearch(request, target)
                }
        }

        /** Travel Ease and the pricing advisor are not sections; keep them across process death (M1). */
        private suspend fun rememberExtras() {
            search.current.map {
                Triple(
                    searchKey(it.request),
                    it.confidence,
                    it.pricingAdvisor,
                )
            }.distinctUntilChanged().collect { (key, confidence, advisor) ->
                if (key == null || key != searchKey(plan.draft.value.searchRequest)) return@collect
                savedState[KEY_EXTRAS_SEARCH] = key
                savedState[KEY_CONFIDENCE] = confidence?.let { TravelJson.encodeToString(ConfidenceData.serializer(), it) }
                savedState[KEY_ADVISOR] = advisor?.let { TravelJson.encodeToString(PricingAdvisorData.serializer(), it) }
            }
        }

        private fun withSavedExtras(snapshot: SearchResultsState): SearchResultsState {
            if (savedState.get<String>(KEY_EXTRAS_SEARCH) != searchKey(snapshot.request)) return snapshot
            val confidence =
                savedState.get<String>(KEY_CONFIDENCE)?.let {
                    runCatching { TravelJson.decodeFromString(ConfidenceData.serializer(), it) }.getOrNull()
                }
            val advisor =
                savedState.get<String>(KEY_ADVISOR)?.let {
                    runCatching { TravelJson.decodeFromString(PricingAdvisorData.serializer(), it) }.getOrNull()
                }
            return snapshot.copy(confidence = confidence, pricingAdvisor = advisor)
        }

        private val request: TravelSearchRequest? get() = state.value.request ?: search.current.value.request ?: plan.draft.value.searchRequest

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        fun onEvent(event: ResultsEvent) {
            when (event) {
                is ResultsEvent.Toggle -> {
                    plan.toggle(event.item)
                }

                is ResultsEvent.ToggleSection -> {
                    toggleSection(event.id)
                }

                is ResultsEvent.SelectSection -> {
                    local.update { it.copy(selected = event.id) }
                }

                is ResultsEvent.Query -> {
                    local.update { it.copy(query = event.text) }
                }

                ResultsEvent.RetryStream -> {
                    request?.let(search::start)
                }

                is ResultsEvent.RetrySection -> {
                    retrySection(event.id)
                }

                ResultsEvent.RunSavedSearch -> {
                    plan.draft.value.searchRequest?.let(search::start)
                }

                ResultsEvent.ApplyPackage -> {
                    applyPackage()
                }

                ResultsEvent.UndoPackage -> {
                    local.value.packageUndo?.let { undo -> plan.restore(undo).also { local.update { it.copy(packageUndo = null) } } }
                }

                is ResultsEvent.BudgetShare -> {
                    updateBudget { a -> BudgetPlanner.reallocate(a, event.category, event.value) }
                }

                is ResultsEvent.BudgetTotal -> {
                    updateBudget { a -> BudgetPlanner.withTotal(a, event.total) }
                }

                ResultsEvent.BudgetReset -> {
                    local.update { it.copy(allocation = null, budgetTouched = false) }
                }

                is ResultsEvent.ShowOnlyFits -> {
                    local.update { it.copy(showOnlyFits = event.on) }
                }

                ResultsEvent.FindCheaperHotels -> {
                    findCheaperHotels()
                }

                is ResultsEvent.Remix -> {
                    remix(event.preset)
                }

                ResultsEvent.DismissRemix -> {
                    local.update { it.copy(remix = null) }
                }

                ResultsEvent.UndoNewPlan -> {
                    undoNewPlan()
                }

                is ResultsEvent.ApplyFlightFilters -> {
                    local.update { it.copy(flightFilters = event.filters) }
                    filter(SectionId.FLIGHTS, FlightsData.serializer()) { r -> search.flightsFiltered(r, event.filters) }
                }

                is ResultsEvent.ApplyHotelFilters -> {
                    local.update { it.copy(hotelFilters = event.filters) }
                    filter(SectionId.HOTELS, HotelsData.serializer()) { r -> search.hotelsFiltered(r, event.filters) }
                }

                is ResultsEvent.ApplyActivityFilters -> {
                    local.update { it.copy(activityFilters = event.filters) }
                    filter(SectionId.ACTIVITIES, ActivitiesData.serializer()) { r -> search.activitiesFiltered(r, event.filters) }
                }

                ResultsEvent.ToggleFareCalendar -> {
                    toggleFareCalendar()
                }

                ResultsEvent.RetryFareCalendar -> {
                    loadFareCalendar()
                }

                is ResultsEvent.PickFareDate -> {
                    local.update { it.copy(fareCalendar = it.fareCalendar.copy(pending = event.date)) }
                }

                ResultsEvent.ConfirmFareReplan -> {
                    confirmFareReplan()
                }

                is ResultsEvent.OpenLayover -> {
                    openLayover(event.layover)
                }

                ResultsEvent.CloseLayover -> {
                    local.update { it.copy(layover = null) }
                }

                ResultsEvent.WatchFare -> {
                    watchFare()
                }

                is ResultsEvent.WatchHotel -> {
                    watchHotel(event.hotel)
                }

                is ResultsEvent.WatchActivity -> {
                    watchActivity(event.activity)
                }

                is ResultsEvent.WatchSuggestion -> {
                    watchSuggestion(event.suggestion)
                }

                ResultsEvent.DismissNoLivePrice -> {
                    local.update { it.copy(noLivePrice = null) }
                }

                is ResultsEvent.ShowVerification -> {
                    local.update { it.copy(verificationFor = event.section) }
                }

                ResultsEvent.DismissVerification -> {
                    local.update { it.copy(verificationFor = null) }
                }
            }
        }

        private fun toggleSection(id: SectionId) {
            val current = state.value.sections.firstOrNull { it.id == id }?.expanded ?: true
            local.update { it.copy(expanded = it.expanded + (id to !current)) }
        }

        // ── best value / budget ───────────────────────────────────────────────

        private fun applyPackage() {
            val pkg = state.value.bestValue?.takeUnless { it.disabled }?.pkg ?: return
            val undo = plan.snapshot()
            plan.setSelections(BestValuePackage.apply(plan.draft.value.selections, pkg))
            local.update { it.copy(packageUndo = undo) }
        }

        private fun undoNewPlan() {
            val undo = local.value.newPlanUndo ?: return
            plan.setSelections(undo.selections)
            local.update { it.copy(newPlanUndo = null) }
        }

        private fun updateBudget(change: (BudgetAllocation) -> BudgetAllocation) {
            val current = state.value.budget?.allocation ?: return
            local.update { it.copy(allocation = change(current), budgetTouched = true) }
        }

        /** Web: "Find cheaper hotels" = the hotel filter with the planner's max nightly. */
        private fun findCheaperHotels() {
            val budget = state.value.budget ?: return
            local.update { it.copy(findingCheaperHotels = true) }
            onEvent(ResultsEvent.ApplyHotelFilters(HotelFilters(maxPricePerNightUsd = budget.maxNightly.toDouble())))
        }

        // ── re-runs ───────────────────────────────────────────────────────────

        /** Web `runSearch`: a new search starts a new plan; the previous picks can be restored from the snackbar. */
        private fun rerun(next: TravelSearchRequest) {
            val previous = plan.snapshot()
            plan.newPlan()
            // The new plan is for the new search, so its stream is the one shown and stored.
            plan.attachSearch(next, null)
            local.update { it.copy(newPlanUndo = previous.takeUnless { p -> p.selections.isEmpty }) }
            if (!previous.selections.isEmpty) messageChannel.trySend(ResultsMessage.NewPlanStarted)
            search.start(next)
        }

        private fun remix(preset: RemixPreset) {
            val req = request ?: return
            val remixed = Remix.apply(req, preset) ?: return
            val before = Remix.snapshot(ResultsStateBuilder.tripResults(builder.effectiveSections(currentResults(), local.value)))
            local.update { it.copy(remix = preset to before) }
            rerun(remixed)
        }

        private fun confirmFareReplan() {
            val req = request ?: return
            val pending = local.value.fareCalendar.pending ?: return
            val departure = pending.departureDate ?: return
            local.update { it.copy(remix = null, fareCalendar = FareCalendarUi()) }
            rerun(req.copy(departureDate = departure, returnDate = pending.returnDate ?: req.returnDate))
        }

        private fun currentResults(): SearchResultsState =
            search.current.value.takeIf { it.phase != StreamPhase.IDLE } ?: ResultsStateBuilder.snapshot(plan.draft.value) ?: search.current.value

        private fun retrySection(id: SectionId) {
            val req = request ?: return
            if (id !in SINGLE_AGENT) {
                search.start(req)
                return
            }
            runSectionJob(id, req, onFailure = { error -> messageChannel.trySend(ResultsMessage.RetryFailed(error.message)) }) { r ->
                when (val result = search.section(id, r)) {
                    is AppResult.Success -> AppResult.Success(result.value to encode(result.value))
                    is AppResult.Failure -> result
                }
            }
        }

        private fun <T : SectionData> filter(id: SectionId, serializer: KSerializer<T>, call: suspend (TravelSearchRequest) -> AppResult<T>) {
            val req = request ?: return
            local.update { it.copy(expanded = it.expanded + (id to true)) }
            runSectionJob(id, req, onFailure = { error -> local.update { it.copy(errors = it.errors + (id to FilterError(error.message))) } }) { r ->
                when (val result = call(r)) {
                    is AppResult.Success -> AppResult.Success(result.value to (TravelJson.encodeToJsonElement(serializer, result.value) as JsonObject))
                    is AppResult.Failure -> result
                }
            }
        }

        /**
         * One job per section: a newer filter/retry cancels the older one, a new search cancels all
         * (I1), and a result is dropped when the search on screen is no longer the one it was for.
         */
        private fun runSectionJob(
            id: SectionId,
            req: TravelSearchRequest,
            onFailure: (AppError) -> Unit,
            call: suspend (TravelSearchRequest) -> AppResult<Pair<SectionData, JsonObject>>,
        ) {
            val key = searchKey(req)
            jobs.remove(id)?.cancel()
            local.update { it.copy(loading = it.loading + id, errors = it.errors - id) }
            jobs[id] =
                viewModelScope.launch {
                    val result = call(req)
                    if (local.value.key != key) return@launch
                    when (result) {
                        is AppResult.Success -> {
                            val (data, raw) = result.value
                            local.update {
                                if (it.key != key) {
                                    it
                                } else {
                                    it.copy(
                                        loading = it.loading - id,
                                        overrides = it.overrides + (id to SectionResult(id, data, raw, null)),
                                        findingCheaperHotels = false,
                                    )
                                }
                            }
                        }

                        is AppResult.Failure -> {
                            local.update { if (it.key != key) it else it.copy(loading = it.loading - id, findingCheaperHotels = false) }
                            onFailure(result.error)
                        }
                    }
                    jobs.remove(id)
                }
        }

        private fun encode(data: SectionData): JsonObject = ResultsStateBuilder.encode(data)

        // ── fare calendar / layover ───────────────────────────────────────────

        private fun toggleFareCalendar() {
            val open = !local.value.fareCalendar.open
            local.update { it.copy(fareCalendar = it.fareCalendar.copy(open = open)) }
            if (open && local.value.fareCalendar.status in setOf(LoadStatus.IDLE, LoadStatus.ERROR)) loadFareCalendar()
        }

        private fun loadFareCalendar() {
            val req = request ?: return
            local.update { it.copy(fareCalendar = it.fareCalendar.copy(status = LoadStatus.LOADING, error = null)) }
            viewModelScope.launch {
                val next =
                    when (val r = fareCalendar.fareCalendar(req)) {
                        is AppResult.Success -> FareCalendarUi(open = true, status = LoadStatus.DONE, dates = r.value.dates)
                        is AppResult.Failure -> FareCalendarUi(open = true, status = LoadStatus.ERROR, error = r.error)
                    }
                local.update { it.copy(fareCalendar = next.copy(open = it.fareCalendar.open)) }
            }
        }

        private fun openLayover(layover: FlightLayover) {
            val req = request ?: return
            val place = layover.city ?: layover.airport ?: return
            val hours = layover.durationHours ?: return
            local.update { it.copy(layover = LayoverUi(layover, LoadStatus.LOADING)) }
            viewModelScope.launch {
                val body =
                    LayoverRequest(city = place, durationHours = hours, nationality = req.nationality, airport = layover.airport, interests = req.interests)
                val r = search.layover(body)
                local.update { l ->
                    if (l.layover?.layover != layover) {
                        l
                    } else {
                        when (r) {
                            is AppResult.Success -> l.copy(layover = LayoverUi(layover, LoadStatus.DONE, plan = r.value))
                            is AppResult.Failure -> l.copy(layover = LayoverUi(layover, LoadStatus.ERROR, error = r.error))
                        }
                    }
                }
            }
        }

        // ── watches (call the repositories directly; Team G's dialogs swap in at integration) ──

        private fun watchFare() {
            val req = request ?: return
            if (!WatchSpecs.isWatchableTrip(req, clock.today())) {
                messageChannel.trySend(ResultsMessage.WatchUnavailable)
                return
            }
            local.update { it.copy(watches = it.watches.copy(fare = WatchState(busy = true))) }
            viewModelScope.launch {
                val trip = WatchSpecs.fareTrip(req).copy(alertMode = ALERT_ANY_DROP, flexibleDays = 0)
                when (val r = fareWatches.create(trip)) {
                    is AppResult.Success -> {
                        val w = r.value
                        val live = w.lastPrice ?: w.baselinePrice
                        val shown = state.value.sections.firstOrNull { it.id == SectionId.FLIGHTS }?.data as? FlightsData
                        val price = live ?: WatchSpecs.cheapestFlightPrice(shown)
                        local.update { it.copy(watches = it.watches.copy(fare = WatchState(watchId = w.id, price = live, currency = "USD"))) }
                        val travellers = w.numTravelers ?: trip.numTravelers
                        messageChannel.trySend(ResultsMessage.FareWatched(price, travellers, w.alreadyLow == true, w.alreadyWatching == true))
                    }

                    is AppResult.Failure -> {
                        local.update { it.copy(watches = it.watches.copy(fare = null)) }
                        messageChannel.trySend(ResultsMessage.WatchFailed(KIND_FARE, r.error.message))
                    }
                }
            }
        }

        /** Airport codes → city names, as the web's `useAirportCities` (local airport data, no live price calls). */
        private suspend fun cityName(value: String?): String? {
            if (value == null || !WatchSpecs.isAirportCode(value)) return value
            val code = value.trim()
            val rows = (search.airports(code, AIRPORT_LOOKUP_LIMIT) as? AppResult.Success)?.value.orEmpty()
            return rows.firstOrNull { it.iataCode.equals(code, ignoreCase = true) }?.city ?: value
        }

        private suspend fun withCityNames(req: TravelSearchRequest): TravelSearchRequest =
            req.copy(destination = cityName(req.destination) ?: req.destination, destinations = req.destinations?.map { cityName(it) ?: it })

        private fun watchHotel(hotel: Hotel) {
            val req = request ?: return
            val name = hotel.name ?: return
            setHotelWatch(name, WatchState(busy = true))
            viewModelScope.launch {
                val spec = WatchSpecs.hotelWatchSpec(hotel.copy(city = cityName(hotel.city)), withCityNames(req), clock.today())
                if (spec == null) {
                    setHotelWatch(name, null)
                    messageChannel.trySend(ResultsMessage.WatchUnavailable)
                    return@launch
                }
                val body = TravelJson.encodeToJsonElement(HotelWatchSpec.serializer(), spec) as JsonObject
                createPriceWatch(PriceWatchKinds.HOTEL, name, body) { setHotelWatch(name, it) }
            }
        }

        private fun watchActivity(activity: Activity) {
            val req = request ?: return
            val name = activity.name ?: return
            setActivityWatch(name, WatchState(busy = true))
            viewModelScope.launch {
                val itinerary = state.value.sections.firstOrNull { it.id == SectionId.ITINERARY }?.data as? ItineraryData
                val spec = WatchSpecs.activitySpec(activity.copy(city = cityName(activity.city)), withCityNames(req), itinerary, clock.today())
                if (spec == null) {
                    setActivityWatch(name, null)
                    messageChannel.trySend(ResultsMessage.WatchUnavailable)
                    return@launch
                }
                val body = TravelJson.encodeToJsonElement(ActivityWatchSpec.serializer(), spec) as JsonObject
                createPriceWatch(PriceWatchKinds.ACTIVITY, name, body) { setActivityWatch(name, it) }
            }
        }

        private fun watchSuggestion(suggestion: PriceSuggestion) {
            val pending = local.value.noLivePrice ?: return
            val spec = suggestion.spec ?: return
            local.update { it.copy(noLivePrice = null) }
            viewModelScope.launch {
                createPriceWatch(pending.kind, pending.itemName, spec) { }
            }
        }

        private suspend fun createPriceWatch(kind: String, itemName: String, spec: JsonObject, onState: (WatchState?) -> Unit) {
            when (val r = priceWatches.create(CreatePriceWatchRequest(kind = kind, spec = spec, alertMode = ALERT_ANY_DROP))) {
                is AppResult.Success -> {
                    val w: PriceWatch = r.value
                    onState(WatchState(watchId = w.id, price = w.baselinePrice ?: w.lastPrice, currency = w.currency))
                    messageChannel.trySend(ResultsMessage.PriceWatched(w.baselinePrice ?: w.lastPrice, w.currency, w.alreadyWatching == true))
                }

                is AppResult.Failure -> {
                    onState(null)
                    val error = r.error
                    val noLive = (error as? AppError.Client)?.takeIf { it.code == NO_LIVE_PRICE }?.let(::decodeNoLivePrice)
                    if (noLive != null) {
                        local.update { it.copy(noLivePrice = NoLivePriceUi(kind, itemName, noLive.message ?: error.message, noLive.suggestions)) }
                    } else {
                        messageChannel.trySend(ResultsMessage.WatchFailed(kind, error.message))
                    }
                }
            }
        }

        /** 422 `no_live_price`: `extras` carries the other top-level keys as raw JSON text. */
        private fun decodeNoLivePrice(error: AppError.Client): NoLivePriceError {
            val suggestions =
                error.extras["suggestions"]?.let { text ->
                    runCatching { TravelJson.decodeFromString(ListSerializer(PriceSuggestion.serializer()), text) }.getOrNull()
                }.orEmpty()
            return NoLivePriceError(message = error.message, code = error.code, suggestions = suggestions)
        }

        private fun setHotelWatch(name: String, value: WatchState?) =
            local.update { l -> l.copy(watches = l.watches.copy(hotels = if (value == null) l.watches.hotels - name else l.watches.hotels + (name to value))) }

        private fun setActivityWatch(name: String, value: WatchState?) =
            local.update { l ->
                l.copy(watches = l.watches.copy(activities = if (value == null) l.watches.activities - name else l.watches.activities + (name to value)))
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val AIRPORT_LOOKUP_LIMIT = 5
            const val KIND_FARE = "fare"
            const val NO_LIVE_PRICE = "no_live_price"
            const val KEY_EXTRAS_SEARCH = "results_extras_search"
            const val KEY_CONFIDENCE = "results_confidence"
            const val KEY_ADVISOR = "results_pricing_advisor"

            /** Sections with a single-agent route (`SearchRepository.section`); others retry by re-running the search. */
            val SINGLE_AGENT =
                setOf(
                    SectionId.FLIGHTS,
                    SectionId.HOTELS,
                    SectionId.ACTIVITIES,
                    SectionId.VISA,
                    SectionId.SIM,
                    SectionId.TIPS,
                    SectionId.GETTING_AROUND,
                    SectionId.FOREX,
                    SectionId.ITINERARY,
                    SectionId.EVENTS,
                    SectionId.WALKING_TOURS,
                )
        }
    }
