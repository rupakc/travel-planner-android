# Foundation interfaces

What feature teams build on. Everything below exists on `main` and compiles; signatures are
copied from the code. If this page and the code disagree, the code wins — fix this page in the
same PR. Packages are under `io.github.rupakc.travelplanner` (abbreviated `…` below).

Read first: `docs/superpowers/specs/2026-11-05-android-app-design.md` (spec),
`docs/reference/api-contract.md` (backend contract), `docs/reference/web-ui-inventory.md` (web copy and tokens).

## 1. Rules

- **Work only inside your feature module** (`feature/<name>`). Features never depend on each other.
  Each feature sees exactly `core:{ui,designsystem,data,domain,model,common}` (wired by the
  `travelplanner.android.feature` plugin — don't add other project dependencies).
- To leave your feature, call a lambda your graph function receives (e.g. `onOpenPlan(planId)`);
  `app` turns it into a route from `core:model` (`….core.model.navigation`). Need a new callback or
  route? Add the parameter/route, update `app/…/navigation/TravelApp.kt`, mention it in your PR.
- Screen pattern (spec §4): stateless `XxxScreen(state, onEvent, modifier)` + `XxxRoute` that gets a
  `@HiltViewModel` via `hiltViewModel()` (import `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`)
  and collects `StateFlow<XxxUiState>` with `collectAsStateWithLifecycle()`.
- Inject **repository interfaces** (section 3) — never Retrofit, DAOs or DataStore.
- No hard-coded colours, dp or text in features: use `TravelTheme` (section 5) and string resources (section 6).
- Never invent data: every price, link and fact comes from the API; label estimates (`PriceText(isEstimate = true)`).
- Lint fails on a11y/i18n issues (missing `contentDescription`, hard-coded text, missing `es`
  translation, plural candidates…). detekt and ktlint (Spotless) run in CI with no baseline.

Verify before every PR (same as CI):

```
./gradlew spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug assembleProdRelease
```

`./gradlew spotlessApply` fixes formatting.

## 2. Modules, flavours, build

| Module | Kind | You use it for |
|---|---|---|
| `core:common` | pure Kotlin | `AppResult`, `AppError`, `AppException`, `AppDispatchers`, `AppClock`, `AppLogger`, `AppBuildInfo` |
| `core:model` | pure Kotlin | wire models (kotlinx.serialization), `SearchEvent`/`ChatEvent`, plan models, routes |
| `core:domain` | pure Kotlin | `TravellerType`, `FeatureId`, `FeatureCatalogue`/`TravellerClassifier` interfaces, `formatPrice`; your ported web utils go here |
| `core:data` | Android | repository interfaces + Hilt bindings, `CurrentPlanStore` |
| `core:designsystem` | Android/Compose | `TravelTheme`, tokens, `Tp*` components |
| `core:ui` | Android/Compose | `SectionScaffold`, `VerificationChip`, `PriceText`, `BookingLinkButton`, `AddToPlanButton`, `StreamingStatusAnnouncer`, `FeaturePlaceholder` |
| `core:notifications` | Android | channels, `Notifier`, optional FCM (app-level only) |
| `core:testing` | Android (tests) | fakes, `MainDispatcherRule`, SSE fixtures |
| `core:network`, `core:database`, `core:datastore` | Android | internal to `core:data` — not visible to features |

- Flavours (every Android module): `local` (default backend `http://10.0.2.2:8001`, cleartext allowed) and `prod`
  (`-PPROD_BASE_URL=https://…`, HTTPS only). Variant names: `localDebug`, `prodRelease`, ….
- compileSdk 37, targetSdk 36, minSdk 26, JDK/Kotlin 21. Versions only in `gradle/libs.versions.toml`.
- Unit tests run on Robolectric SDK 35 (shared `config/robolectric/robolectric.properties`).

## 3. Repositories (`….core.data.repository`, Hilt-injected)

All suspend calls return `AppResult<T>`; they never throw except cancellation. Streams are cold
`Flow`s that throw `AppException(error)` on failure.

```kotlin
sealed interface AppResult<out T> {               // ….core.common
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
// helpers: map, onSuccess, onFailure, getOrNull, errorOrNull

sealed interface AppError { val message: String? }   // message = backend `detail` (show as-is) or null → use your own string
// Network, Unauthorized, Forbidden, NotFound(message, code), RateLimited(message, retryAfterSeconds),
// Client(status, message, code, fieldErrors, extras), Server(status, message, code), Decoding,
// Interrupted, NotConfigured, Unknown
```

`AppError.Client.code`/`extras` carry structured details: `/api/import/*` codes (`empty_page`, `no_places`, …),
price-watch 422s (`no_live_price` with `suggestions`/`estimate` in `extras` as raw JSON text — decode with
`TravelJson` into `NoLivePriceError`; `target_currency_mismatch`), nearby `NotFound(code = "no_place")`,
`Server(code = "agent_error")`. 401 anywhere clears the session automatically (the shell shows Login and a snackbar).
A 401 or an expired token only signs out: the draft, cached plans, recent searches and chat survive. Local data is
wiped (`SessionCleaner`) only on explicit logout, on login as a different user, and on a server change.

Session rules (fix round 1): the session is encrypted at rest (Tink, Keystore-wrapped key); an expired JWT
(`exp`) reads as signed out, so `AuthRepository.session` emits `null` at expiry and the gate shows Login;
`Session.baseUrl` records the issuing backend and the token is sent only there (never to other hosts —
do **not** reuse the app's OkHttp clients for images or third-party pages); changing Settings → Server signs out.

```kotlin
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

    fun plan(id: Long): Flow<Plan?>

    suspend fun refresh(): AppResult<Unit>

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

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setLocaleTag(tag: String?)

    suspend fun setNotificationToggles(trip: Boolean? = null, priceAlerts: Boolean? = null, nearby: Boolean? = null)
}

/** Traveller profile + feature overrides (device-local, spec §6). */
interface ProfileRepository {
    val profile: Flow<TravellerProfile>

    val featureOverrides: Flow<FeatureOverrides>

    suspend fun saveProfile(profile: TravellerProfile)

    suspend fun setFeature(featureId: String, enabled: Boolean?)

    suspend fun resetFeatures()
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
```

### 3.1 Shared My Plan draft (`….core.data.plan.CurrentPlanStore`)

Persistence (fix round 1): the draft is written on every change; the search-data snapshot is written only
when it changes (debounced 500 ms) and is not persisted above 1.5 MB (kept in memory, warning logged).
`save()` is serialised and applies only `planId`/`name` on success, so edits made during a save survive and
leave `dirty = true`. Storage failures are logged, never thrown.

```kotlin
/**
 * The shared My Plan draft consumed by results, chat and plan features.
 * Hilt singleton; persisted to Room so it survives process death.
 */
interface CurrentPlanStore {
    val draft: StateFlow<PlanDraft>

    /** Single-choice categories (flight, hotel, sim, one flight per leg) replace; lists add/remove. */
    fun toggle(item: PlanItem)

    fun isSelected(item: PlanItem): Boolean

    fun setSelections(selections: PlanSelections)

    fun rename(name: String)

    /** Records the search behind the draft and the latest sections (`{"flights":{…}}`); sets `searchKey`. */
    fun attachSearch(request: TravelSearchRequest, data: JsonObject?)

    /** Empties the draft (new, unsaved plan). */
    fun newPlan()

    /** Empties the draft and deletes it from storage (sign-out / user change; called by `SessionCleaner`). */
    suspend fun reset()

    /**
     * Loads a saved plan into the draft (via PlansRepository): `searchRequest` and `searchKey` from the plan's
     * `search_data`, and `searchData` with whatever sections the plan holds (normally just the `itinerary`
     * snapshot) so Results can render it without a stream.
     */
    suspend fun load(planId: Long): AppResult<PlanDraft>

    /**
     * Creates or updates the plan via PlansRepository. Calls are serialised (a double tap creates one plan).
     * On success only `planId`/`name` are applied; edits made while saving are kept and leave `dirty = true`.
     */
    suspend fun save(name: String? = null): AppResult<Plan>

    /** For undo: capture, then [restore]. */
    fun snapshot(): PlanDraft

    fun restore(snapshot: PlanDraft)
}
```

`PlanDraft` (`….core.model.plan`): `planId: Long?`, `name: String?`, `searchRequest: TravelSearchRequest?`,
`searchData: JsonObject?` (sections keyed by wire name), `selections: PlanSelections`, `dirty: Boolean`,
`searchKey: String?` (= `searchRequest.searchKey()`; set by `attachSearch`/`load`, null after `newPlan`);
`PlanDraft.DEFAULT_NAME = "My Trip Plan"`.

`PlanItem` (sealed, `….core.model.plan`): `FlightItem(flight)` (with `leg_index` → one per leg in `flights`),
`HotelItem`, `SimItem` (single choice) and `ActivityItem`, `PlaceItem`, `WalkingTourItem` (key `url`),
`EventItem`, `TipItem` (key `title`), `TransportItem`, `SlotItem` (key `"<day>-<time_of_day>"`) (lists).
Pure rules for chat `plan_action`s or previews: `PlanSelectionRules.toggle(selections, item)` / `isSelected(...)`.

`PlanSelections` mirrors the web's `EMPTY_SELECTIONS` keys exactly (`flight, flights, hotel, activities,
places_to_see, walking_tours, sim, tips, getting_around, events, itinerary_notes, itinerary_edits,
itinerary_slots, itinerary_layout, packing_list` + `itinerary` snapshot + unknown keys in `extras`).
Items keep the exact JSON they came from (`raw`) so web ↔ app saves are lossless; build items from the
objects you received, not by constructing new ones, whenever you have them.

### 3.2 Search state (`SearchRepository.current`)

`SearchResultsState`: `request`, `sections: Map<SectionId, SectionResult(id, data, raw, source)>`,
`statuses: Map<SectionId, SectionStatus>` (WAITING/LOADING/ENHANCING/DONE — web state machine),
`confidence`, `pricingAdvisor`, `phase` (IDLE/STREAMING/DONE/INTERRUPTED/FAILED/CANCELLED), `error`, `retries`;
helper `state.section<HotelsData>(SectionId.HOTELS)`. Replace-by-type, keep-old-on-AI-error and one retry
without `done` are already applied. `SearchResultsReducer.sectionsJson(state)` (in `….core.data.impl`)
gives the `{"flights":{…}}` object for chat `search_results` and `CurrentPlanStore.attachSearch`.

## 4. Models (`core:model`) and streams

- Decode JSON only with `TravelJson` (`….core.model.json`): ignores unknown keys, coerces, lenient.
  Numbers that may be strings use `LenientDouble`/`LenientInt`/`LenientBoolean`/`LenientString`/`LenientStringList`
  typealiases (unparseable → null, never a made-up number).
- Request models: `TravelSearchRequest` (+ `MustIncludePlace`, `ImportSource`, `FlightFilters`, `HotelFilters`,
  `ActivityFilters`), `DiscoveryRequest`, `LayoverRequest`, `StressTestPayload`, `ChatRequest`/`ChatMessage`,
  `CreateFareWatchRequest`, `CreatePriceWatchRequest` (+ `HotelWatchSpec`, `ActivityWatchSpec`, `TripWatchSpec`
  encoded to `JsonObject`), `ImportRequest`, `ResolvePlaceRequest`, `NearbyRequest`, `RecentSearchIn`, `Preferences`.
- Section data (`….core.model.section`, all `SectionData` with `error` + `verification`): `FlightsData`(`Flight`,
  `FlightLeg`, `FlightLegGroup`), `WeatherData`, `HotelsData`(`Hotel`), `ActivitiesData`(`Activity`),
  `PlacesData`(`PlaceToSee`), `WalkingToursData`, `EventsData`(`TravelEvent`), `VisaData`, `SimData`(`SimPlan`),
  `TipsData`(`Tip`), `EmergencyCardData`, `GettingAroundData`(`TransportOption`), `ForexData`, `ItineraryData`
  (`ItineraryDay`, `ItinerarySlot`), `StressTestData`, `PackingListData`; plus `ConfidenceData`, `PricingAdvisorData`,
  `UndecodedSection` (raw fallback). `SectionId` enum (web order, `wire` names, `staticBacked`, `phaseOne`).
- `SearchEvent`: `Section(id, source, data, raw)`, `Confidence`, `PricingAdvisor`, `AgentStatus`, `Done`, `Unknown`.
- `ChatEvent`: `Delta`, `Suggestions`, `SessionContextUpdate`, `PlanAction(action, field, data)` (+`fieldKind`),
  `PlanningStart`, `TripMap`, `ItineraryGenerating`, `ComprehensiveItinerary(data: ChatItinerary?, error, raw)`,
  `AgentStatus`, `SectionResult`, `ImportLink(url, href)`, `Error`, `Message` (legacy), `PlanningDone`, `Done`,
  `Unknown`; `isTerminal` = Done or PlanningDone.
- Others: `Plan`, `SharedPlan`, `FareWatch`, `PriceWatch`, `AppNotification`(+`watchId`, `watchKind`),
  `NotificationsPage`, `Health` (capability flags incl. `pushConfigured`), `CompareSummary`, `DiscoveryResponse`,
  `FareCalendar`, `LayoverPlan`, `ImportPreview`, `ResolvedPlace`, `NearbyResponse`(`NearbyPlace`), `Device`,
  `TasteProfile`, `RecentSearch`, `AppSettings`, `ThemeMode`, `TravellerProfile`, `FeatureOverrides`, `Session`.

## 4b. API additions in fix round 1 (all additive)

- `TravelSearchRequest.searchKey(): String` (`….core.model.search`) — stable id of a search (normalised request hash;
  empty `must_include` and a single-city `destinations` list count as absent).
  Results: the stream belongs to the draft when `state.request?.searchKey() == draft.searchKey`.
- `PlanDraft.searchKey: String? = null`. `CurrentPlanStore.load(id)` now also fills `searchRequest`, `searchKey` and
  `searchData`. A saved plan's `search_data` is the request (web parity), so `searchData` normally holds only
  `itinerary` (from `selections.itinerary`, written on save) — other sections are not stored with plans; any section
  objects found in `search_data` are restored too. `searchData` is null when nothing is recoverable.
- `SessionStore.lastServer()` (core-internal): local data is wiped when a login's user **or** server differs from the
  last session's, and on every Settings → Server change (signed in or not).
- Session storage never crashes: a broken Keystore keeps the session in memory for the process (signed in until the
  app is killed); unreadable data reads as signed out; storage failures in `login`/`changePassword` return `Failure`.
  Expiry is re-checked against the wall clock at least every 30 s of awake time (`EncryptedSessionStore.EXPIRY_TICK_MS`).

- `Session(token, user, baseUrl: String? = null)` — issuing backend.
- `CurrentPlanStore.reset()` (suspend) — empty the draft and its storage.
- `SessionCleaner { suspend fun clearUserData() }` (`….core.data.repository`) — bound in Hilt; called by the core.
- `SessionStore.lastUsername()` (core:datastore, internal to core).
- `NetworkModule.STREAM_IDLE_SECONDS = 300`; `CurrentPlanStoreImpl.MAX_SEARCH_DATA_BYTES`, `SEARCH_DATA_DEBOUNCE_MS`.
- Behaviour: `AuthRepository.logout()` keeps chat history and finishes within ~5 s; `SettingsRepository.setBaseUrl`
  signs out when the effective server changes; `AppError.Client(code = "invalid_url" | "https_required")` unchanged.

## 4c. Security notes

- Session at rest: one AES-256-GCM sealed value (Tink); the keyset lives in `shared_prefs/tp_keys.xml`, wrapped by
  Android Keystore key `tp_session_master`. Both files and the Room DB (+ WAL/SHM) are excluded from backup.
- Tink silently falls back to a **cleartext** keyset when the Keystore is unusable. The app checks
  `isUsingKeystore()` after every build: if false it deletes the keyset and keeps the session **in memory only**
  for the process (the user signs in again after the app is killed). The session token is never written in clear, and a keyset not protected by the Keystore is never used and never left on disk.
- Trade-off (accepted): a transient Keystore error during creation also resets the keyset, which signs the
  user out (the old sealed session can't be decrypted). Sign-out is preferred to a crash or a cleartext key.
- The bearer token is sent only to the backend that issued it (`Session.baseUrl`); never give the app's OkHttp
  clients to image loaders or third-party fetches.
- `searchKey` (R3-M2, accepted): it is persisted with the draft, so after an update that adds request fields a restored
  draft keeps its old key until the next `attachSearch`; Results then treats a finished stream as not the draft's.

## 5. Navigation

Routes (`….core.model.navigation`, `@Serializable`):

```kotlin
sealed interface AppRoute

// --- auth ---------------------------------------------------------------
@Serializable data object LoginRoute : AppRoute

@Serializable data object ChangePasswordRoute : AppRoute

// --- onboarding ---------------------------------------------------------

/** Welcome → questions → result → feature picker. [fromSettings] = re-run from Settings (ends with back, not Home). */
@Serializable data class OnboardingRoute(val fromSettings: Boolean = false) : AppRoute

// --- top level ----------------------------------------------------------
@Serializable data object HomeRoute : AppRoute

/**
 * Search form. [mode]: one of [SearchModes]; [importUrl] pre-fills "From a link"
 * (chat `import_link`, share intents).
 */
@Serializable data class SearchRoute(val mode: String? = null, val importUrl: String? = null) : AppRoute

/** Streamed results for the search currently held by `SearchRepository` / `CurrentPlanStore`. */
@Serializable data object ResultsRoute : AppRoute

/** Compare table for 2–3 destinations of the current compare request. */
@Serializable data object CompareRoute : AppRoute

/** "My trips": saved plans list. */
@Serializable data object PlansRoute : AppRoute

@Serializable data class PlanDetailRoute(val planId: Long) : AppRoute

/** Chat; null [threadId] = the most recent thread (or a new one). */
@Serializable data class ChatRoute(val threadId: Long? = null) : AppRoute

/** Watched prices. Deep link parity with web `/alerts?kind=<kind>&watch=<id>`. */
@Serializable data class AlertsRoute(val kind: String? = null, val watchId: Long? = null) : AppRoute

@Serializable data object NotificationsRoute : AppRoute

// --- settings -----------------------------------------------------------
@Serializable data object SettingsRoute : AppRoute

@Serializable data object PreferencesRoute : AppRoute

@Serializable data object TasteProfileRoute : AppRoute

@Serializable data object FeaturesRoute : AppRoute

@Serializable data object AppearanceRoute : AppRoute

@Serializable data object ServerRoute : AppRoute

@Serializable data object NotificationSettingsRoute : AppRoute

// --- nearby / share -----------------------------------------------------
@Serializable data object NearbyRoute : AppRoute

/** Public share-link viewer (`/share/{token}`). */
@Serializable data class ShareRoute(val token: String) : AppRoute

/** Search form modes (web segmented toggle + "Surprise me"). */
object SearchModes {
    const val KNOWN = "known"
    const val SURPRISE = "surprise"
    const val COMPARE = "compare"
    const val IMPORT = "import"
}

/** Deep link scheme/host constants (app manifest + NavHost must agree). */
object DeepLinks {
    const val SCHEME = "travelplanner"
    const val SHARE_PATTERN = "$SCHEME://share/{token}"
    const val ALERTS_PATTERN = "$SCHEME://alerts?kind={kind}&watch={watchId}"
    const val PLAN_PATTERN = "$SCHEME://plan/{planId}"
    const val CHAT_PATTERN = "$SCHEME://chat"
    const val NEARBY_PATTERN = "$SCHEME://nearby"
}
```

Graphs (each feature exports exactly one; `app` calls them in `TravelApp.kt`):

| Feature | Function | Routes |
|---|---|---|
| auth | `authGraph(onLoggedIn: () -> Unit, onPasswordChanged: () -> Unit)` | `LoginRoute`, `ChangePasswordRoute` |
| onboarding | `onboardingGraph(onFinished: (fromSettings: Boolean) -> Unit)` | `OnboardingRoute` |
| home | `homeGraph(onPlanTrip, onOpenNearby, onOpenPlan: (Long) -> Unit)` | `HomeRoute` |
| search | `searchGraph(onSearchStarted, onCompare)` | `SearchRoute` |
| results | `resultsGraph(onBack)` | `ResultsRoute`, `CompareRoute` |
| plan | `planGraph(onOpenPlan: (Long) -> Unit, onBack)` | `PlansRoute`, `PlanDetailRoute` |
| chat | `chatGraph(onOpenImport: (url: String) -> Unit)` | `ChatRoute` |
| alerts | `alertsGraph(onReplan, onBack)` | `AlertsRoute` (deep link), `NotificationsRoute` |
| settings | `settingsGraph(onBack, onRetakeQuiz, onSignOut)` | `SettingsRoute` + 6 sub-routes |
| nearby | `nearbyGraph(onBack)` | `NearbyRoute` (deep link) |
| share | `shareGraph(onBack)` | `ShareRoute` (deep link, public) |

Registering a graph: in your module, `fun NavGraphBuilder.xGraph(...) { composable<XRoute> { entry -> val r = entry.toRoute<XRoute>(); XRoute(...) } }`;
deep links via `composable<XRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.… }))`.
Top-level destinations (navigation suite: bar on compact, rail on medium/expanded) are `HOME, SEARCH, CHAT,
PLANS (My trips), ALERTS`; the shell draws the top app bar (title + Settings action) for them, so top-level
screens must **not** draw their own top bar. Other screens use `TpTopBar(title, onBack = …)`.

Auth gate (in `app`): no session → Login; `requires_password_change` → Change password; onboarding not
completed on this device → Onboarding; else Home. It reacts to the session/profile flows: just call
`AuthRepository.login/logout/changePassword` or `ProfileRepository.saveProfile(profile.copy(onboardingCompleted = true))`.
The share viewer stays reachable signed-out. Deep links: `travelplanner://share/{token}`,
`travelplanner://alerts?kind={kind}&watch={watchId}`, `travelplanner://nearby` (`plan/{id}` and `chat`
are declared in the manifest; the plan/chat teams add their `navDeepLink`s).

