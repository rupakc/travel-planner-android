package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppException
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import io.github.rupakc.travelplanner.core.model.search.ActivityFilters
import io.github.rupakc.travelplanner.core.model.search.FlightFilters
import io.github.rupakc.travelplanner.core.model.search.HotelFilters
import io.github.rupakc.travelplanner.core.model.search.LayoverPlan
import io.github.rupakc.travelplanner.core.model.search.LayoverRequest
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.StressTestPayload
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import io.github.rupakc.travelplanner.core.network.api.ReferenceApi
import io.github.rupakc.travelplanner.core.network.api.SearchApi
import io.github.rupakc.travelplanner.core.network.apiCall
import io.github.rupakc.travelplanner.core.network.sse.SearchStreamClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Singleton
class SearchRepositoryImpl
    @Inject
    constructor(
        private val streamClient: SearchStreamClient,
        private val searchApi: SearchApi,
        private val referenceApi: ReferenceApi,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) : SearchRepository {
        private val state = MutableStateFlow(SearchResultsState())
        override val current: StateFlow<SearchResultsState> = state.asStateFlow()
        private var job: Job? = null

        override fun start(request: TravelSearchRequest) {
            job?.cancel()
            state.value = SearchResultsState(request = request, phase = StreamPhase.STREAMING)
            job = appScope.launch { results(request).collect { state.value = it } }
        }

        override fun cancel() {
            job?.cancel()
            job = null
            if (state.value.phase == StreamPhase.STREAMING) state.value = state.value.copy(phase = StreamPhase.CANCELLED)
        }

        override fun clear() {
            job?.cancel()
            job = null
            state.value = SearchResultsState()
        }

        override fun results(request: TravelSearchRequest): Flow<SearchResultsState> =
            flow {
                var folded = SearchResultsState(request = request, phase = StreamPhase.STREAMING)
                emit(folded)
                var attempt = 0
                while (true) {
                    var failure: AppError? = null
                    streamClient.stream(request)
                        .catch { e -> if (e is AppException) failure = e.error else throw e }
                        .collect { event ->
                            folded = SearchResultsReducer.reduce(folded, event)
                            emit(folded)
                        }
                    val error = failure
                    when {
                        error == null -> {
                            if (folded.phase != StreamPhase.DONE) folded = SearchResultsReducer.finish(folded, StreamPhase.DONE)
                            emit(folded)
                            return@flow
                        }

                        error is AppError.Interrupted && attempt < MAX_RETRIES -> {
                            attempt++
                            folded = folded.copy(retries = attempt)
                            emit(folded)
                        }

                        error is AppError.Interrupted -> {
                            emit(SearchResultsReducer.finish(folded, StreamPhase.INTERRUPTED))
                            return@flow
                        }

                        else -> {
                            emit(folded.copy(phase = StreamPhase.FAILED, error = error))
                            return@flow
                        }
                    }
                }
            }

        override fun events(request: TravelSearchRequest): Flow<SearchEvent> =
            flow {
                var attempt = 0
                while (true) {
                    var failure: AppException? = null
                    streamClient.stream(request)
                        .catch { e -> if (e is AppException) failure = e else throw e }
                        .collect { emit(it) }
                    val error = failure ?: return@flow
                    if (error.error is AppError.Interrupted && attempt < MAX_RETRIES) {
                        attempt++
                    } else {
                        throw error
                    }
                }
            }

        override suspend fun section(section: SectionId, request: TravelSearchRequest): AppResult<SectionData> {
            val call: suspend (TravelSearchRequest) -> JsonObject =
                when (section) {
                    SectionId.FLIGHTS -> searchApi::flights
                    SectionId.HOTELS -> searchApi::hotels
                    SectionId.ACTIVITIES -> searchApi::activities
                    SectionId.VISA -> searchApi::visa
                    SectionId.SIM -> searchApi::sim
                    SectionId.TIPS -> searchApi::tips
                    SectionId.GETTING_AROUND -> searchApi::gettingAround
                    SectionId.FOREX -> searchApi::forex
                    SectionId.ITINERARY -> searchApi::itinerary
                    SectionId.EVENTS -> searchApi::events
                    SectionId.WALKING_TOURS -> searchApi::walkingTours
                    else -> return AppResult.Failure(AppError.Unknown("No single-agent route for ${section.wire}"))
                }
            return apiCall { call(request) }.map { SectionDecoder.decode(section, it) }
        }

        override suspend fun flightsFiltered(request: TravelSearchRequest, filters: FlightFilters): AppResult<FlightsData> =
            apiCall { searchApi.flightsFiltered(merge(request, filters, FlightFilters.serializer())) }
                .map { SectionDecoder.decode(SectionId.FLIGHTS, it) as? FlightsData ?: FlightsData(error = "") }

        override suspend fun hotelsFiltered(request: TravelSearchRequest, filters: HotelFilters): AppResult<HotelsData> =
            apiCall { searchApi.hotelsFiltered(merge(request, filters, HotelFilters.serializer())) }
                .map { SectionDecoder.decode(SectionId.HOTELS, it) as? HotelsData ?: HotelsData(error = "") }

        override suspend fun activitiesFiltered(request: TravelSearchRequest, filters: ActivityFilters): AppResult<ActivitiesData> =
            apiCall { searchApi.activitiesFiltered(merge(request, filters, ActivityFilters.serializer())) }
                .map { SectionDecoder.decode(SectionId.ACTIVITIES, it) as? ActivitiesData ?: ActivitiesData(error = "") }

        override suspend fun stressTest(payload: StressTestPayload): AppResult<StressTestData> {
            val body =
                buildJsonObject {
                    (TravelJson.encodeToJsonElement(TravelSearchRequest.serializer(), payload.request) as JsonObject).forEach { (k, v) ->
                        put(k, v)
                    }
                    put("itinerary", payload.itinerary)
                    payload.flights?.let { put("flights", it) }
                    payload.visa?.let { put("visa", it) }
                    payload.weather?.let { put("weather", it) }
                }
            return apiCall { searchApi.stressTest(body) }
                .map { SectionDecoder.decode(SectionId.STRESS_TEST, it) as? StressTestData ?: StressTestData(error = "") }
        }

        override suspend fun layover(request: LayoverRequest): AppResult<LayoverPlan> = apiCall { searchApi.layover(request) }

        override suspend fun searchSync(request: TravelSearchRequest): AppResult<JsonObject> = apiCall { searchApi.searchSync(request) }

        override suspend fun airports(query: String, limit: Int): AppResult<List<Airport>> = apiCall { referenceApi.airports(query, limit) }

        override suspend fun nationalities(query: String, limit: Int): AppResult<List<Nationality>> =
            apiCall { referenceApi.nationalities(query, limit) }

        private fun <F> merge(request: TravelSearchRequest, filters: F, serializer: KSerializer<F>): JsonObject =
            buildJsonObject {
                (TravelJson.encodeToJsonElement(TravelSearchRequest.serializer(), request) as JsonObject).forEach { (k, v) -> put(k, v) }
                (TravelJson.encodeToJsonElement(serializer, filters) as JsonObject).forEach { (k, v) -> put(k, v) }
            }

        companion object {
            /** Web parity: one automatic retry when the stream ends without `done`. */
            const val MAX_RETRIES = 1
        }
    }
