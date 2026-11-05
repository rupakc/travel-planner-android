# Travel Planner for Android — design

Status: approved scope (user answers, 2026-11-05). Grounding documents:
`docs/reference/api-contract.md` (backend contract, read from code) and
`docs/reference/web-ui-inventory.md` (web UI, tokens, copy). Anything not in
those two files or in this spec is a builder's call and must be recorded in
`MEMORY.md` as a decision.

## 1. Intent

A fast, native Android companion to the travel-planner web app ("Voyager").
The same people use the same account, the same backend and the same data:
a plan saved on the phone opens on the web and vice versa. The phone adds what
a phone is good at: a personalised home built from a short onboarding, trip-time
notifications, and suggestions near where the traveller is.

## 2. Decisions the user made

| Topic | Decision |
|---|---|
| Booking | Deep-link out, as the web does: open the provider page (Google Flights, hotel page, GetYourGuide/Viator listing…) in a Custom Tab, then keep the item in My Plan. No payments, no partner APIs. "Book" always means "open the provider's page". |
| Push | Both: on-device notifications that work now (WorkManager polling + local scheduling) **and** Firebase Cloud Messaging fully wired but inert until `google-services.json` (app) and a service-account key (backend) are supplied. |
| Repo | Separate public GitHub repo `rupakc/travel-planner-android`, MIT, local folder `~/travel-planner-android`. |
| Toolchain | JDK 21 (Temurin) + Android SDK in the user's home; build and test locally. |

## 3. Goals, non-goals, anti-goals

Goals
- Feature parity for the traveller-facing web flows: login / change password, Search (known destination, Surprise me, Compare, From a link), streamed Results with all sections, My Plan (select, save, load, share, delete, `.ics` export), Chat (streamed, plan commands), Alerts (fare + hotel/activity/trip price watches, notifications bell), Preferences (incl. Taste Graph view/reset), public share-link viewer.
- New: traveller-type onboarding (§6), feature visibility per type with user override (§6.3), trip-time and price notifications (§7), "Near me" recommendations (§8).
- Blazingly fast: cold start < 1 s on a mid-range device in release, 60 fps scrolling, first streamed section painted as soon as it arrives, offline read of saved plans.
- Same look and feel as the web (teal primary, Inter/Sora, accent colour per section, same copy), adapted to Material 3, with a dark theme.

Non-goals
- Admin pages (user management, feedback inbox) stay web-only. Rationale: admin work is rare and desktop-shaped; the API is unchanged so it can be added later as `feature:admin`.
- No in-app payments or bookings (§2).
- No self-registration (the backend has none; admins create accounts).

Anti-goals (would be failure even if it "works")
- Inventing data: every price, link and fact shown must come from the API. Estimates stay labelled as estimates, exactly as on the web (verification chip, "Estimate" labels).
- A second source of truth: preferences, plans, watches and notifications live on the backend; local storage is a cache plus device-only settings.
- Forking the backend: backend changes are additive endpoints in the existing repo, shipped as a normal PR with tests, so the web keeps working unchanged.

## 4. Architecture

Kotlin 2.4, Jetpack Compose (BOM 2026.09.00), Material 3 + material3-adaptive
(NavigationSuiteScaffold, list-detail), Navigation Compose with type-safe routes,
Hilt 2.60 (KSP), Coroutines/Flow, kotlinx.serialization, OkHttp 5 + Retrofit 3,
Room 2.8, DataStore 1.3, WorkManager 2.12, Coil 3, Custom Tabs (androidx.browser),
Play Services Location, Firebase Messaging (BOM 35.1.0, optional). AGP 9.5,
Gradle 9.8, compileSdk/targetSdk 36, minSdk 26. Convention plugins in
`build-logic/`, versions in `gradle/libs.versions.toml`.

