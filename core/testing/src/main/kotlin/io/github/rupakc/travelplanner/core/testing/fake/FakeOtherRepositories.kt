package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.CompareRepository
import io.github.rupakc.travelplanner.core.data.repository.DevicesRepository
import io.github.rupakc.travelplanner.core.data.repository.DiscoverRepository
import io.github.rupakc.travelplanner.core.data.repository.FareCalendarRepository
import io.github.rupakc.travelplanner.core.data.repository.FareWatchesRepository
import io.github.rupakc.travelplanner.core.data.repository.FeedbackRepository
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.ImportRepository
import io.github.rupakc.travelplanner.core.data.repository.LiveCheckRepository
import io.github.rupakc.travelplanner.core.data.repository.NearbyRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.data.repository.PriceWatchesRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.RecentSearchesRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.CreateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.CreatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.FareWatchMatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.LiveCheckResult
import io.github.rupakc.travelplanner.core.model.alerts.MarkReadResponse
import io.github.rupakc.travelplanner.core.model.alerts.NotificationsPage
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.UpdateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.UpdatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.devices.Device
import io.github.rupakc.travelplanner.core.model.feedback.AnalyticsEvent
import io.github.rupakc.travelplanner.core.model.feedback.FeedbackRequest
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreview
import io.github.rupakc.travelplanner.core.model.importlink.ImportRequest
import io.github.rupakc.travelplanner.core.model.importlink.ResolvePlaceRequest
import io.github.rupakc.travelplanner.core.model.importlink.ResolvedPlace
import io.github.rupakc.travelplanner.core.model.nearby.LocationCell
import io.github.rupakc.travelplanner.core.model.nearby.NearbyItemsResponse
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearchIn
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.core.model.search.DiscoveryRequest
import io.github.rupakc.travelplanner.core.model.search.DiscoveryResponse
import io.github.rupakc.travelplanner.core.model.search.FareCalendar
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

/** Server preferences held in memory (PUT replaces all, like the backend). */
class FakePreferencesRepository(initial: Preferences = Preferences(username = "alice")) : PreferencesRepository {
    val current = MutableStateFlow(initial)
    var taste: AppResult<TasteProfile> = AppResult.Success(TasteProfile())

    override suspend fun get(): AppResult<Preferences> = AppResult.Success(current.value)

    override suspend fun save(preferences: Preferences): AppResult<Preferences> = AppResult.Success(preferences.also { current.value = it })

    override suspend fun update(transform: (Preferences) -> Preferences): AppResult<Preferences> = save(transform(current.value))

    override suspend fun tasteProfile(): AppResult<TasteProfile> = taste

    override suspend fun resetTasteProfile(): AppResult<Int> = AppResult.Success(0).also { taste = AppResult.Success(TasteProfile()) }
}

class FakeRecentSearchesRepository : RecentSearchesRepository {
    private val items = MutableStateFlow<List<RecentSearch>>(emptyList())
    override val recent: Flow<List<RecentSearch>> = items
    private var nextId = 1

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun add(search: RecentSearchIn): AppResult<RecentSearch> {
        val item = RecentSearch(id = "rs-${nextId++}", search = search)
        items.update { (listOf(item) + it.filterNot { r -> r.search == search }).take(MAX_RECENT) }
        return AppResult.Success(item)
    }

    override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(items.update { list -> list.filterNot { it.id == id } })

    private companion object {
        const val MAX_RECENT = 8
    }
}

class FakeFareWatchesRepository : FareWatchesRepository {
    val watches = MutableStateFlow<List<FareWatch>>(emptyList())
    var createResult: AppResult<FareWatch>? = null
    private var nextId = 1L

    override suspend fun list(): AppResult<List<FareWatch>> = AppResult.Success(watches.value)

    override suspend fun match(trip: FareWatchMatchRequest): AppResult<FareWatch?> = AppResult.Success(null)

