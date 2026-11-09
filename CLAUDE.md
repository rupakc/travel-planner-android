# Travel Planner for Android ("Voyager")

Native Android companion (Kotlin, Jetpack Compose, Material 3) to the travel-planner web app. Same backend, account and plans. MIT.
The backend lives in `~/travel-planner` (read-only reference: `CLAUDE.md`, `docs/API-Reference.md`, `frontend/src/`). Production is decommissioned; the backend runs locally on port 8001.

## Source of truth, in order

1. `docs/superpowers/specs/2026-11-05-android-app-design.md` (design, authoritative)
2. `docs/Foundation-Interfaces.md` (exact interfaces; the code wins if they disagree, then fix the page in the same PR)
3. `MEMORY.md` (decision log; add a line for every call the spec leaves open)
4. `docs/reference/api-contract.md`, `docs/reference/web-ui-inventory.md`
5. `docs/` wiki pages (start at `docs/Home.md`)

## Architecture

Unidirectional data flow, MVVM. Screen = stateless `XxxScreen(state, onEvent, modifier)` + `XxxRoute` (`@HiltViewModel`, `StateFlow<XxxUiState>` collected with `collectAsStateWithLifecycle()`).
Repositories are interfaces in `core:data` returning `AppResult<T>`; features inject interfaces only (fakes in `core:testing`).

Modules: `app`; `core:{common,model,domain}` (pure Kotlin); `core:{network,database,datastore}` (internal to `core:data`); `core:{data,designsystem,ui,notifications,testing}`;
`feature:{auth,onboarding,home,search,results,plan,chat,alerts,settings,nearby,share}`. Convention plugins in `build-logic/convention`; versions only in `gradle/libs.versions.toml`.

## Module rules (enforced by review)

- A feature depends on exactly `core:{ui,designsystem,data,domain,model,common}` (set by the `travelplanner.android.feature` plugin). Features never depend on each other; leave a feature via a lambda the graph function receives, turned into a route from `core:model` by `app/.../navigation/TravelApp.kt`.
- Edit only the module you own. A missing shared API: smallest additive change, own commit titled `core: ...`, never rename or remove shared APIs.
- Never invent data: every price, link and fact comes from the API; label estimates; booking = open the item's URL in a Custom Tab via `BookingLinkButton` (http/https only).
- Colours, type, shapes, spacing come from `core:designsystem` (`TravelTheme`); no literals in features.

## Commands

```bash
export JAVA_HOME=$HOME/Library/Java/temurin-21/Contents/Home ANDROID_HOME=$HOME/Library/Android/sdk   # adjust to your install
./gradlew assembleLocalDebug                          # local flavour, backend http://10.0.2.2:8001
./gradlew :feature:search:testLocalDebugUnitTest      # one module
./gradlew spotlessApply                               # format (ktlint via Spotless)
./gradlew spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug assembleProdRelease :build-logic:convention:test   # exactly what CI runs
scripts/build-release-apk.sh                          # the only way to build a release APK locally
scripts/check-doc-links.sh                            # relative links in README/docs
```

Capped form when builds share a machine: `./gradlew -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.compiler.execution.strategy=in-process --max-workers=2 <tasks>`.
Never run `./gradlew --stop` or kill other Java processes. Run the backend with `./start.sh` in the web repo.

## Conventions

- Strings: every user-facing string in the module's `res/values/strings.xml` plus `values-es/strings.xml` (MissingTranslation is a lint error); prefix names with the feature; `<plurals>` for counts; reuse the web's English copy; backend `detail` is shown as-is.
- Accessibility: contentDescription on icon-only controls (`null` if decorative), 48 dp targets, `heading()` on section titles, state descriptions on toggles, live regions for async results, works at 200 % font scale, no colour-only meaning.
- Every network screen has loading (skeleton), empty and error-with-retry states; 429 shows the backend message.
- Adaptive: compact = bottom bar, medium = rail, expanded = list-detail where specified.
- Lists: `LazyColumn` with stable `key` and `contentType`; no work in composition.
- JSON only via `TravelJson`; numbers that may be strings use the `Lenient*` types (unparseable becomes null, never 0).
- Tests first: ViewModel (Turbine + fakes), pure functions, Compose on Robolectric; no network.
- KDoc on public APIs; ktlint (`ktlint_official`, max line 160) and detekt clean with no baseline.
- Commits small and logical; PRs via `gh`.

## Gotchas (deliberate)

- compileSdk 37 (AndroidX/OkHttp AARs require it), targetSdk 36, minSdk 26, JDK 21. Robolectric runs on **SDK 35** (36 fails on JDK 21).
- Flavours `local` (cleartext, `http://10.0.2.2:8001`) and `prod` (HTTPS only, `-PPROD_BASE_URL=`) exist in every Android module; root tasks `lintLocalDebug`/`testLocalDebugUnitTest` run everywhere.
- AGP 9 built-in Kotlin: no `org.jetbrains.kotlin.android` plugin.
- `prodRelease` uses the release key only when one is configured (`VOYAGER_KEYSTORE_*` env or git-ignored `keystore.properties`); otherwise it is debug-signed, and a debug-signed APK is never published ([docs/Release.md](docs/Release.md)).
- Publishing is CI only: `android.yml` jobs `release-apk` → `github-release` → `ghcr` run on `main` after `build` passes (signed, cert-checked APK; Release `android-v0.1.<run>`; `ghcr.io/rupakc/voyager-android`). Never print secrets; never commit keystores or `app/google-services.json`.
- KSP 2.3 writes incomplete output when KSP tasks run concurrently (once dropped two ViewModels from Hilt; the APK crashed after sign-in). `configureKspSafety` serializes KSP tasks and keeps them out of the build cache; `ksp.incremental=false`; `verifyHiltViewModels<Variant>` gates every `assemble`/`bundle`/`package`. Do not remove these. Release APKs only from `scripts/build-release-apk.sh` or CI.
- Firebase is optional: the google-services plugin applies only if `app/google-services.json` exists; never commit it, `local.properties`, keystores or secrets.
- Search/chat SSE: do not throw inside the upstream flow (events get dropped); failures travel as values. Search retries once without `done`; chat ends on `done` or `planning_done`.
- Icons: `material-icons-core` only.
- Plan JSON round-trips losslessly with the web (`RawBacked`, `extras`): build items from the objects you received.
- Admin API models exist but there is no admin UI (non-goal); R8 drops `AdminApi` from release (`verifyReleaseKeepRules` checks it).
- `AppError.message` is backend text or null; client-side failures (`Decoding`, `Unknown`, `Network`) never carry exception text, so show your own string.
- Itinerary logic has one port (`core:domain` `itinerary/`: `ItineraryLayouts`, `ItinerarySchedule`, `PlanItinerary`) and one renderer (`core:ui` `ItineraryDayCard`); the .ics export bridges to it and `PlanIcsParityTest` golden files must stay byte-identical.
- `MainActivity` is exported: incoming intents go through `DeepLinkIntents.sanitize`; never read Navigation deep-link extras or non-`travelplanner://` data.

## Claude Code files

Agents in `.claude/agents/` (android-architect, compose-ui-builder, api-contract-checker, android-reviewer, a11y-i18n-auditor, test-engineer);
skills in `.claude/skills/` (verify-android, add-feature-module, add-api-endpoint, add-translation, release). `.claude/settings.json` allows read-only gradle/adb/git commands.
