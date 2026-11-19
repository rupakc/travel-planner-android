# Backend integration

The app talks to the existing [travel-planner](https://github.com/rupakc/travel-planner) backend; it never forks it. Authoritative references:
the web repo's [API-Reference](https://github.com/rupakc/travel-planner/blob/main/docs/API-Reference.md) and, in this repo,
[api-contract](reference/api-contract.md) (read from the backend code). Changes the app needs are additive endpoints shipped as a normal backend PR with tests.
Retrofit declarations are in `core/network/.../api/Apis.kt`; wire models in `core:model`.

## Endpoint to repository map

| Endpoints | API interface | Repository (`core:data`) |
|---|---|---|
| `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/change-password`, `PUT /api/auth/email` | `AuthApi` | `AuthRepository` |
| `POST /api/search` (SSE), `POST /api/search/sync`, single-agent routes (`flights`, `hotels`, `activities`, `visa`, `sim`, `tips`, `getting-around`, `forex`, `itinerary`, `events`, `walking-tours`), `*/filtered`, `flights/fare-calendar`, `stress-test`, `layover` | streaming client, `SearchApi` | `SearchRepository`, `FareCalendarRepository` |
| `GET /api/airports/search`, `GET /api/nationalities/search` | `ReferenceApi` | `SearchRepository.airports/nationalities` |
| `POST /api/chat` (SSE) | streaming client | `ChatRepository` (history is device-local in Room) |
| `GET/POST /api/plans`, `GET/PUT/DELETE /api/plans/{id}`, `POST/DELETE /api/plans/{id}/share`, `GET /api/share/{token}` | `PlansApi` | `PlansRepository` (+ `CurrentPlanStore`) |
| `GET/PUT /api/preferences`, `GET/DELETE /api/taste-profile` | `PreferencesApi` | `PreferencesRepository` |
| `GET/POST /api/recent-searches`, `DELETE /api/recent-searches/{id}` | `PreferencesApi` | `RecentSearchesRepository` |
| `/api/fare-watches*` | `FareWatchApi` | `FareWatchesRepository` |
| `/api/price-watches*` | `PriceWatchApi` | `PriceWatchesRepository` |
| `GET /api/notifications`, `/unread-count`, `POST /read` | `NotificationsApi` | `NotificationsRepository` |
| `POST /api/devices`, `GET /api/devices`, `DELETE /api/devices/{id}`, `POST /api/devices/unregister` | `DevicesApi` | `DevicesRepository` |
| `POST /api/nearby` (v2: `sections: true`) | `NearbyApi` | `NearbyRepository` (no cache) |
| `POST /api/live-check` | `LiveCheckApi` | `LiveCheckRepository` (no cache) |
| `POST /api/discover`, `POST /api/compare/destination` | `SearchApi` | `DiscoverRepository`, `CompareRepository` |
| `POST /api/import/extract`, `/import/resolve-place` | `ImportApi` | `ImportRepository` |
| `POST /api/feedback`, `POST /api/analytics/events` | `FeedbackApi` | `FeedbackRepository` |
| `POST /api/carbon/trip` | `CarbonApi` | `CarbonRepository` (only when `features.carbon >= 1`; see [Trip carbon footprint](#trip-carbon-footprint-featurescarbon--1)) |
| `GET /api/config` | `ClientConfigApi` | `ClientConfigRepository` (telemetry settings; a 404 means off) |
| `POST /api/telemetry` (gzip) | `TelemetryApi` | `Telemetry` (queue, uploader, worker); see [Telemetry-and-Privacy](Telemetry-and-Privacy.md) |
| `GET /api/auth/providers` | `AuthProvidersApi` | `AuthProvidersRepository` (feature-detected; 404 or error = all four providers "coming soon") |
| `GET /health` | `HealthApi` | `HealthRepository` (capability flags, e.g. `serpapi_configured`, `push_configured`) |
| `/api/admin/*` | `AdminApi` | none: wire models exist, no admin UI (non-goal); R8 removes it from release builds |

## The `locale` field

`AiLocaleInterceptor` adds `"locale": <base language code>` to the JSON body of POSTs to the AI paths (search, search/sync, chat, discover, compare/destination, stress-test, flights, hotels, activities
(+ `/filtered`), visa, sim, tips, getting-around, forex, itinerary, events, walking-tours). It runs after auth and before the base-URL interceptor, only for the app's own host, never overrides an existing `locale`,
and leaves non-JSON bodies (multipart import) alone, so draft `searchKey`s are unchanged. Older backends ignore the unknown field. zh-TW devices resolve to `zh`.

## Adding an endpoint

Use the `add-api-endpoint` Claude skill: add the wire model (tolerant, lenient numbers), the Retrofit method on the right client
(`@DefaultHttp` or `@AiHttp`), the repository method returning `AppResult`, a fake in `core:testing`, a MockWebServer test, then update this table and
[api-contract](reference/api-contract.md) if the contract changed. If a backend change is required, open it in the web repo first.

## Honest-data rules

Every price, link and fact comes from the response. Estimates and AI content keep the web's labels; `verification` is rendered as the chip.
Price watches without a live price return `422 no_live_price`; surface it, never fabricate a price.

## Trip carbon footprint (`features.carbon >= 1`)

Port of the web `CarbonFootprint` card (web `docs/Carbon-Footprint.md`). The backend does all the arithmetic; `CarbonRequests`
(`core:domain`, a port of the web `utils/carbon.js`) only trims the raw JSON the screen holds: on Results, once the stream is done (or the
results came from the draft), the cheapest flight (per leg on multi-city; a missing or 0 price sorts last), the first hotel and the
itinerary; in My Plan, the picked flight(s), hotel, transport and activities with the live itinerary (else the plan's saved one), and only
once a flight or hotel is picked. `cardStates` (`core:data` `carbon/`) is the one state machine both screens use: Hidden first and while the flag
is off (no request is sent; the flag is read again on each new body and after 5/15/30/60 s, so a failed `/api/config`, which is not
cached, does not hide the card for the whole visit), a 350 ms debounce, the latest request wins, a 404 hides the card permanently (older backend or
flag turned off), other failures show "The footprint isn't available right now." with Try again. The meals choice is device-local
(`carbon_prefs` DataStore, the web's `tp_carbon_diet`) and shared by both cards. `CarbonFootprintCard` (`core:ui` `carbon/`) renders the
total, per person, the breakdown bar (described for TalkBack) with a legend, the car-km and yearly-budget comparisons, the train hint,
"Includes newer official data from the Voyager crawler" with each crawler source line (name, link, as-of date, attribution) when
`crawler_sources` is present, the diet chips and "How we calculate" (each line with its method badge and its own `crawler_source`, the
assumptions, the sources with licence and attribution). Lines and assumptions are translated from `kind`/`code` + `params`; an unknown one
shows the backend's English `label`/`text`. Responses without the crawler fields render as before. Flight cards show the Google Travel Impact
Model chip "≈ N kg CO₂e" (`EmissionsChip`) when a result carries `emissions`; a tap shows the per-class breakdown and the CC BY-SA credit,
linked to TIM. Strings: `core/ui/src/main/res/values*/strings_carbon.xml`, copied from the web's `results:carbon.*`, `emissions.*` and
`official.source|asOf` in all seven languages.

## Share links 2.0 (`features.sharing >= 1`)

`SharesApi` (`POST/GET /shares`, `DELETE /shares/{token}`) is called only when `JourneyFlags.sharing` is on; the app shell then provides `LocalShareLauncher` (`core:ui`), which opens `ShareCreateRoute` (`feature:share`). Without the flag the launcher is null: the plan keeps the legacy `POST /plans/{id}/share` link, the recap keeps its text share and the savings tally shows no share button. Entry points: plan detail, trip recap (the trip id is the plan id), savings tally; each reads the launcher, so features never depend on each other.

The dialog creates (idempotent: a second call replaces the options) and shows the server's absolute URL. "Share…" downloads the `size=story` card with the app's OkHttp client without interceptors (no bearer token; `http(s)` only, `image/png`, 8 MB cap, one file in `cache/share-cards/`, cleared on sign-out), hands it to the system share sheet through `ShareCardFileProvider` (authority `<applicationId>.share.cards`) with the link as text, and shares the link alone when the card cannot be fetched. Instagram Stories and TikTok Share Kit are out (they need developer apps). Errors: `sharing_disabled` and `plan_not_found` are translated, 429 and other server text is shown as is.

## Social and Play (Phase 5, docs/Social-API.md in the web repo)

`SocialApi` (`/api/social/*`, flag `features.social >= 1`) and `PlayApi` (`/api/play/*`, flag `features.play >= 1`, and the game must be visible) are in `core:network`; `SocialRepository` and `PlayRepository` (`core:data`) return `AppResult` and have scriptable fakes in `core:testing`. Failures carry the envelope `error_code`, read with `AppError.socialErrorCode()` (`SocialErrorCode`, `UNKNOWN` for new codes; `handle_rate_limited` also has `retryAfterSeconds()`). Nothing is cached or stored on the device. Admin moderation routes are not modelled (no admin UI).
