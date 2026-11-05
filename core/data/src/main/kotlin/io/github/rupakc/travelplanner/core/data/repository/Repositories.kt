package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.CreateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.CreatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.FareWatchMatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.MarkReadResponse
import io.github.rupakc.travelplanner.core.model.alerts.NotificationsPage
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.UpdateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.UpdatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.chat.ChatRequest
import io.github.rupakc.travelplanner.core.model.devices.Device
import io.github.rupakc.travelplanner.core.model.feedback.AnalyticsEvent
import io.github.rupakc.travelplanner.core.model.feedback.FeedbackRequest
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreview
import io.github.rupakc.travelplanner.core.model.importlink.ImportRequest
import io.github.rupakc.travelplanner.core.model.importlink.ResolvePlaceRequest
import io.github.rupakc.travelplanner.core.model.importlink.ResolvedPlace
import io.github.rupakc.travelplanner.core.model.nearby.LocationCell
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.SharedPlan
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearchIn
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import io.github.rupakc.travelplanner.core.model.search.ActivityFilters
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.core.model.search.DiscoveryRequest
import io.github.rupakc.travelplanner.core.model.search.DiscoveryResponse
import io.github.rupakc.travelplanner.core.model.search.FareCalendar
import io.github.rupakc.travelplanner.core.model.search.FlightFilters
import io.github.rupakc.travelplanner.core.model.search.HotelFilters
import io.github.rupakc.travelplanner.core.model.search.LayoverPlan
import io.github.rupakc.travelplanner.core.model.search.LayoverRequest
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.search.StressTestPayload
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/** Sign-in, session, password and email. */
interface AuthRepository {
    /** Current session; null = signed out (the app shows Login). */
    val session: Flow<Session?>

    suspend fun login(username: String, password: String): AppResult<User>

    /** Stores the fresh token and clears `requires_password_change`. */
    suspend fun changePassword(currentPassword: String, newPassword: String): AppResult<Unit>

    /** `GET /api/auth/me`; updates the stored user. */
    suspend fun refreshUser(): AppResult<User>

    /** `PUT /api/auth/email` (`""` clears); returns the stored email. */
    suspend fun updateEmail(email: String): AppResult<String?>

    /**
     * Unregisters this device's push token (best effort, at most 5 s), clears the session and the user's
     * local data via [SessionCleaner]. Chat history is kept (per username).
     */
    suspend fun logout()
}

/**
 * `/api/search` streaming plus the non-streaming search routes.
 * [current] is the app-wide running/finished search (survives navigation, web parity:
 * the Search tab keeps streaming while you look elsewhere).
 */
interface SearchRepository {
    val current: StateFlow<SearchResultsState>

    /** Starts (or restarts) the app-wide search; cancels any running one. */
    fun start(request: TravelSearchRequest)

    fun cancel()

    /** Resets [current] to idle (New Search). */
    fun clear()

    /** Cold stream folded into state; retries once when the stream ends without `done`. */
    fun results(request: TravelSearchRequest): Flow<SearchResultsState>

    /** Cold raw event stream (with the same single retry). */
    fun events(request: TravelSearchRequest): Flow<SearchEvent>

    /** Single-agent route for [section] (flights, hotels, activities, visa, sim, tips, getting_around, forex, itinerary, events, walking_tours). */
    suspend fun section(section: SectionId, request: TravelSearchRequest): AppResult<SectionData>

    suspend fun flightsFiltered(request: TravelSearchRequest, filters: FlightFilters): AppResult<FlightsData>

    suspend fun hotelsFiltered(request: TravelSearchRequest, filters: HotelFilters): AppResult<HotelsData>

    suspend fun activitiesFiltered(request: TravelSearchRequest, filters: ActivityFilters): AppResult<ActivitiesData>

    suspend fun stressTest(payload: StressTestPayload): AppResult<StressTestData>

    suspend fun layover(request: LayoverRequest): AppResult<LayoverPlan>

    /** `/api/search/sync` (testing only; slow). */
    suspend fun searchSync(request: TravelSearchRequest): AppResult<JsonObject>

    suspend fun airports(query: String, limit: Int = 10): AppResult<List<Airport>>

    suspend fun nationalities(query: String, limit: Int = 10): AppResult<List<Nationality>>
}

/** Chat: SSE stream plus device-local history (the server is stateless for chat). */
interface ChatRepository {
    /** Cold stream of one assistant turn. Ends on `done` or `planning_done`. */
    fun send(request: ChatRequest): Flow<ChatEvent>

    /** Threads of the signed-in user, newest first. */
    val threads: Flow<List<ChatThread>>

