package io.github.rupakc.travelplanner.feature.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.profile.FeatureVisibility
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.FareCalendarRepository
import io.github.rupakc.travelplanner.core.data.repository.FareWatchesRepository
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.PriceWatchesRepository
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.data.search.SearchLauncher
import io.github.rupakc.travelplanner.core.domain.alerts.FareWatchDialogController
import io.github.rupakc.travelplanner.core.domain.alerts.FareWatchDialogState
import io.github.rupakc.travelplanner.core.domain.alerts.PricePreview
import io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogController
import io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogState
import io.github.rupakc.travelplanner.core.domain.alerts.WatchTrip
import io.github.rupakc.travelplanner.core.domain.alerts.WatchedOutcome
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.results.BestValuePackage
import io.github.rupakc.travelplanner.core.domain.results.BudgetAllocation
import io.github.rupakc.travelplanner.core.domain.results.BudgetPlanner
import io.github.rupakc.travelplanner.core.domain.results.FareCalendarRules
import io.github.rupakc.travelplanner.core.domain.results.Remix
import io.github.rupakc.travelplanner.core.domain.results.RemixPreset
import io.github.rupakc.travelplanner.core.domain.results.WatchSpecs
import io.github.rupakc.travelplanner.core.model.alerts.ALERT_ANY_DROP
import io.github.rupakc.travelplanner.core.model.alerts.ActivityWatchSpec
import io.github.rupakc.travelplanner.core.model.alerts.CreatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.HotelWatchSpec
import io.github.rupakc.travelplanner.core.model.alerts.NoLivePriceError
import io.github.rupakc.travelplanner.core.model.alerts.PriceSuggestion
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatchKinds
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.HotelFilters
import io.github.rupakc.travelplanner.core.model.search.LayoverRequest
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.search.SectionResult
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.StressTestPayload
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.search.searchKey
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
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.core.ui.watch.WatchSheetEvent
import io.github.rupakc.travelplanner.core.ui.watch.applyTo
import io.github.rupakc.travelplanner.feature.results.sections.info.StressRecheckController
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
        private val searchLauncher: SearchLauncher,
        private val plan: CurrentPlanStore,
        private val health: HealthRepository,
        private val fareCalendar: FareCalendarRepository,
        private val fareWatches: FareWatchesRepository,
        private val priceWatches: PriceWatchesRepository,
        featureVisibility: FeatureVisibility,
        private val auth: AuthRepository,
        private val clock: AppClock,
        private val dispatchers: AppDispatchers,
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

        /** The shared visibility rule (core:data): hidden features' sections drop down the order, as on Home and Search. */
        private val visibleFeatures: Flow<Set<FeatureId>?> = featureVisibility.visible()

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

        // ── Team E's SectionActions members (info sections, itinerary editor, health re-check) ──

        /** The draft's selections, observed (itinerary layout/edits/notes/slots, packing list). */
        val selections: StateFlow<PlanSelections> =
            plan.draft.map { it.selections }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, plan.draft.value.selections)

        /** The results on screen, by the same source rule as [state] (live stream, draft snapshot, …). */
        val results: StateFlow<SearchResultsState> =
            source.map { it.results }.stateIn(viewModelScope, SharingStarted.Eagerly, sourceOf(search.current.value, plan.draft.value).results)

        /**
         * Read-only itinerary while a saved plan is open (web `loadedPlanId`, ResultsPage.jsx:3482): set only by
         * `CurrentPlanStore.load`, never by saving, cleared by a new search or New plan.
         */
        val itineraryEditingLocked: Boolean get() = plan.draft.value.loadedPlan

        /** Retained Trip Health Check re-check (survives scrolling the card away and rotation). */
        val stressRecheck = StressRecheckController(viewModelScope) { search.stressTest(it) }

        /** Synchronous read-modify-write of the draft's selections, applied exactly once. */
        fun updateSelections(transform: (PlanSelections) -> PlanSelections) {
            plan.setSelections(transform(plan.draft.value.selections))
        }

        suspend fun recheckStressTest(payload: StressTestPayload): AppResult<StressTestData> = search.stressTest(payload)

        /** Web `clearRecheck`: a new search, opening a saved plan and clearing the selections drop the re-check. */
        private suspend fun clearRecheckOnPlanChange() {
            plan.draft
                .map { Triple(it.searchKey, it.planId.takeIf { _ -> it.loadedPlan }, it.selections.isEmpty) }
                .distinctUntilChanged()
                .drop(1)
                .collect { stressRecheck.clear() }
        }

        init {
            viewModelScope.launch { if (health.capabilities.value == null) health.refresh() }
            viewModelScope.launch { clearRecheckOnPlanChange() }
            viewModelScope.launch { resetOnNewSearch() }
            viewModelScope.launch(dispatchers.default) { attachToDraft() }
            viewModelScope.launch { rememberExtras() }
            viewModelScope.launch { resolvePlaceNames() }
        }

        /**
         * The search form stores airport codes ("LIS"); the title and summary show the city ("Lisbon"), looked up
         * like the web's `useAirportCities` (local airport data). A failed lookup keeps the code: nothing is invented.
         */
        private suspend fun resolvePlaceNames() {
            source.map { it.results.request }.distinctUntilChanged().collect { req ->
                req ?: return@collect
                val codes = (listOf(req.origin, req.destination) + req.destinations.orEmpty()).filter(WatchSpecs::isAirportCode)
                val missing = codes.map(String::trim).distinct().filter { it !in local.value.placeNames }
                if (missing.isEmpty()) return@collect
                val found = missing.mapNotNull { code -> cityName(code)?.takeIf { it != code }?.let { code to it } }.toMap()
                if (found.isNotEmpty()) local.update { it.copy(placeNames = it.placeNames + found) }
            }
        }

        /** A different search on screen: cancel its filter/retry jobs and reset the per-search state. */
        private suspend fun resetOnNewSearch() {
            source.map { it.results.request?.searchKey() }.distinctUntilChanged().collect { key ->
                if (key != local.value.key) {
                    jobs.values.forEach { it.cancel() }
                    jobs.clear()
                    local.update { it.forNewSearch(key) }
                }
            }
        }

        /**
         * Keeps the draft's `searchData` = the draft's own live search (plan snapshot, chat context, process
         * death). A stream for another trip is never written. Every Results screen computes the same target
         * from the shared state (filter/retry results live in SearchRepository), and "already written" is
         * checked per section by reference, so several screens converge after one write instead of
         * ping-ponging (architecture review I1) and nothing deep-compares the snapshot (performance review I3).
         * Runs on the default dispatcher.
         */
        private suspend fun attachToDraft() {
            combine(search.current, plan.draft) { live, draft -> live to draft }
                .collect { (live, draft) ->
                    if (ResultsStateBuilder.selectSource(live, draft) != ResultsSource.LIVE || live.phase == StreamPhase.FAILED) return@collect
                    val request = live.request ?: return@collect
                    if (draft.searchRequest == request && draft.searchData.holdsSameSections(live)) return@collect
                    plan.attachSearch(request, live.sectionsJson())
                }
        }

        private fun JsonObject?.holdsSameSections(live: SearchResultsState): Boolean =
            this != null && size == live.sections.size && live.sections.all { (id, r) -> this[id.wire] === r.raw }

        /** Travel Ease and the pricing advisor are not sections; keep them across process death (M1). */
        private suspend fun rememberExtras() {
            search.current.map {
                Triple(
                    it.request?.searchKey(),
                    it.confidence,
                    it.pricingAdvisor,
                )
            }.distinctUntilChanged().collect { (key, confidence, advisor) ->
                if (key == null || key != plan.draft.value.searchRequest?.searchKey()) return@collect
                savedState[KEY_EXTRAS_SEARCH] = key
                savedState[KEY_CONFIDENCE] = confidence?.let { TravelJson.encodeToString(ConfidenceData.serializer(), it) }
                savedState[KEY_ADVISOR] = advisor?.let { TravelJson.encodeToString(PricingAdvisorData.serializer(), it) }
            }
        }

        private fun withSavedExtras(snapshot: SearchResultsState): SearchResultsState {
            if (savedState.get<String>(KEY_EXTRAS_SEARCH) != snapshot.request?.searchKey()) return snapshot
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
            // The new plan is for the new search, so its stream is the one shown and stored.
            searchLauncher.startNewSearch(next)
            local.update { it.copy(newPlanUndo = previous.takeUnless { p -> p.selections.isEmpty }) }
            if (!previous.selections.isEmpty) messageChannel.trySend(ResultsMessage.NewPlanStarted)
        }

        private fun remix(preset: RemixPreset) {
            val req = request ?: return
            val remixed = Remix.apply(req, preset) ?: return
            val before = Remix.snapshot(ResultsStateBuilder.tripResults(builder.effectiveSections(currentResults())))
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
            val key = req.searchKey()
            jobs.remove(id)?.cancel()
            local.update { it.copy(loading = it.loading + id, errors = it.errors - id) }
            jobs[id] =
                viewModelScope.launch {
                    val result = call(req)
                    if (local.value.key != key) return@launch
                    when (result) {
                        is AppResult.Success -> {
                            val (data, raw) = result.value
                            publish(key, SectionResult(id, data, raw, null))
                            local.update { if (it.key != key) it else it.copy(loading = it.loading - id, findingCheaperHotels = false) }
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

        /**
         * One writer per source (architecture review I1): the live search's result goes to SearchRepository,
         * a saved plan's snapshot is updated once in the draft. Either way every Results screen sees it.
         */
        private fun publish(key: String, result: SectionResult) {
            val live = search.current.value
            val draft = plan.draft.value
            when (ResultsStateBuilder.selectSource(live, draft)) {
                ResultsSource.LIVE -> {
                    search.applySectionResult(key, result)
                }

                ResultsSource.SNAPSHOT -> {
                    val request = draft.searchRequest?.takeIf { it.searchKey() == key } ?: return
                    plan.attachSearch(request, JsonObject(draft.searchData.orEmpty() + (result.id.wire to result.raw)))
                }

                else -> {
                    Unit
                }
            }
        }

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

        // ── watches: Team G's sheets over their dialog controllers (web FareWatchDialog / PriceWatchDialog) ──

        private val sheet = MutableStateFlow<WatchSheet?>(null)

        /** The watch sheet to show, if any (the route hosts FareWatchSheet / PriceWatchSheet). */
        val watchSheet: StateFlow<WatchSheet?> = sheet

        /** The signed-in user's email (the sheets offer email alerts only with one). */
        val userEmail: StateFlow<String?> =
            auth.session.map { it?.user?.email }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun dismissWatchSheet() {
            sheet.value = null
            local.update { l ->
                // A sheet closed without watching leaves no "busy" marker behind.
                l.copy(
                    watches =
                        l.watches.copy(
                            fare = l.watches.fare?.takeIf { it.watchId != null },
                            hotels = l.watches.hotels.filterValues { it.watchId != null },
                            activities = l.watches.activities.filterValues { it.watchId != null },
                        ),
                )
            }
        }

        /** A fare watch was created from the sheet: mark the flights section "Watching". */
        fun onFareWatched(watch: FareWatch) {
            local.update {
                it.copy(watches = it.watches.copy(fare = WatchState(watchId = watch.id, price = watch.lastPrice ?: watch.baselinePrice, currency = "USD")))
            }
        }

        /**
         * Events from the open price-watch sheet. Requests run in [viewModelScope] (the sheet only renders), so a
         * recomposition or rotation mid-request cannot drop the answer — a 422 `no_live_price` keeps its
         * suggestions — and the controller sends at most one request at a time however often the button is tapped.
         */
        fun onPriceSheetEvent(event: WatchSheetEvent) {
            val current = sheet.value as? WatchSheet.Price ?: return
            event.applyTo(current.controller, viewModelScope) { onPriceWatched(current.kind, current.itemName, it) }
        }

        /** A price watch was created; only the card's own item is marked (not a suggestion picked instead). */
        fun onPriceWatched(kind: String, itemName: String, outcome: WatchedOutcome) {
            if (outcome.viaSuggestion) return
            val w = outcome.watch
            val state = WatchState(watchId = w.id, price = w.baselinePrice ?: w.lastPrice, currency = w.currency)
            if (kind == PriceWatchKinds.HOTEL) setHotelWatch(itemName, state) else setActivityWatch(itemName, state)
        }

        private fun watchFare() {
            val req = request ?: return
            val trip = WatchTrip.from(req)
            if (trip == null || !WatchSpecs.isWatchableTrip(req, clock.today())) {
                messageChannel.trySend(ResultsMessage.WatchUnavailable)
                return
            }
            val shown = state.value.sections.firstOrNull { it.id == SectionId.FLIGHTS }?.data as? FlightsData
            val initial =
                FareWatchDialogState(
                    trip = trip,
                    price = WatchSpecs.cheapestFlightPrice(shown),
                    emailAvailable = health.capabilities.value?.emailAlertsAvailable == true,
                )
            sheet.value = WatchSheet.Fare(FareWatchDialogController(initial) { fareWatches.create(it) })
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
                // The estimate the sheet labels "AI estimate for 1 room, whole stay" is the spec's whole-stay total (web
                // `hotelPreview`), not the card's nightly price.
                openPriceSheet(PriceWatchKinds.HOTEL, name, body, PricePreview(title = name, estimate = spec.estimateTotal))
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
                openPriceSheet(PriceWatchKinds.ACTIVITY, name, body, PricePreview(title = name, subtitle = activity.city, estimate = activity.priceUsd))
            }
        }

        private fun openPriceSheet(kind: String, itemName: String, spec: JsonObject, preview: PricePreview) {
            val initial = PriceWatchDialogState(kind, spec, preview, emailAvailable = health.capabilities.value?.emailAlertsAvailable == true)
            val controller = PriceWatchDialogController(initial, { priceWatches.create(it) }, { priceWatches.delete(it) })
            sheet.value = WatchSheet.Price(controller, kind, itemName)
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
