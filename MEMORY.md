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
- 2026-11-05 Onboarding Q6 uses the web's four accessibility ids (wheelchair, visual_impairment, hearing_impairment, cognitive_disability) with web labels instead of the spec's "step-free / limited walking / visual / hearing" — the spec says "the web's 4 accessibility ids" and those are the real ids.
- 2026-11-05 Quiz → Preferences: lists union (never removed), budget set when answered, adults/children filled only while the server still has the default party (1 adult, nobody else) — review I-1, infants ≥ 1 for "infants", photography/beaches map to art / nature+wellness, family_kids adds `family`; pace stays device-local (no Preferences field). Table in docs/Onboarding-and-Traveller-Types.md.
- 2026-11-05 Classifier default when nothing scores: CULTURE_FOODIE (broadest); tie order = TravellerType declaration order; one scored secondary at ≥ 60 %.
- 2026-11-05 Before onboarding (no type) every catalogue feature is visible (minus explicit disables): nothing to personalise from, so nothing is hidden.
- 2026-11-05 Feature/type copy and icons live in core:ui (`core.ui.profile`) so onboarding, home and settings share one set of strings; Hilt bindings for the pure catalogue/classifier live in feature:onboarding (core:domain has no DI).
- 2026-11-05 `AccountChangePasswordRoute` (voluntary change from Settings) is separate from `ChangePasswordRoute`: the gate sends a READY user Home from any gate screen.
- 2026-11-05 Onboarding finish order: profile + overrides → PUT preferences (failure: Retry / Continue without syncing, local data kept) → notification rationale (first run, Android 13+, not yet granted) → onboardingCompleted. Marking complete last keeps the gate from leaving mid-flow.
