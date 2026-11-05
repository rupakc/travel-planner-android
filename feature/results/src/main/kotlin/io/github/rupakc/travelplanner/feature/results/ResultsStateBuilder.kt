package io.github.rupakc.travelplanner.feature.results

import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.plan.computePlanCost
import io.github.rupakc.travelplanner.core.domain.plan.getBudgetStatus
import io.github.rupakc.travelplanner.core.domain.results.BestValuePackage
import io.github.rupakc.travelplanner.core.domain.results.BudgetAllocation
import io.github.rupakc.travelplanner.core.domain.results.BudgetPlanner
import io.github.rupakc.travelplanner.core.domain.results.FareCalendarRules
import io.github.rupakc.travelplanner.core.domain.results.Remix
import io.github.rupakc.travelplanner.core.domain.results.RemixMetrics
import io.github.rupakc.travelplanner.core.domain.results.RemixPreset
import io.github.rupakc.travelplanner.core.domain.results.ResultsTextFilter
import io.github.rupakc.travelplanner.core.domain.results.SectionArrangement
import io.github.rupakc.travelplanner.core.domain.results.TripResults
import io.github.rupakc.travelplanner.core.domain.results.WatchSpecs
import io.github.rupakc.travelplanner.core.domain.results.clearPendingVerification
import io.github.rupakc.travelplanner.core.domain.results.orderSectionByCity
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.search.ActivityFilters
import io.github.rupakc.travelplanner.core.model.search.FlightFilters
import io.github.rupakc.travelplanner.core.model.search.HotelFilters
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.search.SectionResult
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.EventsData
import io.github.rupakc.travelplanner.core.model.section.ForexData
import io.github.rupakc.travelplanner.core.model.section.GettingAroundData
import io.github.rupakc.travelplanner.core.model.section.TipsData
import io.github.rupakc.travelplanner.core.model.section.VisaData
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.PlacesData
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.model.section.SimData
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import io.github.rupakc.travelplanner.feature.results.sections.ResultsSection
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

/** Per-search local state of the results screen (owned by the ViewModel). */
internal data class LocalState(
    /** The search these fields belong to; a new search resets them. */
    val request: TravelSearchRequest? = null,
    /** Section data replaced by a filter / single-section retry (web `setResults`). */
    val overrides: Map<SectionId, SectionResult> = emptyMap(),
    val loading: Set<SectionId> = emptySet(),
    val errors: Map<SectionId, FilterError> = emptyMap(),
    val flightFilters: FlightFilters? = null,
    val hotelFilters: HotelFilters? = null,
    val activityFilters: ActivityFilters? = null,
    /** User expand/collapse choices (default comes from the traveller type). */
    val expanded: Map<SectionId, Boolean> = emptyMap(),
    val query: String = "",
    val allocation: BudgetAllocation? = null,
    val budgetTouched: Boolean = false,
    val showOnlyFits: Boolean = false,
    val findingCheaperHotels: Boolean = false,
    val packageUndo: PlanDraft? = null,
    val newPlanUndo: PlanDraft? = null,
    val remix: Pair<RemixPreset, RemixMetrics>? = null,
    val fareCalendar: FareCalendarUi = FareCalendarUi(),
    val layover: LayoverUi? = null,
    val watches: WatchesUi = WatchesUi(),
    val noLivePrice: NoLivePriceUi? = null,
    val verificationFor: SectionId? = null,
    val selected: SectionId? = null,
) {
    /** Keeps what outlives a new search (filters text, expand choices, remix banner, undo). */
    fun forNewSearch(request: TravelSearchRequest?): LocalState =
        LocalState(request = request, query = query, expanded = expanded, remix = remix, newPlanUndo = newPlanUndo, watches = WatchesUi())
}

/**
 * Pure derivation of [ResultsUiState] from the stream, the plan draft and the local
 * state. Runs off the main thread; decodes only sections whose JSON changed.
 */