Pattern: unidirectional data flow, MVVM. Screen = stateless `XxxScreen(state, onEvent)`
+ `XxxRoute` that wires a `@HiltViewModel` exposing `StateFlow<XxxUiState>`.
Repositories are interfaces in `core:data`, implemented over network + cache;
features depend on interfaces only (fakes in `core:testing`).

### Modules

```
app                     Application, MainActivity, NavHost, adaptive scaffold, DI graph root
build-logic/convention  android-application/library/feature/compose/hilt/room/jvm-library plugins
core/common             dispatchers, Result/AppError, clock, logging
core/model              pure Kotlin domain + wire models (kotlinx.serialization, tolerant)
core/network            OkHttp client, auth interceptor, base-URL provider, Retrofit API, SSE line parser + stream clients
core/database           Room: plans cache, chat threads/messages, recent searches, notification cache
core/datastore          session (token, user), app settings (base URL, theme, locale), traveller profile, feature overrides
core/data               repositories (Auth, Search, Chat, Plans, Preferences, Alerts, Notifications, Nearby, Devices, Profile)
core/domain             pure use cases + ported web utils (planHelpers cost, budgetPlanner, bestValuePackage, schedule, ics export, compare, traveller classifier, feature catalogue)
core/designsystem       theme (colour tokens, typography, shapes, spacing, section accents), core components (buttons, chips, cards, skeleton, empty/error states)
core/ui                 shared feature UI: section scaffold, verification chip, price text, booking-link button, place/hotel/activity cards
core/notifications      channels, notifier, trip reminder scheduler, alert poller worker, FCM service (optional)
core/testing            fakes, test dispatcher rule, fixtures (recorded SSE streams from api-contract)
feature/auth            login, change password
feature/onboarding      traveller-type questionnaire + result + feature picker
feature/home            personalised home (feature tiles per traveller type, upcoming trip, quick actions)
feature/search          search form (4 modes), recent searches
feature/results         streamed results, sections, best-value, budget planner, compare table
feature/plan            My Plan sheet, saved plans list (search + filter), plan detail, share, export
feature/chat            chat threads, streamed messages, chips, plan actions
feature/alerts          watches list with tabs/filters, watch dialogs, notifications inbox
feature/settings        preferences (server), taste graph, traveller type + features, theme, language, server URL, notifications
feature/nearby          location permission, near-me recommendations
feature/share           public share-link viewer (deep link https://…/share/{token} and app link)
```

Dependency rule: `feature:*` → `core:{ui,designsystem,data,domain,model,common}` only;
features never depend on each other — cross-feature navigation goes through
route objects in `core:model` (`navigation` package) consumed by `app`.

### Networking
- Base URL is a setting (Settings → Server), defaulting per flavour. Two product flavours, because Android's cleartext policy is fixed at build time: `local` (default `http://10.0.2.2:8001`, cleartext HTTP allowed — for today's local-only backend, an emulator or a phone on the LAN) and `prod` (no default host baked in beyond the build property `PROD_BASE_URL`, HTTPS only). Both flavours share all code.
- Auth: `Authorization: Bearer` from DataStore; any 401 clears the session and routes to Login. `requires_password_change` routes to Change Password.
- SSE: POST + `text/event-stream`, one `data: {json}` per event, `:` comments ignored, no read timeout (call timeout off, ping via keepalive), cancel on scope cancel. Search: section events replace earlier ones by type; end on `done`; a stream ending without `done` is retried once (web parity). Chat: end on `done` **or** `planning_done`.
- Errors: map `detail` string | list | `{code,message}` into `AppError` with user copy; 429 shows the backend string and honours `Retry-After`.
- Tolerant decoding: `ignoreUnknownKeys`, `coerceInputValues`, `isLenient`, nullable fields, numbers-as-strings accepted via custom serializers.

### Performance
Baseline profile + R8 full mode in release; Compose stability (immutable UI models, `@Immutable`), `LazyColumn` keys and content types, image sizing in Coil, startup work deferred via App Startup/WorkManager, StrictMode in debug. SSE parsing on `Dispatchers.Default`.

