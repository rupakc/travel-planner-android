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
import io.github.rupakc.travelplanner.core.data.repository.LiveCheckRepository
import io.github.rupakc.travelplanner.core.data.repository.NearbyRepository
import io.github.rupakc.travelplanner.core.datastore.LastLocationStore
import io.github.rupakc.travelplanner.core.model.alerts.LiveCheckRequest
import io.github.rupakc.travelplanner.core.model.alerts.LiveCheckResult
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreview
import io.github.rupakc.travelplanner.core.model.importlink.ImportRequest
import io.github.rupakc.travelplanner.core.model.importlink.ResolvePlaceRequest
import io.github.rupakc.travelplanner.core.model.importlink.ResolvedPlace
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.nearby.LocationCell
import io.github.rupakc.travelplanner.core.model.nearby.NearbyRequest
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.core.model.search.DiscoveryRequest
import io.github.rupakc.travelplanner.core.model.search.DiscoveryResponse
import io.github.rupakc.travelplanner.core.model.search.FareCalendar
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.HttpErrorMapper
import io.github.rupakc.travelplanner.core.network.api.HealthApi
import io.github.rupakc.travelplanner.core.network.api.ImportApi
import io.github.rupakc.travelplanner.core.network.api.LiveCheckApi
import io.github.rupakc.travelplanner.core.network.api.NearbyApi
import io.github.rupakc.travelplanner.core.network.api.SearchApi
import io.github.rupakc.travelplanner.core.network.apiCall
import io.github.rupakc.travelplanner.core.network.di.DefaultHttp
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
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

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

/**
 * `/health` capabilities, persisted per server with a [TTL] (performance review I1): [restore] seeds a fresh
 * process from the last answer without a call, and [refreshIfStale] calls `/health` only when that answer is
 * missing or older than [TTL]. A server change makes the old answer unknown, never applied to the new server.
 */
@Singleton
class HealthRepositoryImpl
    @Inject
    constructor(
        private val api: HealthApi,
        @param:DefaultHttp private val client: OkHttpClient,
        private val dispatchers: AppDispatchers,
        private val baseUrlProvider: BaseUrlProvider,
        private val cache: HealthCacheStore,
        private val clock: AppClock,
    ) : HealthRepository {
        private val state = MutableStateFlow<Health?>(null)
        override val capabilities: StateFlow<Health?> = state.asStateFlow()

        /** Which server [state] belongs to and when it was fetched; guarded by [mutex]. */
        private var known: CachedHealth? = null
        private val mutex = Mutex()

        /** Same timeouts and pool, but without the base-URL and auth interceptors: never sends the token elsewhere. */
        private val bareClient: OkHttpClient by lazy {
            client.newBuilder().apply {
                interceptors().clear()
                networkInterceptors().clear()
            }.build()
        }

        override suspend fun refresh(): AppResult<Health> {
            val server = currentServer()
            return apiCall { api.health() }.onSuccess { health ->
                if (server == null) {
                    state.value = health
                    return@onSuccess
                }
                val entry = CachedHealth(server, health, clock.now().toEpochMilli())
                mutex.withLock {
                    known = entry
                    state.value = health
                }
                withContext(dispatchers.io) { cache.write(entry) }
            }
        }

        override suspend fun restore() {
            val server = currentServer() ?: return
            mutex.withLock {
                if (known?.server == server) return
                val stored = withContext(dispatchers.io) { cache.read() }?.takeIf { it.server == server }
                known = stored
                // Another server's capabilities must not stand for this one: unknown until fetched.
                state.value = stored?.health
            }
        }

        override suspend fun refreshIfStale(): Health? {
            restore()
            val server = currentServer()
            val entry = mutex.withLock { known }
            val fresh = entry != null && entry.server == server && Duration.between(Instant.ofEpochMilli(entry.savedAtMillis), clock.now()) < TTL
            if (fresh) return state.value
            return when (val result = refresh()) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> state.value
            }
        }

        private suspend fun currentServer(): String? = baseUrlProvider.current()?.toString()?.trimEnd('/')

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

        companion object {
            /** How long a `/health` answer is trusted before [refreshIfStale] asks again. */
            val TTL: Duration = Duration.ofHours(6)
            private const val CODE_INVALID_URL = "invalid_url"
        }
    }

/**
 * Near me v2. No cache at all (Google Maps grounding terms): every call asks the server and the
 * screen's ViewModel is the only holder of the answer. Only the coarse ~5 km cell of the last
 * foreground fix is kept (DataStore), for the background invitation's gate.
 */
@Singleton
class NearbyRepositoryImpl
    @Inject
    constructor(
        private val api: NearbyApi,
        private val clock: AppClock,
        private val lastLocationStore: LastLocationStore,
    ) : NearbyRepository {
        override suspend fun nearby(lat: Double, lng: Double, limit: Int, language: String?): AppResult<NearbyResponse> =
            apiCall { api.nearby(NearbyRequest(lat = roundCoordinate(lat), lng = roundCoordinate(lng), limit = limit, sections = true, language = language)) }

        override suspend fun rememberLocation(lat: Double, lng: Double) {
            // The cell's centre: precise coordinates are never stored.
            fun centre(d: Double) = (floor(d / CELL_DEGREES) + HALF) * CELL_DEGREES
            lastLocationStore.set(LocationCell(centre(lat), centre(lng), clock.now().toEpochMilli()))
        }

        override suspend fun lastLocation() = lastLocationStore.get()

        override suspend fun forgetLocation() = lastLocationStore.clear()

        private companion object {
            const val HALF = 0.5
            const val CELL_DEGREES = 0.05
        }
    }

/**
 * Coordinates sent for Near me are rounded to 3 decimals (~100 m), as the web does: they travel via the backend
 * to Google Maps grounding, which needs no more precision to find nearby places.
 */
internal fun roundCoordinate(degrees: Double): Double = Math.round(degrees * COORD_SCALE) / COORD_SCALE

private const val COORD_SCALE = 1000.0

/** `POST /api/live-check`: passes the answer straight through; nothing is cached or stored. */
@Singleton
class LiveCheckRepositoryImpl
    @Inject
    constructor(private val api: LiveCheckApi) : LiveCheckRepository {
        override suspend fun check(kind: String, watchId: Long): AppResult<LiveCheckResult> = apiCall { api.check(LiveCheckRequest(kind, watchId)) }
    }