    override suspend fun create(request: CreateFareWatchRequest): AppResult<FareWatch> =
        createResult ?: AppResult.Success(
            FareWatch(
                id = nextId++,
                planId = request.planId,
                alertMode = request.alertMode,
                targetPrice = request.targetPrice,
                status = "active",
            )
                .also { w -> watches.update { it + w } },
        )

    override suspend fun get(id: Long): AppResult<FareWatch> =
        watches.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: NotStubbed

    override suspend fun update(id: Long, patch: UpdateFareWatchRequest): AppResult<FareWatch> {
        val updated = watches.value.firstOrNull { it.id == id }?.copy(status = patch.status ?: "active") ?: return NotStubbed
        watches.update { list -> list.map { if (it.id == id) updated else it } }
        return AppResult.Success(updated)
    }

    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(watches.update { list -> list.filterNot { it.id == id } })

    override suspend fun check(id: Long): AppResult<FareWatch> = get(id)
}

class FakePriceWatchesRepository : PriceWatchesRepository {
    val watches = MutableStateFlow<List<PriceWatch>>(emptyList())
    var createResult: AppResult<PriceWatch>? = null

    /** Every create request, in order (one per POST the app would send). */
    val createRequests = mutableListOf<CreatePriceWatchRequest>()

    /** When set, create suspends until it completes (to test taps while a request is in flight). */
    var createGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private var nextId = 1L

    override suspend fun list(kind: String?): AppResult<List<PriceWatch>> =
        AppResult.Success(watches.value.filter { kind == null || it.kind == kind })

    override suspend fun match(kind: String, spec: JsonObject): AppResult<PriceWatch?> = AppResult.Success(null)

    override suspend fun create(request: CreatePriceWatchRequest): AppResult<PriceWatch> {
        createRequests += request
        createGate?.await()
        return createResult ?: AppResult.Success(
            PriceWatch(id = nextId++, kind = request.kind, planId = request.planId, alertMode = request.alertMode, status = "active")
                .also { w -> watches.update { it + w } },
        )
    }

    override suspend fun get(id: Long): AppResult<PriceWatch> =
        watches.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: NotStubbed

    override suspend fun update(id: Long, patch: UpdatePriceWatchRequest): AppResult<PriceWatch> {
        val updated = watches.value.firstOrNull { it.id == id }?.copy(status = patch.status ?: "active") ?: return NotStubbed
        watches.update { list -> list.map { if (it.id == id) updated else it } }
        return AppResult.Success(updated)
    }

    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(watches.update { list -> list.filterNot { it.id == id } })

    override suspend fun check(id: Long): AppResult<PriceWatch> = get(id)
}

class FakeNotificationsRepository(initial: List<AppNotification> = emptyList()) : NotificationsRepository {
    val items = MutableStateFlow(initial)
    val seen = mutableSetOf<Long>()

    override suspend fun list(unreadOnly: Boolean, limit: Int, offset: Int): AppResult<NotificationsPage> {
        val all = items.value.filter { !unreadOnly || it.isUnread }
        return AppResult.Success(NotificationsPage(all.drop(offset).take(limit), all.size, items.value.count { it.isUnread }))
    }

    override suspend fun unreadCount(): AppResult<Int> = AppResult.Success(items.value.count { it.isUnread })

    override suspend fun markRead(ids: List<Long>): AppResult<MarkReadResponse> {
        items.update { list -> list.map { if (it.id in ids) it.copy(readAt = "now") else it } }
        return AppResult.Success(MarkReadResponse(ids.size, items.value.count { it.isUnread }))
    }

    override suspend fun markAllRead(): AppResult<MarkReadResponse> = markRead(items.value.map { it.id })

    override suspend fun unseen(limit: Int): AppResult<List<AppNotification>> =
        AppResult.Success(items.value.filter { it.isUnread && it.id !in seen }.take(limit))

    override suspend fun markSeen(ids: List<Long>) {
        seen += ids
    }
}

class FakeDevicesRepository : DevicesRepository {
    val devices = MutableStateFlow<List<Device>>(emptyList())
    val unregistered = mutableListOf<String>()