internal class ResultsStateBuilder {
    private val decoded = object : LinkedHashMap<JsonObject, SectionData>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<JsonObject, SectionData>?) = size > CACHE_SIZE
    }

    private fun decode(id: SectionId, raw: JsonObject, fallback: SectionData?): SectionData =
        synchronized(decoded) { decoded[raw] } ?: (fallback ?: SectionDecoder.decode(id, raw)).also { synchronized(decoded) { decoded[raw] = it } }

    /** Effective section data: overrides, journey order (not flights/weather, web parity), resolved pending chips. */
    fun effectiveSections(results: SearchResultsState, local: LocalState): Map<SectionId, Pair<SectionData, JsonObject>> {
        val merged = results.sections + local.overrides
        val destinations = results.request?.destinations
        val ordered =
            merged.mapValues { (id, r) ->
                if (id == SectionId.FLIGHTS || id == SectionId.WEATHER) r.raw else orderSectionByCity(r.raw, destinations)
            }
        val resolved = if (results.phase == StreamPhase.STREAMING) ordered else clearPendingVerification(ordered)
        return resolved.mapValues { (id, raw) ->
            val original = merged.getValue(id)
            decode(id, raw, original.data.takeIf { raw === original.raw }) to raw
        }
    }

    @Suppress("LongParameterList")
    fun build(
        results: SearchResultsState,
        fromSnapshot: Boolean,
        draft: PlanDraft,
        local: LocalState,
        health: Health?,
        visible: Set<FeatureId>?,
        today: LocalDate,
    ): ResultsUiState {
        val request = results.request
        val effective = effectiveSections(results, local)
        val done = results.phase == StreamPhase.DONE || fromSnapshot
        val trip = tripResults(effective)
        val query = local.query
        val sections =
            SectionArrangement.arrange(visible).map { arranged ->
                val id = arranged.id
                val entry = effective[id]
                val (data, count) = entry?.let { (d, raw) -> ResultsTextFilter.filter(d, raw, query) } ?: (null to Int.MAX_VALUE)
                val filtering = query.isNotBlank()
                SectionUi(
                    section = ResultsSection.of(id),
                    status = if (id in local.loading) SectionStatus.LOADING else results.statuses[id] ?: SectionStatus.WAITING,
                    data = data,
                    relevant = arranged.relevant,
                    expanded = local.expanded[id] ?: (arranged.relevant || (filtering && count > 0)),
                    matchCount = if (entry == null && filtering) 0 else count,
                    filterLoading = id in local.loading,
                    filterError = local.errors[id],
                    activeFilterCount = activeFilters(id, local),
                )
            }
        val visibleSections = if (query.isBlank()) sections else sections.filter { it.matchCount > 0 }
        val selections = draft.selections
        val total = computePlanCost(selections, request)
        return ResultsUiState(
            request = request,
            phase = if (fromSnapshot) StreamPhase.DONE else results.phase,
            error = results.error,
            fromSnapshot = fromSnapshot,
            canRunSavedSearch = results.phase == StreamPhase.IDLE && !fromSnapshot && draft.searchRequest != null,
            sections = visibleSections,
            readyCount = results.readyCount,
            confidence = results.confidence,
            pricingAdvisor = results.pricingAdvisor,
            query = query,
            hiddenByQuery = sections.size - visibleSections.size,
            selections = selections,
            plan = PlanSummary(selections.count, total, getBudgetStatus(total, request?.budgetUsd), savedPlanOpen = draft.planId != null),
            bestValue = if (done) bestValue(trip, request, draft, local) else null,
            budget = if (done) budget(trip, request, local) else null,
            remix = local.remix?.let { (preset, before) -> RemixUi(preset, if (done) Remix.diff(before, Remix.snapshot(trip)) else null) },
            fareCalendar = local.fareCalendar.copy(available = health?.serpapiConfigured == true && request != null && !request.isMultiCity),
            layover = local.layover,
            watches =
                local.watches.copy(
                    fareAvailable = health?.fareWatchAvailable == true && WatchSpecs.isWatchableTrip(request, today),
                    priceAvailable = health?.fareWatchAvailable == true,
                ),
            noLivePrice = local.noLivePrice,
            verificationDetail =
                local.verificationFor?.let { id ->
                    effective[id]?.first?.verification?.let { VerificationDetail(id, it) }
                },
            selectedSection = local.selected,
            sectionStatuses = sections.associate { it.id to it.status },
            daysUntilDeparture = daysUntil(request, today),
            flightFilters = local.flightFilters,
            hotelFilters = local.hotelFilters,
            activityFilters = local.activityFilters,
        )
    }

    private fun activeFilters(id: SectionId, local: LocalState): Int =
        when (id) {
            SectionId.FLIGHTS -> {
                local.flightFilters?.let { f ->
                    listOfNotNull(
                        f.maxStops,
                        f.maxPriceUsd,
                        (f.departureTimeEarliest ?: f.departureTimeLatest),
                        (f.arrivalTimeEarliest ?: f.arrivalTimeLatest),
                    ).size
                } ?: 0
            }

            SectionId.HOTELS -> {
                local.hotelFilters?.let { f ->
                    listOfNotNull(f.numBeds, f.maxPricePerNightUsd, f.wifiQuality, f.maxDistanceFromCenterKm, f.privateWashroom?.takeIf { it }).size
                } ?: 0
            }

            SectionId.ACTIVITIES -> {
                local.activityFilters?.let { f ->
                    listOfNotNull(
                        f.filterInterests?.takeIf { it.isNotEmpty() },
                        f.maxPriceUsd,
                        (f.availableFrom ?: f.availableTo),
                        f.minRating,
                    ).size
                } ?: 0
            }

            else -> {
                0
            }
        }

    private fun bestValue(trip: TripResults, request: TravelSearchRequest?, draft: PlanDraft, local: LocalState): BestValueUi? {
        val caps =
            if (local.budgetTouched) {
                budgetAllocation(trip, request, local)?.let { BudgetPlanner.capsFromFit(BudgetPlanner.fitResults(trip, it, request)) }
            } else {
                null
            }
        val pkg = BestValuePackage.build(trip, request, caps) ?: return null
        val applied = BestValuePackage.isApplied(draft.selections, pkg)
        val planTotal = if (applied) computePlanCost(draft.selections, request) else null
        return BestValueUi(
            pkg = pkg,
            applied = applied,
            conflicts = if (applied) emptyList() else BestValuePackage.conflicts(draft.selections, pkg),
            canUndo = local.packageUndo != null,
            disabled = draft.planId != null,
            planTotal = planTotal?.takeIf { Math.round(it) != Math.round(pkg.total) },
        )
    }

    private fun budgetAllocation(trip: TripResults, request: TravelSearchRequest?, local: LocalState): BudgetAllocation? =
        local.allocation ?: BudgetPlanner.defaultAllocation(request, trip)

    private fun budget(trip: TripResults, request: TravelSearchRequest?, local: LocalState): BudgetUi? {
        val allocation = budgetAllocation(trip, request, local) ?: return null
        return BudgetUi(
            allocation = allocation,
            fit = BudgetPlanner.fitResults(trip, allocation, request),
            showOnlyFits = local.showOnlyFits,
            cardsReact = local.budgetTouched || local.showOnlyFits,
            findingCheaperHotels = local.findingCheaperHotels,
        )
    }

    companion object {
        private const val CACHE_SIZE = 64
        private const val LOAD_FACTOR = 0.75f

        /** The typed sections the pure utilities read. */
        fun tripResults(effective: Map<SectionId, Pair<SectionData, JsonObject>>): TripResults =
            TripResults(
                flights = effective[SectionId.FLIGHTS]?.first as? FlightsData,
                hotels = effective[SectionId.HOTELS]?.first as? HotelsData,
                activities = effective[SectionId.ACTIVITIES]?.first as? ActivitiesData,
                walkingTours = effective[SectionId.WALKING_TOURS]?.first as? WalkingToursData,
                placesToSee = effective[SectionId.PLACES_TO_SEE]?.first as? PlacesData,
                sim = effective[SectionId.SIM]?.first as? SimData,
                weather = effective[SectionId.WEATHER]?.first as? WeatherData,
                itinerary = effective[SectionId.ITINERARY]?.first as? ItineraryData,
            )

        /** Results restored from the plan draft's `searchData` (no re-stream after process death / for a saved plan). */
        fun snapshot(draft: PlanDraft): SearchResultsState? {
            val data = draft.searchData?.takeIf { it.isNotEmpty() } ?: return null
            val request = draft.searchRequest ?: return null
            val sections =
                data.mapNotNull { (key, value) ->
                    val id = SectionId.fromWire(key) ?: return@mapNotNull null
                    val raw = value as? JsonObject ?: return@mapNotNull null
                    id to SectionResult(id, SectionDecoder.decode(id, raw), raw, null)
                }.toMap()
            if (sections.isEmpty()) return null
            return SearchResultsState(
                request = request,
                sections = sections,
                statuses = SectionId.entries.associateWith { SectionStatus.DONE },
                phase = StreamPhase.DONE,
            )
        }

        /** JSON of a typed section (items keep their original JSON via RawBacked). */
        @Suppress("CyclomaticComplexMethod")
        fun encode(data: SectionData): JsonObject {
            val json =
                when (data) {
                    is FlightsData -> TravelJson.encodeToJsonElement(FlightsData.serializer(), data)
                    is HotelsData -> TravelJson.encodeToJsonElement(HotelsData.serializer(), data)
                    is ActivitiesData -> TravelJson.encodeToJsonElement(ActivitiesData.serializer(), data)
                    is PlacesData -> TravelJson.encodeToJsonElement(PlacesData.serializer(), data)
                    is WalkingToursData -> TravelJson.encodeToJsonElement(WalkingToursData.serializer(), data)
                    is EventsData -> TravelJson.encodeToJsonElement(EventsData.serializer(), data)
                    is VisaData -> TravelJson.encodeToJsonElement(VisaData.serializer(), data)
                    is SimData -> TravelJson.encodeToJsonElement(SimData.serializer(), data)
                    is TipsData -> TravelJson.encodeToJsonElement(TipsData.serializer(), data)
                    is GettingAroundData -> TravelJson.encodeToJsonElement(GettingAroundData.serializer(), data)
                    is ForexData -> TravelJson.encodeToJsonElement(ForexData.serializer(), data)
                    is ItineraryData -> TravelJson.encodeToJsonElement(ItineraryData.serializer(), data)
                    else -> null
                }
            return json as? JsonObject ?: JsonObject(emptyMap())
        }

        /** Days helper for the fare calendar's "Now" marker. */
        fun daysUntil(request: TravelSearchRequest?, today: LocalDate): Long = FareCalendarRules.daysUntil(request?.departureDate, today)
    }
}