### Scale (10 M users)
The app is stateless against the API, caches per user, backs off on 429 and
uses jittered WorkManager schedules (no synchronized polling). FCM moves alert
delivery from polling to push at scale. The backend today is single-node
SQLite with in-process rate limits and cache; `docs/Scaling.md` states plainly
what the backend must change (Postgres, Redis for rate limit/cache, horizontal
API nodes, a push queue) — the app's contract does not change when it does.

## 5. Look and feel
- Colour: Material 3 scheme built from the web tokens: primary teal-600 #0d9488 (dark: teal-400 #2dd4bf), secondary sky-600 #0284c7, tertiary violet; error red-700 #b91c1c; surfaces gray-50/white. Section accents exactly as the web map (Flights sky, Hotels violet, Activities emerald, Places lime, Walking tours teal, Events fuchsia, Visa amber, SIM rose, Tips amber, Getting around cyan, Forex emerald, Itinerary teal, Trip health rose, Emergency red, Packing teal, Weather sky). Optional dynamic colour toggle (off by default to keep brand).
- Type: Sora for display/headline, Inter for body/title/label via Google Fonts downloadable fonts (fallback system sans).
- Shapes: 24 dp large cards, 12 dp item cards, 8 dp inputs, pills fully rounded.
- All tokens in `core:designsystem` (`TravelTheme`, `LocalSectionAccents`, `Spacing`); no hard-coded colours or dp in features (lint rule / review rule).
- Copy: reuse the web's strings (inventory §3/§5). All user-facing text in `strings.xml`; `en` (default) and `es` shipped; plurals via `<plurals>`; pseudo-locales enabled in debug.

## 6. Onboarding and traveller types

### 6.1 Flow
After the first successful login on a device (and from Settings any time):
Welcome → 6 questions (one per screen, skippable) → result ("You're a Culture Seeker + Foodie") → feature picker (pre-checked by type, editable) → Home.
Answers also pre-fill server Preferences (interests, traveller counts, budget_category, accessibility_needs, nationality if given) via `PUT /api/preferences`, so the web benefits.