    fun messages(threadId: Long): Flow<List<StoredChatMessage>>

    /** Ids of the signed-in user's threads with a message containing [query] (case-insensitive for ASCII; capped). */
    fun threadIdsMatching(query: String): Flow<Set<Long>>

    suspend fun createThread(title: String): Long

    suspend fun renameThread(threadId: Long, title: String)

    suspend fun deleteThread(threadId: Long)

    suspend fun appendMessage(threadId: Long, role: String, content: String, extrasJson: String? = null): Long

    suspend fun updateMessage(messageId: Long, content: String, extrasJson: String? = null)

    suspend fun deleteMessage(messageId: Long)

    suspend fun sessionContext(threadId: Long): JsonObject

    suspend fun saveSessionContext(threadId: Long, context: JsonObject)
}

data class ChatThread(val id: Long, val title: String, val createdAtMillis: Long, val updatedAtMillis: Long)

data class StoredChatMessage(
    val id: Long,
    val threadId: Long,
    val role: String,
    val content: String,
    val extrasJson: String?,
    val createdAtMillis: Long,
)

/** Saved plans, offline-first: [plans]/[plan] read the Room cache; network calls refresh it. */
interface PlansRepository {
    val plans: Flow<List<Plan>>

    /** The signed-in user's cached plan [id] (null when signed out, not cached, or another account's). */
    fun plan(id: Long): Flow<Plan?>

    suspend fun refresh(): AppResult<Unit>

    /** Fetches and caches plan [id]; a 404 evicts its cached copy. */
    suspend fun get(id: Long): AppResult<Plan>

    suspend fun create(name: String, searchData: JsonObject, selections: PlanSelections): AppResult<Plan>

    suspend fun update(id: Long, name: String? = null, selections: PlanSelections? = null): AppResult<Plan>

    suspend fun delete(id: Long): AppResult<Unit>

    /** Returns the 22-char share token (idempotent). */
    suspend fun share(id: Long): AppResult<String>

    suspend fun unshare(id: Long): AppResult<Unit>

    /** Public share link (no auth). */
    suspend fun shared(token: String): AppResult<SharedPlan>
}

/** Server preferences (PUT replaces all fields) and the Taste Graph. */
interface PreferencesRepository {
    suspend fun get(): AppResult<Preferences>

    suspend fun save(preferences: Preferences): AppResult<Preferences>

    /** Read-modify-write helper. */
    suspend fun update(transform: (Preferences) -> Preferences): AppResult<Preferences>

    suspend fun tasteProfile(): AppResult<TasteProfile>

    /** Returns deleted signal count. */
    suspend fun resetTasteProfile(): AppResult<Int>
}

/** Last 8 searches per user, cached in Room. */
interface RecentSearchesRepository {
    val recent: Flow<List<RecentSearch>>

    suspend fun refresh(): AppResult<Unit>

    suspend fun add(search: RecentSearchIn): AppResult<RecentSearch>

    suspend fun delete(id: String): AppResult<Unit>
}

interface FareWatchesRepository {
    suspend fun list(): AppResult<List<FareWatch>>

    suspend fun match(trip: FareWatchMatchRequest): AppResult<FareWatch?>

    suspend fun create(request: CreateFareWatchRequest): AppResult<FareWatch>

    suspend fun get(id: Long): AppResult<FareWatch>

    suspend fun update(id: Long, patch: UpdateFareWatchRequest): AppResult<FareWatch>

    suspend fun delete(id: Long): AppResult<Unit>

    /** Response carries `check_status` and `notification`. */
    suspend fun check(id: Long): AppResult<FareWatch>
}

interface PriceWatchesRepository {
    suspend fun list(kind: String? = null): AppResult<List<PriceWatch>>

    suspend fun match(kind: String, spec: JsonObject): AppResult<PriceWatch?>

    /** 422 `no_live_price` / `target_currency_mismatch` arrive as AppError.Client with code + extras. */
    suspend fun create(request: CreatePriceWatchRequest): AppResult<PriceWatch>

    suspend fun get(id: Long): AppResult<PriceWatch>

    suspend fun update(id: Long, patch: UpdatePriceWatchRequest): AppResult<PriceWatch>

    suspend fun delete(id: Long): AppResult<Unit>

    suspend fun check(id: Long): AppResult<PriceWatch>
}

interface NotificationsRepository {
    suspend fun list(unreadOnly: Boolean = false, limit: Int = 20, offset: Int = 0): AppResult<NotificationsPage>

    suspend fun unreadCount(): AppResult<Int>

    suspend fun markRead(ids: List<Long>): AppResult<MarkReadResponse>