## 6. Design system (`….core.designsystem`) and shared UI (`….core.ui`)

- Theme: `TravelTheme(themeMode, dynamicColor) { … }` (app root only). Accessors:
  `TravelTheme.colors` (M3 `ColorScheme`: primary teal-600/teal-400 dark, secondary sky, tertiary violet, error red-700),
  `TravelTheme.typography` (Sora display/headline, Inter title/body/label via Google downloadable fonts, system fallback),
  `TravelTheme.shapes`, `TravelTheme.extendedColors` (`success`, `warning` (Estimate/over budget), `info`,
  `skeletonBase/Highlight`, containers), `TravelTheme.spacing` (`xxs 2, xs 4, sm 8, md 12, lg 16, xl 24, xxl 32,
  gutter 16, minTouchTarget 48, accentBar 4` dp), `TravelTheme.sectionAccents[SectionId]` → `SectionAccent(bar, icon, tint)`
  (spec §5 map). Named shapes: `TravelShape.Input (8) / ItemCard (12) / LargeCard (24) / Pill`.
- Components: `TpButton(text, onClick, modifier, variant = Primary|Secondary|Tonal|Text|Destructive, enabled, loading, leadingIcon)`,
  `TpChip(label, selected, onClick, …)`, `TpCard(modifier, size = Item|Large, selected, accent, onClick) { … }`,
  `TpTopBar(title, modifier, onBack, actions)`, `SkeletonBlock`, `SkeletonList(message, lines)`, `Modifier.shimmer()`
  (static under reduce-motion), `rememberReduceMotion()`, `LoadingIndicator(label)`, `EmptyState(title, message, icon, actionLabel, onAction)`,
  `ErrorState(message, title, onRetry)` (polite live region).
