# Decision log

One line per decision: what, and why. Newest last. Builders add to this file for every call the spec leaves open.

- 2026-11-05 Package/applicationId `io.github.rupakc.travelplanner` (+ `.local`, `.debug` suffixes) — matches the public GitHub owner; flavours/debug installable side by side.
- 2026-11-05 Kept all pinned versions (AGP 9.5.0, Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, …); KSP 2.3.x is versioned independently of Kotlin and works with AGP 9 built-in Kotlin.
- 2026-11-05 **compileSdk 37** (not 36): the pinned AndroidX (core 1.19.1, Compose 1.12 via BOM, navigation 2.10.2, hilt 1.4.0, lifecycle 2.11.0) and OkHttp 5.5.1 AARs declare minCompileSdk 37. targetSdk stays 36, so runtime behaviour is unchanged.
- 2026-11-05 AGP 9 built-in Kotlin: no `org.jetbrains.kotlin.android` plugin; KGP 2.4.21 put on the classpath by the root `kotlin-jvm apply false`; compiler options via `KotlinCompilationTask`.
- 2026-11-05 `local`/`prod` flavours exist in every Android module (not only `app`) so the root tasks `lintLocalDebug`/`testLocalDebugUnitTest` run everywhere; pure-Kotlin modules alias those names to `lint`/`test`.
- 2026-11-05 Pure-Kotlin modules: core:common, core:model, core:domain (spec "pure"); everything else Android libraries.
- 2026-11-05 detekt 1.23.8 (latest stable) with its classpath pinned to Kotlin 2.0.21; runs once at the root over all sources. 2.0 is alpha.
- 2026-11-05 ktlint 1.8.0 via Spotless 8.11.0, `ktlint_official` style; options passed with `editorConfigOverride` because Spotless did not apply `.editorconfig`. Max line length 160 (140 split too many URL/JSON test lines).
- 2026-11-05 Lint: a11y/i18n ids are errors (list in build-logic `AndroidCommon.kt` + `config/lint/lint.xml`); version-freshness checks disabled so builds don't fail by date.
- 2026-11-05 Preferences DataStore (not Proto): four small files (session, settings, traveller_profile, feature_overrides); no protobuf toolchain needed. Session token is excluded from backup/device transfer.
- 2026-11-05 Token kept in app-private DataStore, not encrypted: EncryptedSharedPreferences is deprecated and the JWT expires in 24 h; revisit with Keystore-wrapped storage before a store release.
- 2026-11-05 Base URL: requests target a placeholder host and an interceptor swaps in Settings → Server or the flavour default (keeps a base path prefix). `prod` rejects http in validation; cleartext policy is per-flavour network_security_config.
- 2026-11-05 Three OkHttp clients: default (30 s read), AI (300 s read: agents may run 240 s; nearby cache miss 10–40 s; import 90 s), streaming (no read/call timeout).
- 2026-11-05 SSE: parser joins multi-`data:` events (spec-correct) and delivers a trailing event without a blank line; failures cross the IO `flowOn` boundary as values (`SseItem.Failure`) and are rethrown in the collector — throwing upstream cancelled the collector and dropped buffered events (caught by a test).
- 2026-11-05 Search keeps sections received before an interrupted stream and replays into them on the single retry (replace-by-type makes it idempotent).
- 2026-11-05 Result items keep their original JSON (`RawBacked`) and `PlanSelections` keeps unknown keys, so a plan saved on the web and re-saved on the phone loses nothing.
- 2026-11-05 Unparseable lenient numbers become null (never 0) — spec anti-goal "inventing data".
- 2026-11-05 `CurrentPlanStore` persists the draft to Room (single-row `plan_draft`), latest-wins writes through a conflated channel.
- 2026-11-05 Chat history in Room per user (web keeps it in localStorage per user); logout clears plans/recent/seen/draft/chat caches.
- 2026-11-05 Feature overrides and traveller profile are device-local (server Preferences have no field), per spec §6.3.
- 2026-11-05 Robolectric 4.17 runs unit tests on SDK 35: SDK 36 images fail on JDK 21 ("raw FileDescriptor internals"). Shared via `config/robolectric/robolectric.properties`.
- 2026-11-05 Firebase: firebase-messaging always on the classpath; the google-services plugin and `fcm_enabled=true` apply only when `app/google-services.json` exists; `FirebasePushTokenSource` returns null when FirebaseApp isn't initialised.
- 2026-11-05 Google Fonts certificates shipped under our own names (`tp_google_fonts_certs`); the library's identically named arrays are private.
- 2026-11-05 Icons: `material-icons-core` only (extended adds megabytes to debug builds); Chat uses `Send`, My trips uses `List`.
- 2026-11-05 Release signing: none yet; `prodRelease` is debug-signed so CI can assemble it (docs/Release.md). Never publish that APK.
- 2026-11-05 Devices API per backend review: `GET /api/devices` → `{items}`, `DELETE /api/devices/{id}`, `POST /api/devices/unregister {token}` (used on sign-out).
- 2026-11-05 Admin wire models + `AdminApi` exist (contract completeness) but no admin UI (spec non-goal).
- 2026-11-05 Auth gate re-evaluates on every destination change (not only on session change): Back from a deep-linked share screen popped to the synthetic Home entry while signed out. Share is always allowed; Onboarding with `fromSettings=true` is allowed when signed in.
- 2026-11-05 MainActivity is `singleTask`; deep links arriving while running are forwarded with `addOnNewIntentListener { navController.handleDeepLink(it) }` (verified on the emulator, cold and warm).
- 2026-11-05 Chat: a thread is created on the first send, titled with the first question (≤ 60 chars; renamable). Thread search matches titles and message text (Room LIKE over the user's messages, capped at 200 threads; no FTS table, so no schema migration). The web has one conversation per user; threads are the Android addition from the brief.
- 2026-11-05 Chat history port (`core:domain` `buildApiHistory`): itinerary stand-ins read the real chat-itinerary slots (`morning/afternoon/evening`) and `trip_summary.cities`; the web reads `day.slots`/`destination`, which that schema lacks, so its summary had no activities.
- 2026-11-05 Chat `applyPlanAction`: unknown fields/actions and undecodable `data` are no-ops, and `set` on a list field adds (the web would store an object in an array field). Plan actions edit the shared `CurrentPlanStore` (the web chat kept its own selections).
- 2026-11-05 Chat error turns store an error kind instead of English apology text; the UI shows the localized web copy, and history sends nothing for them (the web resent its apology text as assistant content). Retry follows the web: the failed reply stays and the question is asked again at the end of the thread with the full history (offered on every failed reply, as on the web).
- 2026-11-05 Chat "Save as plan" (not on the web) starts a new My Plan draft from the chat itinerary: `itinerary_slots` in the web chat slot shape + a `selections.itinerary` snapshot in the search schema (slot costs only; the per-person total is not relabelled), search data from `trip_map` + `session_context` when origin/destination/date are known (`adults` = travellers). It asks before replacing an unsaved, non-empty draft.
- 2026-11-05 Chat trip route: no map SDK; numbered stops on a route spine, each with an "Open in Maps" `geo:` intent (coordinates when the API sent them, else a place search).
- 2026-11-05 Chat thread delete: hidden at once, committed after a 10 s undo window by the ViewModel (or on snackbar dismissal / when the ViewModel is cleared) on the application scope, so a cancelled snackbar never leaves a delete pending.
