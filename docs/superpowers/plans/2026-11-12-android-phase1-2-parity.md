# Android Phase 1 + 2 parity (accounts, game, journey) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox syntax. Ledger: `.superpowers/sdd/` (git-ignored).

**Goal:** Bring the Android app to the web's Phase 1 (accounts, game, onboarding) and Phase 2 (trips journey) feature set, against the frozen contracts `docs/Accounts-API.md` and `docs/Trips-API.md` of `rupakc/travel-planner` (read them with `git show origin/main:docs/<file>` in `~/travel-planner`; the working tree there is stale). Spec authority: `docs/superpowers/specs/2026-11-10-voyager-reimagined-design.md` (web repo). Models for scope: web plans `2026-11-11-phase1-accounts-game.md`, `2026-11-12-phase2-journey.md` (owner rulings at its end), iOS plan `2026-11-11-ios-app.md`.

**Architecture:** Same modules as `CLAUDE.md`. New feature modules `feature:account`, `feature:trips`, `feature:play`; shared DTOs, APIs, repository interfaces and fakes land once in Task 0 so feature tasks own disjoint files. Everything new is feature-detected from `GET /api/config` (`features.accounts|game|trips|journey|offline_pack`, `auth.*`); against the previous backend the app behaves exactly as today.

## Global constraints

- **Mixed versions.** No call to a new endpoint unless the flag says it exists. Journey UI (tabs, hub, `/` routing) needs `features.trips && features.journey`; without it the five current tabs stay. `404 offline_pack_disabled` or `features.offline_pack=false` hides the offline tab, deletes every pack and the outbox.
- **Contracts are frozen and additive.** Unknown JSON keys are ignored (`TravelJson`); numeric/decimal strings use `Lenient*`; amounts are decimal strings, never floats.
- **Credentials do not exist** (OAuth, FCM, SMTP). All of it is config-gated and inert: providers `configured=false` show "coming soon"; `signup_available=false` hides sign-up; tests use fakes (MockWebServer/fake repositories), never real services.
- **Strings:** every string in `values/` (en), `values-es/` (hand) and de/fr/hi/zh/bn (web-synced into `strings_web.xml` where a web key exists via `config/i18n/web-keys/<module>.json`, else hand-translated). Light theme default, dark supported. `contentDescription`, 48 dp targets, 200 % font scale, no colour-only meaning. Backend `detail` shown as-is; clients map `error_code` to local copy where one exists (web `errors` namespace) and fall back to `detail`.
- **Telemetry:** every interaction through the existing SDK (tids, enum props); event names/props exactly as the server registry (`auth_start`, `auth_result`, `signup_step`, `email_verify`, `password_reset`, `account_action`, `quota_block`, `game_*`, `trip_view`, `trip_action`, `prep_toggle`, `today_action`, `offline_pack`, `outbox_flush`, `expense_action`, `journal_action`, `place_action`, `recap_action`, `onboarding_step`, `onboarding_result`, `completeness_view`). Never amounts, journal text, place names, titles, emails or usernames. Consent/opt-out rules unchanged; invisible to the user.
- **Hide game:** `Settings.hide_game` (or `/game/me` 404, `features.game=false`) hides every game element (XP, badges, streaks, celebrations, rule copy).
- **Per-user local data:** packs, outbox and cached trips are keyed by user id and deleted on sign-out/`session_revoked`/account delete (`SessionCleaner`).
- **Commands:** the CI line from `CLAUDE.md`; each task runs its module's `:testLocalDebugUnitTest`, `spotlessApply`, `detekt` before the PR.

## Rulings (also in the ledger)