    override suspend fun register(token: String, locale: String?): AppResult<Device> {
        val device = Device(id = devices.value.size + 1L, token = token, platform = "android", locale = locale)
        devices.update { it + device }
        return AppResult.Success(device)
    }

    override suspend fun list(): AppResult<List<Device>> = AppResult.Success(devices.value)

    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(devices.update { list -> list.filterNot { it.id == id } })

    override suspend fun unregister(token: String): AppResult<Unit> = AppResult.Success(Unit).also { unregistered += token }
}

class FakeNearbyRepository(var result: AppResult<NearbyResponse> = AppResult.Success(NearbyResponse())) : NearbyRepository {
    var last: LocationCell? = null
    var now: () -> Long = { System.currentTimeMillis() }

    /** Every `/api/nearby` call made through this fake (lat, lng, limit, language), oldest first. */
    val requests = mutableListOf<NearbyCall>()

    data class NearbyCall(val lat: Double, val lng: Double, val limit: Int, val language: String?)

    override suspend fun nearby(lat: Double, lng: Double, limit: Int, language: String?): AppResult<NearbyResponse> {
        requests += NearbyCall(lat, lng, limit, language)
        return result
    }

    var itemsResult: AppResult<NearbyItemsResponse> = AppResult.Success(NearbyItemsResponse())

    /** Delays [nearbyItems] by this long (timeout tests). */
    var itemsDelayMs: Long = 0

    /** Every v1 `/api/nearby` call (lat, lng, limit), oldest first. */
    val itemCalls = mutableListOf<Triple<Double, Double, Int>>()

    override suspend fun nearbyItems(lat: Double, lng: Double, limit: Int): AppResult<NearbyItemsResponse> {
        itemCalls += Triple(lat, lng, limit)
        if (itemsDelayMs > 0) kotlinx.coroutines.delay(itemsDelayMs)
        return itemsResult
    }

    override suspend fun rememberLocation(lat: Double, lng: Double) {
        last = LocationCell(lat, lng, now())
    }

    override suspend fun lastLocation() = last

    override suspend fun forgetLocation() {
        last = null
    }
}

class FakeLiveCheckRepository(var result: AppResult<LiveCheckResult> = AppResult.Success(LiveCheckResult())) : LiveCheckRepository {
    /** (kind, watchId) of every call, oldest first. */
    val requests = mutableListOf<Pair<String, Long>>()

    /** When set, [check] waits for it (to test in-flight behaviour). */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun check(kind: String, watchId: Long): AppResult<LiveCheckResult> {
        requests += kind to watchId
        gate?.await()
        return result
    }
}

class FakeCompareRepository(var result: AppResult<CompareSummary> = AppResult.Success(CompareSummary())) : CompareRepository {
    val requests = mutableListOf<TravelSearchRequest>()

    override suspend fun compare(request: TravelSearchRequest): AppResult<CompareSummary> = result.also { requests += request }
}

class FakeDiscoverRepository(var result: AppResult<DiscoveryResponse> = AppResult.Success(DiscoveryResponse())) : DiscoverRepository {
    /** Every request sent, oldest first. */
    val requests = mutableListOf<DiscoveryRequest>()

    override suspend fun discover(request: DiscoveryRequest): AppResult<DiscoveryResponse> {
        requests += request
        return result
    }
}

class FakeImportRepository : ImportRepository {
    var extractResult: AppResult<ImportPreview> = AppResult.Success(ImportPreview())
    var resolveResult: AppResult<ResolvedPlace> = NotStubbed

    override suspend fun extract(request: ImportRequest) = extractResult

    override suspend fun resolvePlace(request: ResolvePlaceRequest) = resolveResult
}

class FakeFareCalendarRepository(var result: AppResult<FareCalendar> = AppResult.Success(FareCalendar())) : FareCalendarRepository {
    override suspend fun fareCalendar(request: TravelSearchRequest) = result
}