- Shared UI: `SectionScaffold(section, status, expanded, onToggle, icon, badge, headerActions) { content }`
  (accent bar, heading semantics, expand/collapse state description), `SectionStatusIndicator`, `SectionId.titleRes()`,
  `VerificationChip(verification, onClick)`, `PriceText(amount, currency, isEstimate, cents, style, hideWhenUnknown)`,
  `BookingLinkButton(url, providerName, variant)`, `openInCustomTab(context, url)`, `rememberLinkOpener()`,
  `AddToPlanButton(selected, itemName, onToggle)`, `StreamingStatusAnnouncer(statuses)` ("Hotels ready"),
  `FeaturePlaceholder(...)` (delete once your screens exist).
- Icons: `material-icons-core` only (`Icons.Filled.*`, `Icons.AutoMirrored.Filled.*`); add a vector drawable to your
  module if you need more. Every icon-only button needs a `contentDescription` string resource.
- Price formatting: `formatPrice(amount, currency = "USD", cents = false)` (`….core.domain.format`, web parity).

## 7. Strings

- All UI text in your module's `src/main/res/values/strings.xml` **and** `values-es/strings.xml`
  (MissingTranslation is an error). Prefix names with your feature (`search_…`). Reuse web copy
  (`web-ui-inventory.md` §3/§5). Counts use `<plurals>` (Spanish needs `one`, `many`, `other`).
