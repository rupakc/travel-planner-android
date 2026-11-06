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
| `GET /health` | `HealthApi` | `HealthRepository` (capability flags, e.g. `serpapi_configured`, `push_configured`) |
| `/api/admin/*` | `AdminApi` | none: wire models exist, no admin UI (non-goal); R8 removes it from release builds |

## Adding an endpoint

Use the `add-api-endpoint` Claude skill: add the wire model (tolerant, lenient numbers), the Retrofit method on the right client
(`@DefaultHttp` or `@AiHttp`), the repository method returning `AppResult`, a fake in `core:testing`, a MockWebServer test, then update this table and
[api-contract](reference/api-contract.md) if the contract changed. If a backend change is required, open it in the web repo first.

## Honest-data rules

Every price, link and fact comes from the response. Estimates and AI content keep the web's labels; `verification` is rendered as the chip.
Price watches without a live price return `422 no_live_price`; surface it, never fabricate a price.