Questions (single/multi choice):
1. Who do you usually travel with? solo / partner / family with kids / friends group / for work
2. Typical budget? shoestring / mid-range / comfort / luxury → budget_category low/medium/medium/high
3. What do you love doing? multi: food, history & culture, nature & outdoors, adventure & sports, nightlife, shopping, art & museums, beaches & relaxation, photography, wellness (mapped to the web's 10 interest ids)
4. Trip pace? relaxed / balanced / packed → search `pace`
5. How do you plan? book far ahead, hunt deals / plan loosely / spontaneous, last-minute
6. Anything we should plan around? multi: step-free access, limited walking, visual, hearing (the web's 4 accessibility ids), travelling with infants, dietary needs (none)

### 6.2 Classification
Pure, deterministic, unit-tested `TravellerClassifier` in `core:domain`: weighted score per type from answers; primary type = highest score, secondary = second if ≥ 60 % of primary; ties broken by a fixed order. Types:
`BUDGET_BACKPACKER, FAMILY, LUXURY, ADVENTURE, CULTURE_FOODIE, BUSINESS, RELAXATION, ACCESSIBLE`.
`ACCESSIBLE` is added as a secondary whenever any accessibility need is chosen (never hides other types).

### 6.3 Feature catalogue and visibility
`FeatureCatalogue` (core:domain) lists every optional surface with id, title, icon, and default-on per type, e.g.:
deal_alerts (fare/price watches), best_value, budget_planner, compare, surprise_me, import_link, chat, near_me, packing_list, emergency_card, walking_tours, events, visa_checker, forex, sim, trip_health_check, itinerary_editor, accessibility_info.
Core surfaces (Search, Results, My Plan, Settings) are never hidden. Visible set = type defaults ∪ user-enabled − user-disabled; stored device-locally in DataStore (the server Preferences schema has no field for it; v1 keeps it on the device and documents that). Home tiles, Results section order (type-relevant sections first, others collapsed, never removed) and the Search mode chips follow the visible set. Settings → "Your traveller type" re-runs the quiz; Settings → "Features" toggles any feature at any time.

## 7. Notifications
Channels: Trip reminders, Price alerts, Nearby suggestions, Account.
- Price alerts: `AlertPollWorker` (periodic 30 min, network-constrained, jittered) reads `/api/notifications` and posts new unread ones locally (deduped by id in Room). When FCM is configured, the server pushes and the worker drops to a 6 h safety net.
- Trip reminders (local, from saved plans with dates): 7 days before (visa/packing check), 1 day before (flight + weather), morning of each trip day (that day's itinerary), last day (checkout). Scheduled with WorkManager one-time work keyed by plan id; rescheduled on plan save/delete and boot.
- Nearby: during a trip day (or when enabled always), at most 1 per 4 h, from §8.
- Android 13+ POST_NOTIFICATIONS asked in context (after onboarding feature picker, not at launch); deep links open the right screen.

Backend additions (travel-planner repo, one PR, tested):
- `devices` table + `POST /api/devices {token, platform, locale}` / `DELETE /api/devices/{token}` (auth).
- `services/push.py`: FCM HTTP v1 sender, active only when `FCM_SERVICE_ACCOUNT_FILE` is set; called where alert notifications are created; failures logged, never break alert creation; invalid tokens pruned.
- `POST /api/nearby {lat, lng, interests?, limit}` (auth, light_ai bucket): nearest known place via the existing places DB, then the existing activities agent with the user's interests + taste context; returns `{place, items[]}` with the same item shape as activities. 404 `no_place` when nothing is near.
- Docs: API-Reference, Configuration, Architecture, Changelog.

## 8. Near me
Coarse location (precise optional), foreground only, asked in context on the Near me screen. Results cached 1 h per ~5 km cell. Uses `/api/nearby`. Items open in Custom Tabs / Maps, can be added to the active plan.

## 9. Accessibility, i18n, adaptivity
- 48 dp touch targets, content descriptions on every icon button, semantic headings, live-region announcements for streaming sections ("Hotels ready"), respects font scale up to 200 % and reduce-motion (animator duration scale), contrast AA in both themes.
- Window size classes: compact = bottom bar + single pane; medium = nav rail; expanded = rail/drawer + list-detail (results sections | detail, plans | plan).
- Locales: `en`, `es`; RTL-safe layouts (start/end only).

## 10. Quality
- Unit: domain (classifier, cost, ics, schedule…), SSE parser (fixtures), repositories (MockWebServer), ViewModels (Turbine).
- UI: Compose tests on Robolectric for every screen state; a few instrumented smoke tests on the emulator.
- E2E: emulator against a local backend (isolated DATA_DIR, fake SerpAPI) covering login → onboarding → search stream → add to plan → save → web sees plan.
- Static: Android Lint (warnings as errors for a11y/i18n), ktlint via Spotless, detekt; CI on GitHub Actions (build, lint, unit tests, assembleRelease).
- Five self-review rounds: per-task reviews + final whole-repo reviews by fresh reviewers (architecture, UI/a11y, API contract, security, performance).

## 11. Claude Code and docs
Repo ships `CLAUDE.md`, `MEMORY.md` (decision log), `.claude/agents/` (android-architect, compose-ui-builder, api-contract-checker, android-reviewer, a11y-i18n-auditor), `.claude/skills/` (verify-android, add-feature-module, add-api-endpoint, release), README, CONTRIBUTING, `docs/` (Architecture, Modules, Networking-and-SSE, Onboarding-and-Traveller-Types, Notifications-and-Push, Theming, Localization, Accessibility, Testing, Scaling, Release, Firebase-Setup) synced to the GitHub wiki.

## 12. Left to the builder
Exact question copy and scoring weights (must be in one table in code and docs), icon choices, animation details, cache TTLs not stated above.