- Use `stringResource(R.string.x)` / `pluralStringResource(...)`; RTL-safe layouts (start/end).
- Backend `detail` strings (`AppError.message`) are shown as-is (web parity); otherwise use your resource.

## 8. Results sections contract (`feature:results`, package `….feature.results.sections`)

```kotlin
/** The 16 results sections in web `AGENT_ORDER` (api-contract §3.2). */
enum class ResultsSection(val id: SectionId) {
    Flights(SectionId.FLIGHTS),
    Weather(SectionId.WEATHER),
    Hotels(SectionId.HOTELS),
    Activities(SectionId.ACTIVITIES),
    PlacesToSee(SectionId.PLACES_TO_SEE),
    WalkingTours(SectionId.WALKING_TOURS),
    Events(SectionId.EVENTS),
    Visa(SectionId.VISA),
    Sim(SectionId.SIM),
    Tips(SectionId.TIPS),
    EmergencyCard(SectionId.EMERGENCY_CARD),
    GettingAround(SectionId.GETTING_AROUND),
    Forex(SectionId.FOREX),
    Itinerary(SectionId.ITINERARY),
    StressTest(SectionId.STRESS_TEST),
    PackingList(SectionId.PACKING_LIST),
    ;

    companion object {
        fun of(id: SectionId): ResultsSection = entries.first { it.id == id }
    }
}

/**
 * Everything a section composable may do outside itself. Implemented once by the
 * results screen (backed by CurrentPlanStore, Custom Tabs and the watch dialogs).
 */
interface SectionActions {
    /** Add/remove from My Plan (single-choice categories replace). */
    fun onToggle(item: PlanItem)

    fun isSelected(item: PlanItem): Boolean

    /** Open a provider/info page in a Custom Tab. */
    fun onOpenLink(url: String)

    /** Start a fare watch for this flight (shown only when `/health.fare_watch_available`). */
    fun onWatchFare(flight: Flight)

    fun onWatchHotel(hotel: Hotel)

    fun onWatchActivity(activity: Activity)

    /** Re-run this section via its single-agent route. */
    fun onRetry(section: ResultsSection)
}
```