class FakeHealthRepository(initial: Health? = Health(status = "ok")) : HealthRepository {
    private val state = MutableStateFlow(initial)
    override val capabilities: StateFlow<Health?> = state.asStateFlow()

    override suspend fun refresh(): AppResult<Health> = state.value?.let { AppResult.Success(it) } ?: NotStubbed

    /** Result of [probe]; every probed URL is recorded in [probed]. */
    var probeResult: AppResult<Health> = AppResult.Success(Health(status = "ok"))
    val probed = mutableListOf<String>()

    override suspend fun probe(baseUrl: String): AppResult<Health> = probeResult.also { probed += baseUrl }

    fun set(health: Health?) {
        state.value = health
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings(), private val defaultBaseUrl: String = "http://10.0.2.2:8001") :
    SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override val effectiveBaseUrl: Flow<String> = state.map { it.baseUrlOverride ?: defaultBaseUrl }

    /** Set to make [checkBaseUrl] (and so [setBaseUrl]) reject every URL with this error. */
    var rejectUrls: io.github.rupakc.travelplanner.core.common.AppError? = null

    override suspend fun setBaseUrl(url: String?): AppResult<Unit> {
        if (!url.isNullOrBlank()) rejectUrls?.let { return AppResult.Failure(it) }
        return AppResult.Success(state.update { it.copy(baseUrlOverride = url?.takeIf(String::isNotBlank)) })
    }

    override fun checkBaseUrl(url: String): AppResult<String> = rejectUrls?.let { AppResult.Failure(it) } ?: AppResult.Success(url)

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }

    override suspend fun setDynamicColor(enabled: Boolean) = state.update { it.copy(dynamicColor = enabled) }

    override suspend fun setLocaleTag(tag: String?) = state.update { it.copy(localeTag = tag) }

    override suspend fun setNotificationToggles(trip: Boolean?, priceAlerts: Boolean?, nearby: Boolean?, nearbyAlways: Boolean?) =
        state.update {
            it.copy(
                tripNotifications = trip ?: it.tripNotifications,
                priceAlertNotifications = priceAlerts ?: it.priceAlertNotifications,
                nearbyNotifications = nearby ?: it.nearbyNotifications,
                nearbyNotificationsAlways = nearbyAlways ?: it.nearbyNotificationsAlways,
            )
        }
}

class FakeProfileRepository(initial: TravellerProfile = TravellerProfile()) : ProfileRepository {
    val profileState = MutableStateFlow(initial)
    val overridesState = MutableStateFlow(FeatureOverrides())
    override val profile: Flow<TravellerProfile> = profileState
    override val featureOverrides: Flow<FeatureOverrides> = overridesState

    override suspend fun saveProfile(profile: TravellerProfile) {
        profileState.value = profile
    }

    override suspend fun setFeature(featureId: String, enabled: Boolean?) =
        overridesState.update { o ->
            val on = o.enabled - featureId
            val off = o.disabled - featureId
            FeatureOverrides(if (enabled == true) on + featureId else on, if (enabled == false) off + featureId else off)
        }

    override suspend fun resetFeatures() {
        overridesState.value = FeatureOverrides()
    }

    /** Number of whole-set writes (atomic replace). */
    var overrideWrites = 0

    override suspend fun setFeatureOverrides(overrides: FeatureOverrides) {
        overrideWrites++
        overridesState.value = FeatureOverrides(overrides.enabled, overrides.disabled - overrides.enabled)
    }
}

class FakeFeedbackRepository : FeedbackRepository {
    val feedback = mutableListOf<FeedbackRequest>()
    val events = mutableListOf<AnalyticsEvent>()

    override suspend fun sendFeedback(request: FeedbackRequest): AppResult<Unit> = AppResult.Success(Unit).also { feedback += request }

    override suspend fun sendAnalytics(events: List<AnalyticsEvent>): AppResult<Unit> =
        AppResult.Success(Unit).also {
            this.events += events
        }
}