| # | Ruling | Why | Cost if wrong |
|---|---|---|---|
| R1 | Sessions list needs a backend endpoint that does not exist; add `GET /api/auth/sessions` + `DELETE /api/auth/sessions/{id}` (Task B1). App shows "this device" + list when the endpoint exists, otherwise only "Sign out everywhere" | contract has only logout-all | one small additive backend PR |
| R2 | Android OAuth uses the system browser + PKCE + `voyager://auth/callback` for Google, GitHub, Facebook. No Apple button on Android. Native Google ID-token sign-in deferred (needs the owner's client ID and Play Services) | contract allow-lists `voyager://auth/callback`; credentials missing | follow-up task only |
| R3 | Verify/reset emails are `https://<base>/verify?token=` and `/reset-password?token=`. The app registers non-autoVerify https filters for the prod host and always offers "paste the link or code" | `assetlinks.json` needs the release cert fingerprint (owner) | one chooser tap |
| R4 | Journey tabs Trips / Explore / Alerts / Play / You replace Home / Search / Chat / My trips / Alerts only when journey is on. Explore = Search with Chat and Near me entries; saved plans stay reachable from Trips | web ruling 2 and Trips-API Mobile tabs; two-way compat | tab labels only |
| R5 | Existing users see the 5-step onboarding once when `onboarding.done == false`; Skip sends `skipped:true` and counts as seen. The local `TravellerProfile` is kept and synced to server preferences | web ruling 1 | none |
| R6 | Offline storage is Room (`offline_pack`, `outbox` tables, DB version bump with explicit migration) keyed `userId:tripId`; flush via WorkManager single-flight per user | existing Room use; `RoomDatabase` migration test exists | schema migration |
| R7 | Dreaming trip creation, rename, dates, phase override, delete are all offline-unsupported (online only); expenses and journal are offline-capable (outbox) | Trips-API client notes | none |
| R8 | Place keys use the shared fixture (`place_key_cases.json`, incl. FNV-1a suffix for non-ASCII); the phase function passes `trip_phase_cases.json`; both fixtures are copied into `core/domain/src/test/resources/journey/` | contract requires | none |

## Hotspots (one owner per file)

`core:model` DTOs, `core:network` APIs, `core:data` repository interfaces/fakes, `core:model` `Routes.kt`, `app` nav + manifest: **Task 0 and Task 17 only**. Later tasks add files in their own module and, where a shared file must change, make the smallest additive edit in a `core: ...` commit (CLAUDE.md rule) and rebase.

## Waves

| Wave | Tasks | Gate |
|---|---|---|
| 0 | T0 contracts, T1 pure helpers, B1 backend sessions endpoint (independent repo) | merged to main |
| 1 accounts | T2 session + refresh, T3 sign-up/verify/reset, T4 OAuth, T5 account area, T6 game + Play, T7 onboarding | T2 first (T3-T5 consume `SessionManager`) |
| 2 journey | T8 hub + routing, T9 trip screen + prep + completeness, T10 Today + check-in, T11 offline pack, T12 expenses, T13 journal + places, T14 recap + countries | after T0, T1, T2 |
| 3 integration | T15 results/plan/chat snapshot and trip create, T16 Near me "I'm here", T17 shell (tabs, home rule, deep links, coach marks) | after waves 1-2 |
| 4 finish | T18 translation pass, T19 docs + parity matrix, T20 final review, clean release APK smoke | all merged |

At most three subagents at once.

---

## Task 0: Shared contracts (wave 0)

**Files:** `core/model/.../auth/{Accounts,Signup,Oauth}.kt`, `.../game/Game.kt`, `.../trips/{Trip,Prep,Expense,Journal,Recap,OfflinePack,Stats}.kt`, `settings/Session.kt` (adds `refreshToken: String? = null`, `expiresAtEpochMs: Long? = null`, `userId: String? = null`), `client/ClientConfig.kt` (features + `auth`), `navigation/Routes.kt` (new routes below); `core/network/.../api/{AccountApi,SignupApi,OauthApi,GameApi,TripsApi}.kt`; `core/data` interfaces `AccountRepository`, `SignupRepository`, `OauthRepository`, `GameRepository`, `TripsRepository`, `PrepRepository`, `ExpensesRepository`, `JournalRepository`, `RecapRepository`, `OfflinePackRepository`; `core/testing` fakes for each; `core/domain/.../JourneyFlags.kt`; telemetry registry mirror in `core:domain` `TelemetryTypes.kt` (new events, closed enums); `config/web-source.properties` pin bump + empty `config/i18n/web-keys/<module>.json` for the new modules.

**Interfaces (names are binding):**
- DTOs mirror the two contract pages field for field (`User` gains `id, displayName, emailVerified, accountVerified, hasPassword, signupMethod, avatarUrl`, all defaulted); `Session{accessToken, tokenType, expiresIn, refreshToken?, user}` response type `SessionDto`; `GameDelta{xp, awards, badges, levelUp}` nullable; `Trip`, `Phase` enum (`DREAMING..MEMORIES`, unknown to `null`), `PrepItem`, `OfflinePack` (all nested types), `Expense` (`amount: String`), `Entry`, `TripPlace`, `Recap`, `Stats`.
- Routes (all `@Serializable`): `SignUpRoute, CheckEmailRoute(email), VerifyEmailRoute(token?), ForgotPasswordRoute, ResetPasswordRoute(token?), OauthUsernameRoute, AccountRoute, SessionsRoute, PlayRoute, TripsRoute, TripRoute(tripId, tab?), TodayRoute(tripId), OfflinePackRoute(tripId), ExpensesRoute(tripId), JournalRoute(tripId), RecapRoute(tripId), YouRoute`.
- `JourneyFlags(config): {accounts, game, trips, journey, offlinePack, signupAvailable, resetAvailable}` pure, default all false when keys are absent.
- Every repository method returns `AppResult<T>`; fakes expose call logs and settable results.
- [ ] Tests: decode a golden JSON of every contract object (copy examples from the two pages into `core/model/src/test/resources/contracts/`); `ClientConfig` without the new keys decodes to flags all false; unknown keys ignored.
- [ ] `./gradlew spotlessCheck detekt :core:model:test :core:network:testLocalDebugUnitTest :core:data:testLocalDebugUnitTest`. Commit `core: Phase 1/2 contracts`.

## Task 1: Pure helpers (wave 0)

**Files:** `core/domain/.../journey/{TripPhase,PlaceKey,PrepRules,TripDates,Money,RecapRules}.kt`, tests + `src/test/resources/journey/{trip_phase_cases,place_key_cases}.json` copied from the web repo (`backend/tests/fixtures/journey/`).
- `TripPhaseRules.effective(start?, end?, override?, today, prepComplete): Phase` and `daysToStart`, `dayIndex`, exactly the Phases section of Trips-API; `PlaceKey.of(kind, name): String` with the NFKD slug rule and the FNV-1a 8-hex suffix for non-ASCII; `Money.parse(amount, currency): Long?`/`format` with the exponent table (0: JPY KRW VND CLP ISK UGX XAF XOF; 3: BHD KWD OMR JOD TND); `TripDates.localToday(clock, zone)`.
- [ ] Tests: all golden cases pass (including `tokyo_morning_day1`); `Pastéis de Belém!` gives `activity:pasteis-de-belem`; CJK name gets `-<8 hex>`; amount edge cases (`12.50` EUR ok, `12.505` rejected, `0` and `1000000.01` rejected, JPY decimals rejected).

## Task B1: Backend sessions list (repo `~/travel-planner`, separate PR)

Additive and safe on its own: `GET /api/auth/sessions` (auth) returns `{"sessions": [{"id": family_id, "client", "device_name", "created_at", "last_used_at", "current": bool}]}` for non-revoked, unexpired refresh families of the caller; `current` is true when optional header `X-Refresh-Token` hashes into that family (the header is never logged). `DELETE /api/auth/sessions/{id}` revokes that family (`204`, `404 session_not_found` for anyone else's). Rate bucket `account`. Update `docs/Accounts-API.md` Changelog (additive) and `docs/Feature-Parity.md`. Web SPA unchanged. Tests in `backend/tests/test_sessions_list.py`: owner only, revoked family disappears, `current` flag, other user 404, web logins (no refresh token) not listed. `ruff check backend/`, `python3 -m pytest -q`.

## Task 2: Session, refresh, sign-out (wave 1, first)

**Files:** `core/network/.../{AuthInterceptor,TokenAuthenticator}.kt`, `core/datastore/.../EncryptedSessionStore.kt`, `core/data/.../{AuthRepositoryImpl,SessionManager,SessionCleanerImpl}.kt`.
- `SessionManager.refresh(): RefreshOutcome` single-flight (Mutex); rotation persisted before the retry; `refresh_stale`: re-read the newest stored token, retry once, else treat as invalid; `refresh_invalid`: clear and emit `SessionExpired`; `429`: wait `Retry-After`, do not sign out; `401 session_revoked` triggers one refresh, then sign-out. Network errors never sign out. Pre-emptive refresh when `expiresAtEpochMs` is within 60 s.
- Login sends `client:"android"`, `device_name` (`Build.MODEL`, max 64). Web-style token-only sessions (no refresh token, old backend) keep today's behaviour (a 401 clears the session).
- Sign-out calls `POST /auth/logout {refresh_token}` best effort, then clears tokens, packs, outbox, per-user caches. `logoutAll()`. `changePassword` stores the returned tokens.
- [ ] Tests (MockWebServer): 20 parallel 401s cause exactly one refresh call; rotated token persisted; `refresh_stale` retry path; `refresh_invalid` signs out; 429 keeps session; offline error keeps session; old-backend login response without `refresh_token` works; the token is never sent to a foreign host.

## Task 3: Native sign-up, verify, reset (wave 1)

**Files:** `feature/auth/.../{signup,verify,reset}/*`, `core/data/.../SignupRepositoryImpl.kt`, strings.
- Screens: `SignUpScreen` (username, email, password with live rule hints, display name, optional invite code when `auth.signup_mode == invite`), `CheckEmailScreen` (resend with cooldown 30 s), `VerifyEmailScreen` (token from the deep link or a pasted link/code: accepts a full URL or a bare token), `ForgotPasswordScreen`, `ResetPasswordScreen`. Login shows "Create account" only when `signup_available` and "Forgot password?" only when `password_reset_available`; `403 account_pending` offers resend with `email_hint`.
- Verify success stores the session and shows the `GameDelta`; `session == null` (confirmation only) shows a message. Error codes map: `username_taken, username_invalid, username_reserved, email_invalid, password_too_short, password_too_long, password_common, password_matches_identity, display_name_invalid, invite_required, invite_invalid, email_unavailable, token_invalid, token_expired, token_used, email_changed, rate_limited, email_unverified`.
- Telemetry: `signup_step`, `email_verify`, `password_reset`, `auth_start/result` (mode `signup`).
- [ ] Tests: each error code to its message; token extraction from `https://h/verify?token=abc&x=1`; resend cooldown; no sign-up button when the flag is off; ViewModel tests with `FakeSignupRepository`.

## Task 4: Social sign-in (wave 1)

**Files:** `feature/auth/.../oauth/*`, `core/data/.../OauthRepositoryImpl.kt`, `core/domain/.../Pkce.kt`.
- Buttons for Google, GitHub, Facebook from `/auth/providers` (existing `AuthProvidersRepository`), order as served, Apple omitted (R2). `configured=false` or endpoint 404 shows "coming soon" (existing copy). Tap: generate verifier (64 chars, `SecureRandom`), challenge `b64url(sha256)`, open Custom Tab at `<base>/api/auth/oauth/<p>/start?client=android&challenge=…&redirect_uri=voyager%3A%2F%2Fauth%2Fcallback`; the callback arrives as `voyager://auth/callback?code=|error=`; verifier is held in a process-scoped holder and survives only that attempt.
- `exchange` handles `signed_in`, `needs_username` (username screen then `/auth/oauth/complete`), `linked`; `400 code_invalid` restarts. Callback errors `cancelled, state_invalid, provider_error, provider_unavailable, email_in_use_unverified, identity_in_use, account_inactive, bad_request` map to copy. Account linking (from Task 5) uses `/auth/oauth/link-ticket` then the same browser flow.
- [ ] Tests: PKCE challenge matches the RFC 7636 vector; verifier is 43-128 URL-safe chars; stale or unknown callback without a pending attempt is ignored; the three results; `DeepLinkIntents.sanitize` accepts only `voyager://auth/callback` with `code` or `error` params of bounded length.

## Task 5: Account area (new `feature:account`) (wave 1)

- `AccountScreen`: avatar (photo picker `GetContent` -> compress to jpeg/webp under 2 MB, `PUT /account/avatar` multipart; initials when null; remove), display name edit, email + "verify email" (`POST /account/email/verify`, `409 already_verified`, `422 no_email`), change password (existing screen), linked sign-in methods with link/unlink (`409 last_sign_in_method`), daily AI allowance (`/account/quota`, shown on `ai_daily_quota` / `email_unverified` responses too), **settings**: language, theme, currency, units, `hide_game`, `hide_community`, `profile_visibility`, `telemetry_opt_out` (kept in sync with the local consent switch), `bucket_list_goal` via `PATCH /account/settings`; **sessions**: `SessionsScreen` (list when B1 exists, `this device` marker, revoke one) and "Sign out everywhere" (`logout-all`); **export** (`GET /account/export` streamed to a cache file, shared with `ACTION_SEND` through the existing FileProvider; `429` shown) and **delete** (password or `confirm_username`, `403 password_incorrect`, `400 confirmation_mismatch`, `409 last_admin`; on success wipe local data).
- Telemetry `account_action` with the contract enum.
- [ ] Tests: settings PATCH sends only changed keys; delete requires the right confirmation field per `has_password`; avatar over 2 MB is downscaled before upload; export writes to cache, not external storage; sessions screen degrades when the endpoint 404s.

## Task 6: Game and Play (new `feature:play`; `core:ui` celebration) (wave 1)

- `GameRepository` (`/game/me`, `/game/catalog` cached 5 min, 404 = disabled). `GameDeltaHost` in `core:ui`: given a `GameDelta?` shows XP snackbar, badge and level-up sheets (reduced-motion safe, no animation under `ANIMATOR_DURATION_SCALE == 0`); every repository returning a `game` field forwards it to `GameEvents` (`SharedFlow`).
- `PlayScreen`: level and XP bar (`level_min_xp`, `next_level_xp`), badge grid (progress/target, earned date, tier colours plus label text), streaks (`daily` and `trip_checkin`), recent awards, countries visited list and bucket-list goal (`/trips/stats`; hidden without `features.trips`). Names: `game:badges.<id>.name|desc` and `game:rules.<id>` (web-synced keys); an unknown id falls back to the id's words.
- `hide_game` hides the screen, tab and every celebration. Telemetry `game_view`, `game_award`, `badge_earned`, `level_up`.
- [ ] Tests: delta to celebration state; hidden flag suppresses everything; unknown badge id; `GET /game/me` 404 hides Play.

## Task 7: Shared onboarding (feature:onboarding) (wave 1)

Follow web Task 26 (`2026-11-12-phase2-journey.md`) and keep the existing local quiz/classifier: steps 1 welcome, 2 how you travel (6 questions + result), 3 home airport + passport, 4 notifications (`POST_NOTIFICATIONS` only after "Allow"), 5 first trip or explore. Each step `PUT /account/onboarding {step, done:false}` fire-and-forget; resume from `onboarding.step`. Finish: `PUT /preferences` (read-modify-write, Retry or "Continue without syncing") then `{step:5, done:true, traveller_type}`; Skip sends `{step, done:true, skipped:true}`. Gate: signed in, `GET /account` says `done == false`, shown once per process start; a 404/error does nothing. Telemetry `onboarding_step`, `onboarding_result`. Strings: existing `onboarding:*` keys unchanged, only additions.
- [ ] Tests: classifier parity cases remain; resume at step 3; skip flag; permission asked only after Allow; gate never fires on 404.

## Task 8: Trips hub and home rule (feature:trips) (wave 2)

- `TripsRepository.list(today)`, `create`, `get`, `update`, `delete`, `stats`; hub groups by phase in the served order, pinned "Next up" card (`pinned_id`), phase chips with colour **and** text, new-trip sheet (title, destination, optional dates; maps `trip_dates_invalid`, `trip_title_invalid`), empty state with "Plan a trip" and "Start dreaming". Always sends the device local date as `today`, and recomputes phase offline from cached dates with `TripPhaseRules`.
- Home rule in `HomeRoute` resolution: journey on, signed in, at least one trip: Trips; otherwise Explore. Config-loading never flashes the wrong start (splash holds until config + trips count are known or 2 s pass).
- Telemetry `trip_view{surface:hub}`, `trip_action`.
- [ ] Tests: sort order golden; pinned card; create validation; offline phase recompute; the start-route function table (journey on/off, signed out, zero trips, trips fail to load = Explore).

## Task 9: Trip screen, completeness meter, prep (feature:trips) (wave 2)

- `TripScreen` tabs by phase (Overview, Prep, Today when on the road, Money, Journal, Recap when memories, Offline); phase menu (override and clear), rename, dates, delete with confirm; overview shows destinations, dates, `days_to_start`, prep ring, plan summary and "Open planner"/"Plan it" (hands a search request to the existing launcher).
- Completeness meter: pure `PlanCompleteness` in `core:domain` ported from web Task 18 (`utils/` plus tests there), shown on the overview and in My Plan.
- Prep: `GET /trips/{id}/prep`, toggle with optimistic update and rollback (`prep_item_unknown`, `409 prep_item_auto|prep_item_na` show a message and reload), `due`/`overdue` text, per-item facts (visa type and official URL opened in the existing custom tab, bookings, SIM plan, currency), `trip_ready` game delta forwarded.
- [ ] Tests: ring maths (`floor`), `na` excluded, rollback on failure, 409 reload, completeness cases ported from the web test file.

## Task 10: Today and check-in (feature:trips) (wave 2)

- `TodayScreen`: "Day N" (server `day_index`, local date), city for the day, itinerary slots for today (existing `ItineraryDayCard`/`PlanItinerary`), next-up slot, emergency button (opens the offline pack's card), expenses quick add. Check-in `POST /trips/{id}/checkin {day: local date}` with `new_country`, streak and `GameDelta`; `409 checkin_out_of_trip` and `422 checkin_day_invalid` messages; second tap is a no-op. Directions opens a maps intent for the slot name plus city.
- Telemetry `today_action` (enum from the registry).
- [ ] Tests: local date sent (Tokyo morning case), idempotent second check-in, out-of-trip, new-country banner.

## Task 11: Offline pack (core:database, core:data, feature:trips offline pkg) (wave 2)

- Room: `offline_pack(key PK userId:tripId, json, savedAt)`, `outbox(id, userId, method, path, body, key, createdAt)` with a migration test. `OfflinePackRepository.save(tripId)` calls `GET /offline-pack` (`Cache-Control: no-store`); `404 offline_pack_disabled` or `features.offline_pack=false` disables the feature and purges (`SessionCleaner`-style `OfflinePurger`). Pack viewer (works with no network): emergency numbers (static/AI label and `as_of`), phrases with big-text "show to a local" mode (`take_me_to` address card), currency card ("rate from <date>, approximate"), plan and itinerary slots, check-in days. Snapshot upload: `PUT /trips/{id}/snapshot` after every plan save while results are shown (Task 15 calls it; `413`/`422` swallowed, retried later).
- `Outbox`: `enqueue` (a repeated `key` replaces), single-flight `flush(userId)` through WorkManager (on connectivity and every 15 min with pending items); 2xx and 409 sent; 4xx except 408/429 dropped; 5xx/network kept; telemetry `outbox_flush`, `offline_pack`. Storage-limit failure reports `quota`.
- [ ] Tests: concurrent flushes send each item once; a client_id resend answered 200 yields one row; per-user isolation and purge on sign-out; pack opens with the fake network off; migration from the previous schema version keeps the old tables.

## Task 12: Expenses (feature:trips) (wave 2)

- List by day, totals by currency, USD estimate and `unconverted` currencies listed, rate label with date. Add/edit/delete form: amount validated with `Money`, currency picker (default trip currency or last used), category chips (8), note (140), date. Create always carries a fresh UUID `client_id`; offline goes to the outbox with a "waiting to sync" marker; `409 expense_limit` message; server `422` codes mapped (`amount_invalid, currency_invalid, category_invalid, note_too_long, spent_on_invalid, client_id_invalid`, generic for unknown `*_invalid`).
- Telemetry `expense_action{action, category, queued}` only.
- [ ] Tests: JPY rejects decimals, queued add shows pending then reconciles, totals never mix currencies, delete rollback, no amount in telemetry events.

## Task 13: Journal and place ratings (feature:trips) (wave 2)

- Journal entries (body 1..4000, day, mood great/good/ok/tough) with outbox; places list from the itinerary and "I'm here" places: visited toggle, stars 1..5, note (500) via `PUT /trips/{id}/places/{place_key}` using `PlaceKey` (visited sets `GameDelta` forward); `409 journal_limit|places_limit`.
- Telemetry `journal_action`, `place_action{action, kind, stars}`.
- [ ] Tests: key built from kind+name via fixture; unvisit keeps `visited_at` semantics (UI reads `visited`); queued entries sort after synced in the same day; stars bounds.

## Task 14: Recap and countries (feature:trips) (wave 2)

- `RecapScreen`: stats (days, places visited/rated, average stars, distance or none, spend vs budget with over-budget text), route on a simple canvas map (no new map dependency; draws `route` or the planned route), countries with "new" stamps (static scale-in, none under reduced motion), "Mark recap complete" (`409 recap_not_ready` message, idempotent repeat), share text via the existing share sheet, "Where next" opens Explore. `plan_cost_usd` computed client-side with `PlanCost`.
- Telemetry `recap_action`.
- [ ] Tests: null distance renders, repeat completion keeps first `completed_at`, over-budget label, no-dates trip hides complete.

## Task 15: Results, plan, chat integration (feature:results/plan/chat) (wave 3)

- After a plan save or update with results on screen: ensure the plan is a trip (it already is server-side), `PUT snapshot` with `emergency_card, forex, visa, packing_list, weather` as streamed minus `verification`; show the completeness meter in My Plan and results; "Save as trip" from a search result sets dates from the request; Chat's planning completion does the same snapshot. All behind `features.trips`.
- [ ] Tests: snapshot body has only allowed keys and no `verification`; skipped when the flag is off; 413 swallowed.

## Task 16: Near me "I'm here" (feature:nearby) (wave 3)

- During `on_the_road` (or the trip chosen in a picker) an "I'm here" action on a nearby item upserts a place (`kind: nearby`, name capped at 120, coordinates of the item when present, the rounded device location otherwise) and records `visited`. Hidden for Maps-grounded v2 items (as web ruling R41). Telemetry `place_action`.
- [ ] Tests: name truncation, hidden for grounded items, no trip means no action.

## Task 17: Shell: tabs, deep links, coach marks (app) (wave 3)

- `TopLevelDestination` becomes dynamic: journey on gives TRIPS, EXPLORE, ALERTS, PLAY, YOU (R4); off gives today's five. Settings reached from You. Manifest: `voyager://auth/callback` and the https `/verify`, `/reset-password` filters for the prod host (R3); `DeepLinkIntents.sanitize` extended and unit-tested (rejects other hosts, long tokens, extra paths). Cold-start deep links hold until the session gate resolves (existing `DeepLinkHold`). A one-time coach mark sequence on Trips/Explore/Play (3 tips, skippable, state per user in DataStore).
- Wire all new routes into `TravelApp.kt` via lambdas; `session_revoked` and sign-out purge per-user data.
- [ ] Tests: destination table for journey on/off, sanitize table, coach marks shown once per user, signed-out sees Explore.

## Task 18: Translation pass (wave 4)

- Per new module: generate synced strings (`gen_strings.py generate`), hand-translate the rest in es/de/fr/hi/zh/bn (informal es/de, `vous` in fr; never translate "Voyager"), `scripts/check-web-sync.sh`, MissingTranslation lint clean, check long-copy fits (fr/de buttons) at 200 % font.

## Task 19: Docs (wave 4)

- Android: README feature list, `docs/` pages (accounts, journey, offline, deep links), `MEMORY.md` decision lines for R1-R8.
- Web repo (`~/travel-planner`, docs-only PR): `docs/Feature-Parity.md` Android column to shipped, `docs/Accounts-API.md` Changelog for B1.

## Task 20: Final verification (wave 4)

- Opus whole-branch review (once). `scripts/build-release-apk.sh` clean (no build cache), install against a local backend (`./start.sh`, `SIGNUP_ENABLED=false`), sign in, smoke past sign-in: Trips hub, create trip, prep, offline save and open in airplane mode, add expense offline then reconnect, check-in, recap, sign out everywhere. Follow-ups file `2026-11-12-android-phase1-2-followups.md`.

## Deferred (owner or later phases)

Native Google ID-token and Apple sign-in; verified App Links (`assetlinks.json` with the release cert); FCM trip-phase pushes (Phase 6); recap share cards (Phase 4); scratch-map polygons (Phase 5); native-speaker review of hi/bn/zh and the phrase list.