One file per section, each owning exactly one function — replace the whole file, keep the signature:
`FlightsSection.kt` … `PackingListSection.kt` with
`@Composable internal fun <Name>SectionContent(data: <Name>Data, actions: SectionActions, modifier: Modifier = Modifier)`
for `Flights(FlightsData)`, `Weather(WeatherData)`, `Hotels(HotelsData)`, `Activities(ActivitiesData)`,
`PlacesToSee(PlacesData)`, `WalkingTours(WalkingToursData)`, `Events(EventsData)`, `Visa(VisaData)`,
`Sim(SimData)`, `Tips(TipsData)`, `EmergencyCard(EmergencyCardData)`, `GettingAround(GettingAroundData)`,
`Forex(ForexData)`, `Itinerary(ItineraryData)`, `StressTest(StressTestData)`, `PackingList(PackingListData)`.
`SectionRegistry.kt` (`ResultsSectionCard`, `SectionContent`) maps each `ResultsSection` into `SectionScaffold`
(skeleton while loading, `ErrorState` + `onRetry` on error). Use `actions.isSelected(PlanItem.HotelItem(hotel))`
with `AddToPlanButton`, `actions.onOpenLink(url)` for booking.

## 9. Domain interfaces to implement (`core:domain`)

```kotlin
enum class TravellerType { BUDGET_BACKPACKER, FAMILY, LUXURY, ADVENTURE, CULTURE_FOODIE, BUSINESS, RELAXATION, ACCESSIBLE }
data class TravellerClassification(val primary: TravellerType, val secondary: List<TravellerType>)
fun interface TravellerClassifier { fun classify(answers: Map<String, List<String>>): TravellerClassification }

enum class FeatureId(val id: String) { DEAL_ALERTS, BEST_VALUE, BUDGET_PLANNER, COMPARE, SURPRISE_ME, IMPORT_LINK, CHAT,
    NEAR_ME, PACKING_LIST, EMERGENCY_CARD, WALKING_TOURS, EVENTS, VISA_CHECKER, FOREX, SIM, TRIP_HEALTH_CHECK,
    ITINERARY_EDITOR, ACCESSIBILITY_INFO }            // ids are snake_case, stored in FeatureOverrides
data class FeatureDescriptor(val id: FeatureId, val titleKey: String, val iconName: String, val defaultOnFor: Set<TravellerType>)
interface FeatureCatalogue {
    val all: List<FeatureDescriptor>
    fun defaultsFor(types: Collection<TravellerType>): Set<FeatureId>
    fun visible(types: Collection<TravellerType>, overrides: FeatureOverrides): Set<FeatureId> // default impl: defaults ∪ enabled − disabled
}
```