    suspend fun markAllRead(): AppResult<MarkReadResponse>

    /** Unread notifications not yet posted on this device (for AlertPollWorker / FCM dedupe). */
    suspend fun unseen(limit: Int = 50): AppResult<List<AppNotification>>

    suspend fun markSeen(ids: List<Long>)
}

/** Push device registration (spec §7). */
interface DevicesRepository {
    suspend fun register(token: String, locale: String?): AppResult<Device>

    suspend fun list(): AppResult<List<Device>>

    suspend fun delete(id: Long): AppResult<Unit>

    /** Idempotent `POST /api/devices/unregister`. */
    suspend fun unregister(token: String): AppResult<Unit>
}

/** Near-me recommendations, cached 1 h per ~5 km cell (spec §8). */
interface NearbyRepository {
    suspend fun nearby(lat: Double, lng: Double, interests: List<String>? = null, limit: Int = 10): AppResult<NearbyResponse>

    /** Remembers a foreground fix, rounded to its ~5 km cell, with the time (device-only). */
    suspend fun rememberLocation(lat: Double, lng: Double)

    /** The last remembered cell, or null (never set, or cleared on sign-out). */
    suspend fun lastLocation(): LocationCell?

    /** Deletes the remembered cell (nearby suggestions turned off). */
    suspend fun forgetLocation()
}

interface CompareRepository {
    suspend fun compare(request: TravelSearchRequest): AppResult<CompareSummary>
}

interface DiscoverRepository {
    suspend fun discover(request: DiscoveryRequest): AppResult<DiscoveryResponse>
}

interface ImportRepository {
    suspend fun extract(request: ImportRequest): AppResult<ImportPreview>

    suspend fun resolvePlace(request: ResolvePlaceRequest): AppResult<ResolvedPlace>
}

interface FareCalendarRepository {
    suspend fun fareCalendar(request: TravelSearchRequest): AppResult<FareCalendar>
}

/** `/health` capability flags, cached for the process. */
interface HealthRepository {
    val capabilities: StateFlow<Health?>

    suspend fun refresh(): AppResult<Health>

    /**
     * One-off `GET <baseUrl>/health` of a candidate server (Settings → Server "Test connection"):
     * no session token, no change to settings or [capabilities]. An unparseable URL fails with
     * `AppError.Client(code = "invalid_url")`.
     */
    suspend fun probe(baseUrl: String): AppResult<Health>
}

/** Device-only settings. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Effective backend URL (override or flavour default; "" when none). */
    val effectiveBaseUrl: Flow<String>

    /**
     * Validates then stores; null/blank resets to the flavour default. If the effective server changes
     * the user is signed out (the token belongs to the old server) and local user data is cleared.
     */
    suspend fun setBaseUrl(url: String?): AppResult<Unit>

    /**
     * [setBaseUrl]'s own validation without storing anything: the normalised URL, or the same failure
     * `setBaseUrl` would return. Callers that sign out before switching check this first, so a URL the
     * store would reject never costs the user their session.
     */
    fun checkBaseUrl(url: String): AppResult<String>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setLocaleTag(tag: String?)

    suspend fun setNotificationToggles(trip: Boolean? = null, priceAlerts: Boolean? = null, nearby: Boolean? = null, nearbyAlways: Boolean? = null)
}

/** Traveller profile + feature overrides (device-local, spec §6). */
interface ProfileRepository {
    val profile: Flow<TravellerProfile>

    val featureOverrides: Flow<FeatureOverrides>

    suspend fun saveProfile(profile: TravellerProfile)

    suspend fun setFeature(featureId: String, enabled: Boolean?)

    suspend fun resetFeatures()

    /** Replaces all overrides in one write (observers never see a half-applied set). */
    suspend fun setFeatureOverrides(overrides: FeatureOverrides) {
        resetFeatures()
        overrides.enabled.forEach { setFeature(it, true) }
        overrides.disabled.forEach { setFeature(it, false) }
    }
}

interface FeedbackRepository {
    suspend fun sendFeedback(request: FeedbackRequest): AppResult<Unit>

    suspend fun sendAnalytics(events: List<AnalyticsEvent>): AppResult<Unit>
}

/**
 * Wipes the signed-in user's local data (draft, search, caches; not chat history). Called only on explicit
 * logout, when a different user signs in, and when the server URL changes — never on a 401 or token expiry.
 * Features normally never call it.
 */
interface SessionCleaner {
    suspend fun clearUserData()
}

/** Supplied by core:notifications when Firebase is configured (optional binding). */
fun interface PushTokenSource {
    suspend fun token(): String?
}
