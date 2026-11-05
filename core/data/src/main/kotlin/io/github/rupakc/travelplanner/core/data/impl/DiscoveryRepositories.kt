package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppException
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.repository.CompareRepository
import io.github.rupakc.travelplanner.core.data.repository.DiscoverRepository
import io.github.rupakc.travelplanner.core.data.repository.FareCalendarRepository
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.ImportRepository
import io.github.rupakc.travelplanner.core.data.repository.NearbyRepository
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreview
import io.github.rupakc.travelplanner.core.model.importlink.ImportRequest
import io.github.rupakc.travelplanner.core.model.importlink.ResolvePlaceRequest
import io.github.rupakc.travelplanner.core.model.importlink.ResolvedPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbyRequest
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.core.model.search.DiscoveryRequest
import io.github.rupakc.travelplanner.core.model.search.DiscoveryResponse
import io.github.rupakc.travelplanner.core.model.search.FareCalendar
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.network.api.HealthApi
import io.github.rupakc.travelplanner.core.network.api.ImportApi
import io.github.rupakc.travelplanner.core.network.api.NearbyApi
import io.github.rupakc.travelplanner.core.network.api.SearchApi
import io.github.rupakc.travelplanner.core.network.HttpErrorMapper
import io.github.rupakc.travelplanner.core.network.apiCall
import io.github.rupakc.travelplanner.core.network.di.DefaultHttp
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CompareRepositoryImpl
    @Inject
    constructor(private val api: SearchApi) : CompareRepository {
        override suspend fun compare(request: TravelSearchRequest): AppResult<CompareSummary> = apiCall { api.compareDestination(request) }
    }

@Singleton
class DiscoverRepositoryImpl
    @Inject
    constructor(private val api: SearchApi) : DiscoverRepository {
        override suspend fun discover(request: DiscoveryRequest): AppResult<DiscoveryResponse> = apiCall { api.discover(request) }
    }

@Singleton
class ImportRepositoryImpl
    @Inject
    constructor(private val api: ImportApi) : ImportRepository {
        override suspend fun extract(request: ImportRequest): AppResult<ImportPreview> = apiCall { api.extract(request) }

        override suspend fun resolvePlace(request: ResolvePlaceRequest): AppResult<ResolvedPlace> = apiCall { api.resolvePlace(request) }
    }

@Singleton
class FareCalendarRepositoryImpl
    @Inject
    constructor(private val api: SearchApi) : FareCalendarRepository {
        override suspend fun fareCalendar(request: TravelSearchRequest): AppResult<FareCalendar> = apiCall { api.fareCalendar(request) }
    }

@Singleton
class HealthRepositoryImpl
    @Inject
    constructor(
        private val api: HealthApi,
        @param:DefaultHttp private val client: OkHttpClient,
        private val dispatchers: AppDispatchers,
    ) : HealthRepository {
        private val state = MutableStateFlow<Health?>(null)
        override val capabilities: StateFlow<Health?> = state.asStateFlow()

        /** Same timeouts and pool, but without the base-URL and auth interceptors: never sends the token elsewhere. */
        private val bareClient: OkHttpClient by lazy {
            client.newBuilder().apply {
                interceptors().clear()
                networkInterceptors().clear()
            }.build()
        }

        override suspend fun refresh(): AppResult<Health> = apiCall { api.health() }.onSuccess { state.value = it }

        override suspend fun probe(baseUrl: String): AppResult<Health> {
            val url =
                baseUrl.trim().trimEnd('/').let { "$it/health" }.toHttpUrlOrNull()
                    ?: return AppResult.Failure(AppError.Client(status = 0, message = null, code = CODE_INVALID_URL))
            return apiCall {
                withContext(dispatchers.io) {
                    bareClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                        val body = response.body.string()
                        if (!response.isSuccessful) throw AppException(HttpErrorMapper.map(response.code, response.header("Retry-After"), body))
                        TravelJson.decodeFromString(Health.serializer(), body)
                    }
                }
            }
        }

        private companion object {
            const val CODE_INVALID_URL = "invalid_url"
        }
    }

/** In-memory cache: 1 h per ~5 km cell (0.05° grid), keyed with interests and limit. */
@Singleton
class NearbyRepositoryImpl
    @Inject
    constructor(
        private val api: NearbyApi,
        private val clock: AppClock,
    ) : NearbyRepository {
        private data class Key(val latCell: Long, val lngCell: Long, val interests: List<String>, val limit: Int)

        private data class Entry(val at: Instant, val value: NearbyResponse)

        private val cache = mutableMapOf<Key, Entry>()
        private val mutex = Mutex()

        override suspend fun nearby(lat: Double, lng: Double, interests: List<String>?, limit: Int): AppResult<NearbyResponse> {
            val key = Key(cell(lat), cell(lng), interests.orEmpty().sorted(), limit)
            mutex.withLock {
                cache[key]?.takeIf { Duration.between(it.at, clock.now()) < TTL }?.let { return AppResult.Success(it.value) }
            }
            return apiCall { api.nearby(NearbyRequest(lat, lng, interests, limit)) }
                .onSuccess { mutex.withLock { cache[key] = Entry(clock.now(), it) } }
        }

        private fun cell(degrees: Double): Long = floor(degrees / CELL_DEGREES).toLong()

        private companion object {
            const val CELL_DEGREES = 0.05
            val TTL: Duration = Duration.ofHours(1)
        }
    }