The onboarding/settings team owns the implementations (and a Hilt `@Binds` for them in `core:domain`'s
consumer — add a small Hilt module in your feature or ask to add one in `core:data`).

## 9b. Room schema changes

The schema is exported to `core/database/schemas/`. Every `version` bump ships a `Migration` (or
`@AutoMigration`) **and** a `MigrationTestHelper` test against the previous `schemas/…/N.json`.
`fallbackToDestructiveMigration` is not allowed (chat history is local-only user data); only
`fallbackToDestructiveMigrationOnDowngrade()` is acceptable. Add new entities through the foundation owners.

## 9c. Streams and releases

- Streams (`SearchRepository`, `ChatRepository.send`) cancel the HTTP call as soon as the collector is
  cancelled; a connection idle for 300 s (`NetworkModule.STREAM_IDLE_SECONDS`) ends as `AppError.Interrupted`.
- R8: `core:model` `@Serializable` classes are kept by `app/proguard-rules.pro`; `./gradlew :app:verifyReleaseKeepRules`
  (run in CI) fails if a serializer is stripped. Put new wire models in `core:model` so the rules cover them.

## 10. Testing (`core:testing`, already a `testImplementation` of every feature)

- `MainDispatcherRule()` (JUnit rule), `TestAppDispatchers(dispatcher)`.
- Fakes (`….core.testing.fake`): `FakeAuthRepository(initialSession)` (`sessionFlow`, `loginResult`, …),
  `FakeSearchRepository(events = fixture)` (folds with the real reducer; `failure`, `sectionResult`, …),
  `FakeChatRepository(replies = fixture)` (`sent`, `failure`, in-memory history), `FakePlansRepository(initial)`
  (`failNext`, `sharedPlans`), `FakeCurrentPlanStore(plans)`, `FakePreferencesRepository`, `FakeRecentSearchesRepository`,
  `FakeFareWatchesRepository`, `FakePriceWatchesRepository`, `FakeNotificationsRepository`, `FakeDevicesRepository`,
  `FakeNearbyRepository(result)`, `FakeCompareRepository`, `FakeDiscoverRepository`, `FakeImportRepository`,
  `FakeFareCalendarRepository`, `FakeHealthRepository(initial)`, `FakeSettingsRepository`, `FakeProfileRepository`,
  `FakeFeedbackRepository`; store fakes for core tests (`FakeSessionStore` (tracks `lastUser`), `FakeSettingsStore`, …); `TestUser`, `NotStubbed`.
  `FakeCurrentPlanStore` implements `reset()`.
- Fixtures: `Fixtures.searchEvents(Fixtures.SEARCH_FULL | SEARCH_INTERRUPTED)`,
  `Fixtures.chatEvents(Fixtures.CHAT_QA | CHAT_PLANNING | CHAT_FALLBACK)`, `Fixtures.read(name)` (raw SSE text).
- `TestNetwork(baseUrl, sessionStore, dispatchers)` — the real OkHttp/Retrofit stack for MockWebServer tests.
- ViewModel tests: Turbine (`flow.test { … }`); Compose tests: `createComposeRule()` + `@RunWith(AndroidJUnit4::class)`
  + `@Config(application = android.app.Application::class)` on Robolectric (see `app/src/test/…/AppShellTest.kt`).
