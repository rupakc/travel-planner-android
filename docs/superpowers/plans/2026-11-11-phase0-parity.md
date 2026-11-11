# Android parity with web Phase 0: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the Android app to the web's Phase 0: generated design tokens with a designed dark theme and light as the default, all UI in the 7 web languages, the telemetry client with EU consent and a Privacy opt-out, the AI `locale` field, and social sign-in buttons that say "coming soon".

**Architecture:** The web repo stays the source. Two stdlib-only Python generators in `tools/web-sync/` turn the web's `tokens.json` into `Palette.kt`/`DarkPalette.kt` and the web's i18next catalogues into `values-<lang>/strings_web.xml`. The web commit they read is pinned in `config/web-source.properties`, and CI checks the generated files against it. Telemetry follows `frontend/src/telemetry/`: the pure rules live in `core:domain/telemetry`, and the engine, uploader, queue file and WorkManager flush live in `core:data/telemetry` (no new module). The app shell wires lifecycle, screens, taps and the consent prompt. An OkHttp interceptor adds `locale` to AI requests.

**Tech Stack:** Kotlin 2.4.21, AGP 9.5 (built-in Kotlin), Compose BOM 2026.09.00, Material 3, Hilt + KSP 2.3.13, OkHttp 5.5.1 / Retrofit, kotlinx.serialization (`TravelJson`), Preferences DataStore, WorkManager 2.12.0, `lifecycle-process`, JUnit4 + Turbine + Robolectric 4.17 (SDK 35), MockWebServer, Python 3 (stdlib only) for the generators.

**Spec:** `~/travel-planner/docs/superpowers/specs/2026-11-10-voyager-reimagined-design.md` §8 (telemetry), §9 (look, feel, language) and §11 (Phase 8). The contracts are in the web repo's `docs/Telemetry.md`, `docs/Privacy.md`, `docs/Internationalization.md`, `docs/Theming.md` and `backend/app/telemetry/registry.py`, and the behaviour to mirror is in `frontend/src/telemetry/{client,consent,routes}.js`, `frontend/src/components/ConsentPrompt.jsx` and `frontend/src/components/auth/SocialSignInButtons.jsx`. For the Android side, read `docs/superpowers/specs/2026-11-05-android-app-design.md`, `CLAUDE.md` and `MEMORY.md` in this repo.

## Global Constraints

- Toolchain as in `CLAUDE.md`: compileSdk 37, targetSdk 36, minSdk 26, JDK 21, Robolectric on SDK 35. Command prefix for every Gradle step: `export JAVA_HOME=$HOME/Library/Java/temurin-21/Contents/Home ANDROID_HOME=$HOME/Library/Android/sdk; G="./gradlew -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.compiler.execution.strategy=in-process --max-workers=2"`. Never run `./gradlew --stop`.
- No new dependencies and no tracker SDK. `gradle/libs.versions.toml` is not edited: `androidx-work-runtime`, `androidx-work-testing`, `androidx-hilt-work`, `androidx-hilt-compiler`, `androidx-lifecycle-process` and `okhttp-mockwebserver` already exist. If a task believes it needs a new library, it stops and reports.
- Module rules: a feature depends on exactly `core:{ui,designsystem,data,domain,model,common}`. Features never depend on each other. `core:network`, `core:database` and `core:datastore` are visible only to `core:data` (and `app`, `core:testing`).
- Telemetry is invisible and never blocks. `Telemetry.emit` is non-suspending, returns at once, never throws and does no I/O on the caller's thread. All work runs on a single-threaded `Dispatchers.IO.limitedParallelism(1)` confinement or in WorkManager. Nothing on a user path awaits telemetry.
- Wire contract (web `docs/Telemetry.md`): `POST /api/telemetry`, body `{"client":{anon_id,session_id,platform:"android",app_version,locale},"events":[{event,ts,screen,props}]}`, sent gzipped (`Content-Encoding: gzip`), with one session per batch. Event and prop names come only from `registry.py` `EVENTS`. A prop value is a token `^[A-Za-z0-9_.:/-]{1,64}$`, an integer within ±2^53, a finite number or a boolean.
- Client rules (verbatim values): fetch `GET /api/config` once per process per server. A `404`, an error or `telemetry.enabled != true` turns telemetry off with no retries. `flush_interval_s` is clamped to 1..300 (default 15) and `max_batch` to 1..100 (default 50). The queue holds at most 500 events, and stored events older than 7 days are dropped. A session ends after 30 min idle, and its write is throttled to 10 s. After 3 failures in a row the client backs off exponentially, up to 5 min. `Retry-After` (seconds or an HTTP date) is honoured, capped at 1 h. A `4xx` other than 408 and 429 drops the batch, and a `404` on POST disables telemetry.
- Consent (web `consent.js`): consent is required when the device zone is `Europe/*`, except `Europe/Istanbul`, `Europe/Moscow`, `Europe/Minsk`, `Europe/Kiev` and `Europe/Kyiv`, and also for `Atlantic/Reykjavik`, `Atlantic/Canary`, `Atlantic/Madeira`, `Atlantic/Azores` and `Atlantic/Faroe`. Until the user chooses, nothing is queued or sent. Elsewhere telemetry is on and can be switched off. A grant sends `consent {choice: "granted"}`. A denial is stored only on the device, clears the queue and sends nothing.
- Languages: `en es de fr hi zh bn`, listed in the picker by their native names (English, Español, Deutsch, Français, हिन्दी, 中文（简体）, বাংলা). The AI `locale` field is the base code. The telemetry `client.locale` is `zh-CN`, `hi-IN` or `bn-IN` for those three languages and the bare code for the others (web `intlLocale`).
- Strings: `values-es` stays hand-maintained. For `de fr hi zh bn`, the strings whose English matches a web key are generated into `values-<lang>/strings_web.xml` (never hand-edited). Every other string is hand-translated in files with the same names as the module's `values/` files. Plural categories: de one/other, fr one/many/other, hi one/other, zh other, bn one/other. The brand name is `brand_name` via `%1$s` (MEMORY M5) and is never translated.
- Copy is the web's English: `privacy.json`, `auth.json` and `common.json` in `frontend/src/i18n/locales/en/`. The privacy policy URL is `https://github.com/rupakc/travel-planner/blob/main/docs/Privacy.md`.
- Two-way compatibility: the app must keep working against an older backend. `/api/config` is feature-detected (a 404 means off). `locale` is sent unconditionally because older backends ignore unknown fields (Pydantic `extra=ignore`).
- Release APKs are built only by `scripts/build-release-apk.sh` (a separate `clean`, then `--no-build-cache --rerun-tasks`) or by CI.
- Commits: small, logical, as the configured git identity, each ending with the session's `Co-Authored-By:` trailer. Never `git config`.

## Review Focus

1. **Process killed in the background with unsent events.** The user expects the events to arrive later and never twice. Pinned in Task 13 (`claim removes the file so a second claim is empty`, `append during a claimed drain is not overwritten`) and Task 14 (`onBackground persists pending events and schedules the worker`, `in-flight events are never persisted`).
2. **Device time zone changes into the EU (the user flies to Paris) with no choice stored.** The user expects nothing to be collected until they choose, and the prompt to appear. Pinned in Task 12 (`allowed follows the zone at call time`).
3. **An older, self-hosted or switched server** (`/api/config` 404, `enabled: false`, Settings → Server change). The user expects silence with no retry storm, and a refetch for the new server. Pinned in Task 12 (`404 is cached for the server`, `refetches after a base URL change`) and Task 14 (`config off drops queued events and stops`).
4. **Translations with apostrophes, `%`, `@` at the start, `<`, `&`, or a dropped `{{count}}`** (French "l'itinéraire", "100 %"). The user expects the app not to crash in `String.format` and aapt not to fail. Pinned in Task 2 (`escapes apostrophe, quote, ampersand, lt, leading at`, `percent becomes %% only when the string has arguments`, `plural form without count keeps the other placeholders`).
5. **Request bodies that already carry `locale`, are not JSON (multipart import), or go to another host**, plus a zh-TW device. The user expects them unchanged, and Chinese AI text for zh-TW. Pinned in Task 7 (`keeps an existing locale`, `multipart import is untouched`, `foreign host is untouched`, `zh-TW resolves to zh`).

---

## Hotspots and conflict rules

| Shared resource | Owner (only this task edits it) |
|---|---|
| `gradle/libs.versions.toml`, `settings.gradle.kts` | nobody (no new libraries, no new modules) |
| `core/data/build.gradle.kts` | Task 13 |
| `app/build.gradle.kts`, `app/src/main/res/xml/*`, `TravelApp.kt`, `MainActivity.kt`, `MainViewModel.kt`, `TravelPlannerApplication.kt`, `BackupRulesTest.kt` | Task 17 |
| `core/network/.../di/NetworkModule.kt` | Task 7 (Task 6 adds a separate `TelemetryNetworkModule.kt`) |
| `core/data/.../di/DataModule.kt`, `core/datastore/.../Stores.kt`, `DataStoreModule.kt` | nobody (each task adds its own Hilt module file) |
| `core/datastore/.../PreferencesStores.kt`, `core/model/.../settings/Settings.kt` | Task 9 |
| `core/designsystem/.../theme/Palette.kt` (generated), `DarkPalette.kt` (generated) | Task 1 |
| `core/designsystem/.../theme/Color.kt`, `ContrastTest.kt` | Task 9 |
| `core/designsystem/.../component/Buttons.kt`, `Chips.kt` | Task 10 |
| `core/testing/.../fake/FakeTelemetry.kt` (new) | Task 0; other fake files are untouched |
| `.github/workflows/android.yml`, `.gitignore`, `scripts/check-web-sync.sh` | Task 3 |
| `<module>/src/main/res/values/strings*.xml` and `values-es/*` | the UI task for that module in waves 1–2 (Tasks 11, 15, 16); after that, its translation task |
| `config/i18n/web-keys/<module>.json`, `<module>/src/main/res/values-{de,fr,hi,zh,bn}/*` | that module's translation task (TR-1…TR-8) |
| `MEMORY.md`, `CLAUDE.md`, `docs/**`, `README.md`, `.claude/skills/add-translation/SKILL.md` | Tasks D1/D2 in wave 4 (other tasks list their decisions in the commit body, and D2 copies them) |

**Branching:** create the integration branch `feat/phase0-parity` from `origin/main`. For every task, create a worktree from the integration branch's head at the start of its wave: `git worktree add .claude/worktrees/p0-<task> -b p0/<task> feat/phase0-parity`. After review, merge it back with `git merge --no-ff`. Each wave ends with a **wave gate** on the integration branch, and the next wave starts from the gated head. Lint across modules (`lintLocalDebug`) is expected to fail between the first translation task in wave 2 and the end of wave 3 (a module with `values-de` makes the other modules look incomplete). The translation tasks therefore gate on their own module's lint, and the wave 3 gate is the first one to require the full `lintLocalDebug`.

## Execution waves

| Wave | Tasks (in parallel) | Gate on `feat/phase0-parity` |
|---|---|---|
| 0 | T0 contracts | `$G :core:model:test :core:domain:test :core:data:testLocalDebugUnitTest assembleLocalDebug` |
| 1 | T1 tokens generator · T2 strings generator · T4 domain rules · T5 queue/session/policy · T6 network APIs · T7 AI locale · T8 telemetry prefs store · T10 designsystem UI events + social button | `$G spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug` + `python3 -m unittest discover -s tools/web-sync -p 'test_*.py'` |
| 2 | T3 CI web-sync · T9 theme · T11 consent prompt · T12 consent + config repos · T13 uploader/queue file/worker · T14 engine · T15 settings · T16 auth + share · TR-1 · TR-2 · TR-3 · TR-4 · TR-5 | `$G spotlessCheck detekt testLocalDebugUnitTest assembleLocalDebug` + each TR task's module lint |
| 3 | T17 app integration · TR-6 · TR-7 · TR-8 | full CI line: `$G spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug assembleProdRelease :build-logic:convention:test` + `scripts/check-web-sync.sh` |
| 4 | D1 i18n/theme docs · D2 telemetry/privacy docs + MEMORY | `scripts/check-doc-links.sh` |
| 5 | F final verification, release APK, emulator smoke, PR | see Task F |

Critical path: T0 → T4/T5/T6/T8 → T12/T13/T14 → T17 → F.

---

### Task 0: Shared contracts

The types and interfaces every later task compiles against, so waves 1–2 can run in parallel.

**Files:**
- Create: `core/model/src/main/kotlin/io/github/rupakc/travelplanner/core/model/settings/ConsentChoice.kt`
- Create: `core/model/src/main/kotlin/io/github/rupakc/travelplanner/core/model/config/ClientConfig.kt`
- Create: `core/model/src/main/kotlin/io/github/rupakc/travelplanner/core/model/telemetry/TelemetryWire.kt`
- Create: `core/common/src/main/kotlin/io/github/rupakc/travelplanner/core/common/AppLanguage.kt`
- Create: `core/domain/src/main/kotlin/io/github/rupakc/travelplanner/core/domain/telemetry/TelemetryTypes.kt`
- Create: `core/data/src/main/kotlin/io/github/rupakc/travelplanner/core/data/telemetry/TelemetryContracts.kt`
- Create: `core/testing/src/main/kotlin/io/github/rupakc/travelplanner/core/testing/fake/FakeTelemetry.kt`
- Create: `config/web-source.properties`, `tools/web-sync/websource.py`
- Test: `core/model/src/test/kotlin/io/github/rupakc/travelplanner/core/model/telemetry/TelemetryWireTest.kt`

**Interfaces (Produces):**
```kotlin
// core:model
enum class ConsentChoice(val wire: String) { GRANTED("granted"), DENIED("denied") }
@Serializable data class ClientConfigDto(@SerialName("public_base_url") val publicBaseUrl: String? = null, val telemetry: TelemetryConfigDto? = null)
@Serializable data class TelemetryConfigDto(val enabled: Boolean = false, @SerialName("flush_interval_s") val flushIntervalS: LenientInt? = null, @SerialName("max_batch") val maxBatch: LenientInt? = null)
@Serializable data class TelemetryBatchDto(val client: TelemetryClientDto, val events: List<TelemetryEventDto>)
@Serializable data class TelemetryClientDto(@SerialName("anon_id") val anonId: String, @SerialName("session_id") val sessionId: String,
    val platform: String = "android", @SerialName("app_version") val appVersion: String, val locale: String)
@Serializable data class TelemetryEventDto(val event: String, val ts: Long, val screen: String? = null, val props: JsonObject = JsonObject(emptyMap()))

// core:common
/** The effective UI language: always one of en, es, de, fr, hi, zh, bn ("en" when the device language is unsupported). */
interface AppLanguage { fun current(): String }

// core:domain telemetry
/** One queued event. A plain class: identity equality, like the web's queue entries (two identical taps are two events). */
class QueuedEvent(val event: String, val ts: Long, val props: Map<String, Any>, val screen: String, val sessionId: String)
sealed interface SendOutcome { data object Sent : SendOutcome; data object Dropped : SendOutcome; data object Retry : SendOutcome
    data class RetryAfter(val ms: Long) : SendOutcome; data object Disabled : SendOutcome }
data class TelemetrySettings(val flushIntervalMs: Long, val maxBatch: Int) { companion object }
object TelemetryEvents { /* each a const val */ APP_OPEN = "app_open"; APP_BACKGROUND = "app_background"; SESSION_START = "session_start"; SCREEN_VIEW = "screen_view"
    SCREEN_TIME = "screen_time"; UI_TAP = "ui_tap"; UI_TOGGLE = "ui_toggle"; THEME_CHANGE = "theme_change"; LANGUAGE_CHANGE = "language_change"; CONSENT = "consent"; PERF = "perf" }

// core:data telemetry
interface Telemetry { fun emit(event: String, props: Map<String, Any?> = emptyMap()); fun screenView(routeTemplate: String); fun currentRoute(): String }
interface TelemetryLifecycle { fun start(); fun onForeground(); fun onBackground() }
sealed interface ConsentPromptState { data object Hidden : ConsentPromptState; data object FirstRun : ConsentPromptState; data class Reopened(val allowed: Boolean) : ConsentPromptState }
interface ConsentRepository {
    val allowed: Flow<Boolean>            // stored choice, else !ConsentRules.required(zone at collection time)
    suspend fun allowedNow(): Boolean
    val choices: Flow<ConsentChoice>      // explicit choices only, not replayed
    val prompt: StateFlow<ConsentPromptState>  // initial Hidden
    suspend fun choose(choice: ConsentChoice)
    fun openChoices()
    fun closeChoices()
}
interface ClientConfigRepository { /** null = telemetry off for this server (404, error, disabled). */ suspend fun telemetrySettings(): TelemetrySettings? }
internal interface TelemetrySender { suspend fun send(sessionId: String, events: List<QueuedEvent>): SendOutcome }
internal interface TelemetryQueueStore { suspend fun append(events: List<QueuedEvent>); suspend fun claim(nowMs: Long): List<QueuedEvent>; suspend fun clear() }
internal interface TelemetryFlushScheduler { fun schedule(); fun cancel() }
```
`FakeTelemetry` (records `events: List<Pair<String, Map<String, Any?>>>` and `screens: List<String>`), `FakeConsentRepository` (a settable `allowed`, recorded `chosen`, `openCount`), `FakeClientConfigRepository(var settings: TelemetrySettings?)` and `FakeAppLanguage(var code: String = "en")`.
`config/web-source.properties`: `web.repo=rupakc/travel-planner` and `web.sha=e8c78cb600e1e25bc5778ee42bc03333ad05c530`. `tools/web-sync/websource.py`: `read_pin(repo_root: Path) -> tuple[str, str]`.

- [ ] **Step 1: Write the failing test** `TelemetryWireTest`
  - `client block encodes snake case and android platform`: `TravelJson.encodeToString(TelemetryClientDto("a","s",appVersion="0.1.5",locale="zh-CN"))` contains `"anon_id":"a"`, `"session_id":"s"`, `"platform":"android"` and `"app_version":"0.1.5"`.
  - `config decodes the live shape`: `{"public_base_url":"http://x","telemetry":{"enabled":true,"flush_interval_s":15,"max_batch":50},"features":{"telemetry":true}}` gives `telemetry.enabled == true`, `flushIntervalS == 15` and `maxBatch == 50` (unknown `features` ignored).
  - `missing telemetry block decodes as null`.
- [ ] **Step 2: Run it.** `$G :core:model:test --tests '*TelemetryWireTest'` → FAIL (unresolved references).
- [ ] **Step 3: Create the files above** with KDoc on every public type. Use the `Lenient*` serializer the model already uses for numbers.
- [ ] **Step 4: Run.** `$G :core:model:test :core:domain:test :core:data:testLocalDebugUnitTest :core:testing:assembleLocalDebug` → PASS.
- [ ] **Step 5: Commit** `core: Phase 0 parity contracts (telemetry, consent, client config, app language)`.

---

### Task 1: Design-token generator (wave 1)

**Files:**
- Create: `tools/web-sync/gen_tokens.py`, `tools/web-sync/test_gen_tokens.py`, `tools/web-sync/fixtures/tokens_min.json`
- Regenerate: `core/designsystem/src/main/kotlin/io/github/rupakc/travelplanner/core/designsystem/theme/Palette.kt`
- Create (generated): `core/designsystem/src/main/kotlin/io/github/rupakc/travelplanner/core/designsystem/theme/DarkPalette.kt`

**Interfaces:**
- Consumes: `websource.read_pin` (T0).
- Produces: `internal object Palette` (every light family × stop 50–950, plus `White`), with names `<Family PascalCase><stop>` (`Teal700`, `Primary600`, `Accent500`, `White`). `internal object DarkPalette` has the same names from the `dark` block (`DarkPalette.White` = `#121a2b`). CLI: `gen_tokens.py generate|check --tokens <path to tokens.json>`. `check` exits 1 and prints the stale file when the output differs.

- [ ] **Step 1: Write the failing tests** in `test_gen_tokens.py` (unittest, fixture `tokens_min.json` with `teal` 600/700, `white`, and a `dark` block):
  - `test_names_and_hex`: the output contains `    val Teal700 = Color(0xFF0F766E)` and `    val White = Color(0xFFFFFFFF)`.
  - `test_dark_object`: `DarkPalette` contains `val White = Color(0xFF121A2B)` when the fixture's dark white is `#121a2b`.
  - `test_header_names_pinned_sha`: the first line is `// Generated by tools/web-sync/gen_tokens.py from rupakc/travel-planner@<sha> frontend/design/tokens.json. Do not edit.`
  - `test_check_detects_drift`: changing one hex in the written file makes `check` return 1.
  - `test_rejects_bad_hex`: `#12345` raises `ValueError`.
- [ ] **Step 2: Run** `python3 -m unittest tools/web-sync/test_gen_tokens.py -v` → FAIL.
- [ ] **Step 3: Implement `gen_tokens.py`.** Families in the JSON's order and stops in ascending order. Emit a ktlint-clean layout (package line, `import androidx.compose.ui.graphics.Color`, 4-space indent, trailing newline) and uppercase hex. Skip keys starting with `$`.
- [ ] **Step 4: Run the tests** → PASS. Then generate from the pinned web commit: `tmp=$(mktemp -d); git -C ~/travel-planner archive e8c78cb600e1e25bc5778ee42bc03333ad05c530 frontend/design/tokens.json | tar -x -C $tmp; python3 tools/web-sync/gen_tokens.py generate --tokens $tmp/frontend/design/tokens.json`. Then run `$G spotlessCheck :core:designsystem:testLocalDebugUnitTest assembleLocalDebug` → PASS (`ContrastTest` unchanged: the light values equal today's Palette, as checked while planning).
- [ ] **Step 5: Commit** `core: generate Palette and DarkPalette from the web tokens`.

---

### Task 2: Strings generator (wave 1)

**Files:**
- Create: `tools/web-sync/gen_strings.py`, `tools/web-sync/test_gen_strings.py`, `tools/web-sync/fixtures/strings/` (a fake web `locales/{en,de,fr}/x.json` and a fake module `mod/src/main/res/values/strings.xml`)

**Interfaces:**
- Consumes: `websource.read_pin` (T0).
- Produces:
  - The CLI below, used by T3 and TR-1…TR-8. `<M>` is a module path such as `feature/results`.
    ```
    gen_strings.py suggest  --web <locales dir> --module <M>             # adds candidates to config/i18n/web-keys/<M with / → ->.json; keeps existing entries
    gen_strings.py generate --web <locales dir> [--module <M>]...        # writes <M>/src/main/res/values-<lang>/strings_web.xml, lang in de fr hi zh bn
    gen_strings.py check    --web <locales dir> [--module <M>]...        # exit 1 on any problem below
    ```
  - Map file format: `{"strings": {"<android name>": "<ns>:<dotted.key>"}, "plurals": {"<android name>": "<ns>:<base key without _one/_other>"}}`.
- Rules:
  - **Match.** A mapping is valid only when the Android English, normalised, equals the web English, normalised.
    - Normalising replaces `{{x}}` and `%n$s`/`%n$d`/`%n$f`/`%s`/`%d` with `§` and unescapes Android (`\'`, `\"`, `\n`, `\@`, `\?`, `%%`).
    - The literal word `Voyager` in the web value matches a `%n$s` placeholder in Android (brand rule M5). When generating, `Voyager` in the translation becomes that placeholder.
    - For plurals, every Android `<item>` must match the web `_<quantity>` value of `en`.
    - Values with `<tag>` markup are never mappable.
  - **Placeholders.** The i-th web `{{name}}` in English maps to the i-th Android specifier in English, and a name that repeats must map to the same specifier. Translations replace `{{name}}` with that specifier. A plural form may drop `{{count}}`.
  - **Escaping out.** `&` → `&amp;`, `<` → `&lt;`, `'` → `\'`, `"` → `\"`, newline → `\n`, and a leading `@` or `?` gets a backslash. `%` → `%%` only when the Android English has format arguments.
  - **Plural categories:** `{"de": ["one","other"], "fr": ["one","many","other"], "hi": ["one","other"], "zh": ["other"], "bn": ["one","other"]}`.
  - **Suggest.** A suggestion prefers the web namespace listed for the module:
    - `feature/results`: results, sections
    - `feature/search`: search
    - `feature/chat`: chat
    - `feature/plan`: plan, itinerary
    - `feature/alerts`: alerts
    - `feature/settings`: account, privacy, common
    - `feature/auth`: auth
    - `feature/share`: account, plan
    - `core/ui`: sections, itinerary, privacy, common
    - all other modules: common, misc

    Otherwise it takes the first key in sorted order. Strings with `translatable="false"` are skipped.
  - **Check fails on:**
    - a stale or missing generated file;
    - a map entry whose web key is missing or whose English no longer matches (drift on either side);
    - a key in `values-<lang>` (generated plus hand files, ignoring `translatable="false"`) that is missing, duplicated or extra compared with `values/`;
    - plural items whose categories differ from the table;
    - a hand-translated string whose set of format specifiers differs from the English.
  - Generated file header: `<!-- Generated by tools/web-sync/gen_strings.py from rupakc/travel-planner@<sha>. Do not edit; see docs/Localization.md. -->`. Entries are sorted by Android name.

- [ ] **Step 1: Write the failing tests** (unittest, fixtures):
  - `test_exact_match_maps` and `test_brand_literal_maps_to_placeholder` (web `"Help improve Voyager"` ↔ Android `"Help improve %1$s"`, so German `"Hilf, Voyager zu verbessern"` becomes `"Hilf, %1$s zu verbessern"`).
  - `test_placeholder_order_mapping`: web `"{{a}} of {{b}}"` ↔ Android `"%1$s of %2$s"`; a web translation `"{{b}} von {{a}}"` becomes `"%2$s von %1$s"`.
  - `test_escapes_apostrophe_quote_ampersand_lt_leading_at`: French `"l'itinéraire & <plan>"` is written as `l\'itinéraire &amp; &lt;plan>`, and a value starting with `@` gets a backslash.
  - `test_percent_becomes_double_only_with_args`.
  - `test_plural_categories_per_language`: the fr output has `one`, `many` and `other` items, and zh has only `other`.
  - `test_plural_form_without_count_keeps_other_placeholders`.
  - `test_markup_is_unmappable`.
  - `test_check_detects_english_drift`: editing the Android English of a mapped string makes `check` return 1 and name the key.
  - `test_check_detects_missing_hand_translation`.
  - `test_generate_is_idempotent`.
  - `test_suggest_prefers_module_namespace`.
- [ ] **Step 2: Run** `python3 -m unittest tools/web-sync/test_gen_strings.py -v` → FAIL.
- [ ] **Step 3: Implement `gen_strings.py`** with `xml.etree.ElementTree` for reading and deterministic writing (2-space indent, as in the existing `strings.xml` files).
- [ ] **Step 4: Run the tests** → PASS.
- [ ] **Step 5: Commit** `tools: web catalogue to strings.xml generator`.

---

### Task 3: CI web-sync check (wave 2)

**Files:**
- Create: `scripts/check-web-sync.sh`
- Modify: `.github/workflows/android.yml` (`build` job, after "Check documentation links"), `.gitignore` (`.web-source/`)

**Interfaces:**
- Consumes: the CLIs from T1 and T2, and the pin from T0.
- Produces: `scripts/check-web-sync.sh <web git dir>`. It reads `config/web-source.properties`, runs `git -C "$1" archive "$sha" frontend/design/tokens.json frontend/src/i18n/locales | tar -x -C "$tmp"`, then runs `python3 -m unittest discover -s tools/web-sync -p 'test_*.py'`, `gen_tokens.py check` and `gen_strings.py check` (all modules). Exit status is non-zero on any failure.

- [ ] **Step 1: Write the script** with `set -euo pipefail`, shellcheck-clean, and a clear message when the pinned SHA is missing locally ("run git -C <dir> fetch").
- [ ] **Step 2: Run** `scripts/check-web-sync.sh ~/travel-planner` → PASS (the map files are still empty, so `check` validates only the tokens and the absence of generated files).
- [ ] **Step 3: Add the CI steps.** One step reads the pin into `$GITHUB_OUTPUT`. A second uses the already-pinned `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1` with `repository: rupakc/travel-planner`, `ref: <sha>`, `path: .web-source`, `persist-credentials: false` and `sparse-checkout: frontend/design/tokens.json` plus `frontend/src/i18n/locales`. A third runs `scripts/check-web-sync.sh .web-source`.
- [ ] **Step 4: Verify** with `actionlint .github/workflows/android.yml` if installed (otherwise `python3 -c "import yaml,sys;yaml.safe_load(open('.github/workflows/android.yml'))"`), then rerun `scripts/check-web-sync.sh ~/travel-planner` → PASS.
- [ ] **Step 5: Commit** `ci: check generated tokens and strings against the pinned web commit`.

---

### Task 4: Telemetry rules (wave 1)

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/rupakc/travelplanner/core/domain/telemetry/{TelemetryProps.kt, ConsentRules.kt, TelemetryRoutes.kt}`
- Test: `core/domain/src/test/kotlin/io/github/rupakc/travelplanner/core/domain/telemetry/{TelemetryPropsTest.kt, ConsentRulesTest.kt, TelemetryRoutesTest.kt}`

**Interfaces (Produces):**
```kotlin
object TelemetryProps {
    val TOKEN: Regex                          // ^[A-Za-z0-9_.:/-]{1,64}$
    fun isToken(value: String): Boolean
    /** Keeps token strings, Int/Long within ±2^53 (as Long), finite Float/Double (as Double), Booleans; drops everything else. */
    fun clean(props: Map<String, Any?>): Map<String, Any>
    /** Non-token chars → '_', at most 64 chars, blank → "0.0.0". */
    fun cleanVersion(version: String): String
}
object ConsentRules { fun required(zoneId: String?): Boolean }
object TelemetryRoutes { const val OTHER = "/other"; val table: List<Pair<KClass<out AppRoute>, String>> }
```
`TelemetryRoutes.table`, where the web name is used for the same screen:

| Route | Template |
|---|---|
| `LoginRoute` | `/login` |
| `ChangePasswordRoute`, `AccountChangePasswordRoute` | `/change-password` |
| `OnboardingRoute` | `/onboarding` |
| `HomeRoute` | `/home` |
| `SearchRoute` | `/` |
| `ResultsRoute` | `/results` |
| `PlansRoute` | `/plans` |
| `PlanDetailRoute` | `/plans/:id` |
| `MyPlanRoute` | `/my-plan` |
| `ChatRoute` | `/chat` |
| `AlertsRoute` | `/alerts` |
| `NotificationsRoute` | `/notifications` |
| `GlobalSearchRoute` | `/global-search` |
| `SettingsRoute` | `/settings` |
| `SettingsEntryRoute` | `/settings/:entry` |
| `PreferencesRoute` | `/preferences` |
| `TasteProfileRoute` | `/settings/taste` |
| `FeaturesRoute` | `/settings/features` |
| `AppearanceRoute` | `/settings/appearance` |
| `ServerRoute` | `/settings/server` |
| `NotificationSettingsRoute` | `/settings/notifications` |
| `NearbyRoute` | `/nearby` |
| `ShareRoute` | `/share/:token` |

- [ ] **Step 1: Write the failing tests**
  - `TelemetryPropsTest`:
    - `free text with a space is dropped` (`"q" to "hello world"` is absent);
    - `email is dropped` (`"a@b.c"`);
    - `65 chars dropped, 64 kept`;
    - `2^53 kept, 2^53+1 dropped`;
    - `NaN and Infinity dropped`;
    - `Int is widened to Long`;
    - `null dropped`;
    - `cleanVersion("0.1.5 (debug)") == "0.1.5__debug_"`.
  - `ConsentRulesTest`:
    - `Europe/Berlin`, `Europe/London`, `Europe/Zurich`, `Atlantic/Canary`, `Atlantic/Reykjavik`, `Atlantic/Faroe` → `true`;
    - `Europe/Istanbul`, `Europe/Moscow`, `Europe/Minsk`, `Europe/Kiev`, `Europe/Kyiv`, `America/New_York`, `Asia/Kolkata`, `Atlantic/Bermuda`, `""`, `null` → `false`.
  - `TelemetryRoutesTest`:
    - `every route class is mapped exactly once`: a private exhaustive `when (route: AppRoute)` returning the KClass (adding a route breaks compilation), together with `assertEquals(24, table.size)`, `table.map { it.first }.toSet().size == 24` and every template passing `TelemetryProps.isToken`.
- [ ] **Step 2: Run** `$G :core:domain:test --tests '*telemetry*'` → FAIL.
- [ ] **Step 3: Implement** the three objects (KDoc cites `frontend/src/telemetry/consent.js` and `routes.js`).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `core: telemetry prop, consent and route rules (web parity)`.

---

### Task 5: Queue, session and send policy (wave 1)

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/rupakc/travelplanner/core/domain/telemetry/{TelemetryQueue.kt, TelemetrySession.kt, SendPolicy.kt}`
- Test: `core/domain/src/test/kotlin/io/github/rupakc/travelplanner/core/domain/telemetry/{TelemetryQueueTest.kt, TelemetrySessionTest.kt, SendPolicyTest.kt}`

**Interfaces (Produces):**
```kotlin
class TelemetryQueue(private val cap: Int = 500) {           // not thread-safe: the engine confines it
    val size: Int
    fun push(event: QueuedEvent)                              // drops the oldest beyond cap
    fun pending(inFlight: Set<QueuedEvent>): List<QueuedEvent>
    fun takeBatch(max: Int, inFlight: Set<QueuedEvent>): List<QueuedEvent>   // one session: the first non-in-flight event's
    fun remove(batch: Collection<QueuedEvent>)
    fun prepend(saved: List<QueuedEvent>, nowMs: Long)       // keeps |now - ts| < 7 d, token screen/event; cap keeps the newest
    fun clear()
}
data class SessionTouch(val id: String, val isNew: Boolean, val persist: Boolean)
class TelemetrySession(private val newId: () -> String) {
    fun restore(id: String?, lastMs: Long?)
    fun touch(nowMs: Long): SessionTouch   // new if none, idle > 30 min, or clock moved back > 30 min; persist if new or > 10 s since last write
}
object SendPolicy {
    /** null status = network error. */
    fun classify(status: Int?, retryAfter: String?, nowMs: Long): SendOutcome
    fun parseRetryAfter(value: String?, nowMs: Long): Long   // seconds or HTTP date → ms, capped at 3_600_000, invalid/absent → 0
}
class Backoff { val blockedUntil: Long; fun onSuccess(); fun onFailure(intervalMs: Long, nowMs: Long); fun block(untilMs: Long) }
fun TelemetrySettings.Companion.fromServer(flushIntervalS: Int?, maxBatch: Int?): TelemetrySettings
```
`classify` mapping:

| Status | Outcome |
|---|---|
| null, 408, 5xx | `Retry` |
| 2xx | `Sent` |
| 404 | `Disabled` |
| 429 | `RetryAfter(parsed)`, or `Retry` when the parsed value is 0 |
| any other 4xx | `Dropped` |

`Backoff.onFailure`: increment `failures`. From 3 failures on, set `blockedUntil = now + min(300_000, interval * 2^(failures - 2))`. `block` keeps the later of the two times.

- [ ] **Step 1: Write the failing tests**
  - Queue:
    - `cap 500 drops the oldest`;
    - `takeBatch takes one session and skips in-flight`;
    - `two equal-looking events are both kept` (identity);
    - `prepend drops events older than 7 days and keeps the newest 500`.
  - Session:
    - `same id within 30 min`;
    - `new id after 30 min + 1 ms idle`;
    - `new id when the clock moves back more than 30 min`;
    - `persist only when new or after 10 s`.
  - SendPolicy:
    - `204 sent`, `404 disabled`, `413 and 422 dropped`, `408 retry`, `500 retry`, `null retry`;
    - `429 with "30" is RetryAfter(30000)`;
    - `429 with an HTTP date 10 s ahead is about 10000`;
    - `Retry-After 7200 is capped at 3600000`;
    - `backoff starts at the third failure: interval 15000 gives 30000, then 60000, capped at 300000`;
    - `fromServer(0, 500) clamps to (1000 ms, 100)`, `fromServer(null, null) is (15000, 50)`.
- [ ] **Step 2: Run** `$G :core:domain:test --tests '*telemetry*'` → FAIL.
- [ ] **Step 3: Implement** (HTTP dates via `java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME`).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `core: telemetry queue, session and send policy (web parity)`.

---

### Task 6: Telemetry and config APIs (wave 1)

**Files:**
- Create: `core/network/src/main/kotlin/io/github/rupakc/travelplanner/core/network/api/TelemetryApis.kt`, `core/network/src/main/kotlin/io/github/rupakc/travelplanner/core/network/di/TelemetryNetworkModule.kt`
- Test: `core/network/src/test/kotlin/io/github/rupakc/travelplanner/core/network/api/TelemetryApisTest.kt`

**Interfaces (Produces):**
```kotlin
interface ClientConfigApi { @GET("api/config") suspend fun config(): Response<ClientConfigDto> }
interface TelemetryApi { @POST("api/telemetry") suspend fun send(@Header("Content-Encoding") encoding: String?, @Body body: RequestBody): Response<Unit> }
```
Both are provided `@Singleton` from the `@DefaultHttp Retrofit`, so they get auth, the base URL swap and redaction.

- [ ] **Step 1: Write the failing tests** (MockWebServer, `TravelJson` converter as in the existing API tests):
  - `config 404 is a Response with code 404, not an exception`;
  - `config decodes enabled and max_batch`;
  - `send posts to /api/telemetry with Content-Encoding gzip and the raw body bytes`.
- [ ] **Step 2: Run** `$G :core:network:testLocalDebugUnitTest --tests '*TelemetryApisTest'` → FAIL.
- [ ] **Step 3: Implement** the two interfaces and the Hilt module.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `core: /api/config and /api/telemetry Retrofit APIs`.

---

### Task 7: AI `locale` field (wave 1)

**Files:**
- Create: `core/domain/src/main/kotlin/io/github/rupakc/travelplanner/core/domain/i18n/AppLanguages.kt`
- Create: `core/network/src/main/kotlin/io/github/rupakc/travelplanner/core/network/AiLocaleInterceptor.kt`
- Modify: `core/network/src/main/kotlin/io/github/rupakc/travelplanner/core/network/di/NetworkModule.kt` (add the interceptor to the `@DefaultHttp`, `@AiHttp` and `@StreamingHttp` clients, after `auth` and before `baseUrl`)
- Create: `app/src/main/kotlin/io/github/rupakc/travelplanner/i18n/AppCompatAppLanguage.kt`, `app/src/main/kotlin/io/github/rupakc/travelplanner/di/LanguageModule.kt`
- Test: `core/domain/src/test/.../i18n/AppLanguagesTest.kt`, `core/network/src/test/.../AiLocaleInterceptorTest.kt`

**Interfaces:**
- Consumes: `AppLanguage` (T0).
- Produces:
```kotlin
object AppLanguages {
    val CODES: List<String>   // en, es, de, fr, hi, zh, bn (picker order)
    /** First app-locale tag whose base language is supported, else the first such system tag, else "en". Base = subtag before '-', lowercased ("zh-Hant-TW" → zh). */
    fun resolve(appLocaleTags: List<String>, systemLocaleTags: List<String>): String
    fun intlTag(code: String): String   // zh → zh-CN, hi → hi-IN, bn → bn-IN, else code
}
class AiLocaleInterceptor @Inject constructor(private val language: AppLanguage) : Interceptor
```
For a POST whose host is the placeholder backend host, whose encoded path is in `AI_PATHS` and whose body is `application/json` that parses as a JSON object without `locale`, the interceptor rewrites the body with `"locale": language.current()`. Everything else passes through untouched, including parse failures. `AI_PATHS` is: `/api/search`, `/api/search/sync`, `/api/chat`, `/api/discover`, `/api/compare/destination`, `/api/stress-test`, `/api/flights`, `/api/flights/filtered`, `/api/hotels`, `/api/hotels/filtered`, `/api/activities`, `/api/activities/filtered`, `/api/visa`, `/api/sim`, `/api/tips`, `/api/getting-around`, `/api/forex`, `/api/itinerary`, `/api/events`, `/api/walking-tours`. `AppCompatAppLanguage.current()` is `AppLanguages.resolve(AppCompatDelegate.getApplicationLocales().toLanguageTags() split on ',', Resources.getSystem().configuration.locales.toLanguageTags() split on ',')`. Drafts' `searchKey` is untouched, because the field is added only on the wire.

- [ ] **Step 1: Write the failing tests**
  - `AppLanguagesTest`:
    - `app de wins over system fr`;
    - `system ja then fr resolves to fr`;
    - `zh-TW resolves to zh`;
    - `empty resolves to en`;
    - `intlTag zh is zh-CN and de is de`.
  - `AiLocaleInterceptorTest` (MockWebServer and an OkHttp client with the interceptor, using the placeholder host via the existing `BaseUrlInterceptor` test setup):
    - `search body gains locale de`;
    - `keeps an existing locale`;
    - `plans POST is untouched`;
    - `GET is untouched`;
    - `multipart import is untouched`;
    - `foreign host is untouched`;
    - `non-object JSON is untouched`.
- [ ] **Step 2: Run** `$G :core:domain:test :core:network:testLocalDebugUnitTest --tests '*Locale*' --tests '*AppLanguages*'` → FAIL.
- [ ] **Step 3: Implement** the three classes and the Hilt binding `@Binds AppLanguage`.
- [ ] **Step 4: Run** → PASS, then `$G assembleLocalDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `core: send the UI language as locale on AI requests`.

---

### Task 8: Telemetry prefs store (wave 1)

**Files:**
- Create: `core/datastore/src/main/kotlin/io/github/rupakc/travelplanner/core/datastore/{TelemetryStores.kt, TelemetryPrefsStoreImpl.kt, TelemetryDataStoreModule.kt}`
- Test: `core/datastore/src/test/kotlin/io/github/rupakc/travelplanner/core/datastore/TelemetryPrefsStoreTest.kt`

**Interfaces (Produces):**
```kotlin
data class StoredSession(val id: String, val lastMs: Long)
interface TelemetryPrefsStore {
    suspend fun anonId(): String                 // random UUID, created and persisted on the first call; an invalid stored value is replaced
    val consent: Flow<ConsentChoice?>
    suspend fun setConsent(choice: ConsentChoice)
    suspend fun session(): StoredSession?
    suspend fun setSession(session: StoredSession)
}
object TelemetryDataStoreFiles { const val TELEMETRY = "telemetry" }   // datastore/telemetry.preferences_pb
```
Sign-out does not clear this store (web parity: `tp_anon` and `tp_consent` survive). Backup exclusion is Task 17.

- [ ] **Step 1: Write the failing tests** (Robolectric, temp DataStore as in the existing store tests):
  - `anonId is stable across calls and a UUID`;
  - `a non-UUID stored value is replaced`;
  - `consent round-trips and starts null`;
  - `session round-trips`.
- [ ] **Step 2: Run** `$G :core:datastore:testLocalDebugUnitTest --tests '*TelemetryPrefsStoreTest'` → FAIL.
- [ ] **Step 3: Implement** with its own `PreferenceDataStoreFactory.create { preferencesDataStoreFile("telemetry") }` in the new Hilt module.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `core: telemetry prefs store (anon id, consent, session)`.

---

### Task 9: Theme: light default and designed dark (wave 2)

**Files:**
- Modify: `core/designsystem/src/main/kotlin/.../theme/Color.kt` (only `DarkColors` and `DarkExtendedColors`)
- Modify: `core/model/src/main/kotlin/.../settings/Settings.kt` (`themeMode = ThemeMode.LIGHT`)
- Modify: `core/datastore/src/main/kotlin/.../PreferencesStores.kt` (missing or invalid `theme_mode` → `ThemeMode.LIGHT`)
- Test: `core/designsystem/src/test/.../theme/{ContrastTest.kt, DarkSchemeTokensTest.kt}`, plus the existing datastore settings test

**Interfaces:**
- Consumes: `DarkPalette` (T1).
- Produces: dark roles from the web dark tokens:

| Role | Token | Hex |
|---|---|---|
| `background` | `DarkPalette.Gray50` | #0b1120 |
| `surface`, `surfaceContainerLow` | `DarkPalette.White` | #121a2b |
| `surfaceContainerLowest`, `surfaceDim` | `DarkPalette.Gray50` | |
| `surfaceContainer`, `surfaceVariant` | `DarkPalette.Gray100` | |
| `surfaceContainerHigh`, `surfaceBright` | `DarkPalette.Gray200` | |
| `surfaceContainerHighest` | `DarkPalette.Gray300` | |
| `onBackground`, `onSurface`, `inverseSurface` | `DarkPalette.Gray900` | |
| `inverseOnSurface` | `DarkPalette.Gray100` | |
| `onSurfaceVariant` | `DarkPalette.Gray700` | ≥ 5.5:1 on every container |
| `outline` | `DarkPalette.Gray600` | ≥ 4:1 on every container |
| `outlineVariant` | `DarkPalette.Gray200` | |
| `skeletonBase` | `DarkPalette.Gray100` | |
| `skeletonHighlight` | `DarkPalette.Gray200` | |

  Primary, secondary, tertiary, error and the accents are unchanged. Light is unchanged.

- [ ] **Step 1: Write the failing tests**
  - `DarkSchemeTokensTest`:
    - `dark surface is the web dark page surface` (`DarkColors.surface == DarkPalette.White`);
    - `background is dark gray-50`;
    - `onSurface is dark gray-900`.
  - `ContrastTest`: add `outline on surfaceContainerHighest ≥ 3.0` to the border pairs.
  - Datastore settings test: `missing theme key reads LIGHT`.
  - Model: `AppSettings().themeMode == ThemeMode.LIGHT`.
- [ ] **Step 2: Run** `$G :core:designsystem:testLocalDebugUnitTest :core:datastore:testLocalDebugUnitTest :core:model:test` → FAIL.
- [ ] **Step 3: Implement** the mappings above. If a `ContrastTest` pair fails, move that role one stop toward higher contrast and note the change in the commit body.
- [ ] **Step 4: Run** → PASS, then `$G assembleLocalDebug`.
- [ ] **Step 5: Commit** `core: light by default; dark surfaces from the web dark tokens`. Commit body: "Decision: installs that never chose a theme now get LIGHT (web default), superseding the SYSTEM default."

---

### Task 10: UI events and the social sign-in button (wave 1)

**Files:**
- Create: `core/designsystem/src/main/kotlin/.../component/UiEvents.kt`, `core/designsystem/src/main/kotlin/.../component/SocialSignInButton.kt`
- Create: `core/designsystem/src/main/res/drawable/{ic_brand_apple.xml, ic_brand_google.xml, ic_brand_facebook.xml, ic_brand_github.xml}` (paths ported from the web's `frontend/src/components/auth/providerIcons.jsx`)
- Modify: `core/designsystem/src/main/kotlin/.../component/Buttons.kt`, `Chips.kt` (add `tid: String? = null` as the last parameter)
- Test: `core/designsystem/src/test/.../component/{UiEventsTest.kt, SocialSignInButtonTest.kt}`

**Interfaces (Produces):**
```kotlin
enum class UiEventKind { TAP, TOGGLE }
fun interface UiEventSink { fun onUiEvent(kind: UiEventKind, tid: String, on: Boolean?) }
val LocalUiEventSink: ProvidableCompositionLocal<UiEventSink>   // default no-op
// TpButton(..., tid: String? = null): on click (not while loading) reports TAP with tid ?: "auto:button", then calls onClick
// TpChip(..., tid: String? = null): reports TOGGLE with tid ?: "auto:chip", on = !selected, then onClick
enum class SocialProvider(val id: String, val displayName: String) { APPLE("apple", "Apple"), GOOGLE("google", "Google"), FACEBOOK("facebook", "Facebook"), GITHUB("github", "GitHub") }
@Composable fun SocialSignInButton(provider: SocialProvider, label: String, onClick: () -> Unit, modifier: Modifier = Modifier)  // reports TAP "auth.provider.<id>"
```
Sink calls are wrapped in `runCatching`, so a throwing sink never blocks `onClick`. The button colours are the web's literal brand colours (light and dark variants from `SocialSignInButtons.jsx`), kept in a private `SocialBrandColors` in this file. Dark is decided by `TravelTheme.colors.surface.luminance() < 0.5`. Minimum height is `TravelTheme.spacing.minTouchTarget`, the icon is decorative (`contentDescription = null`) and the label wraps at 200 % font scale. Light and dark previews for all four.

- [ ] **Step 1: Write the failing tests** (Compose on Robolectric with a recording sink):
  - `button click reports TAP with its tid then calls onClick`;
  - `button without tid reports auto:button`;
  - `loading button reports nothing`;
  - `throwing sink still calls onClick`;
  - `chip reports TOGGLE with on = !selected`;
  - `social button reports auth.provider.github and shows the label`;
  - `social button is at least 48 dp tall`.
- [ ] **Step 2: Run** `$G :core:designsystem:testLocalDebugUnitTest` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS, then `$G spotlessCheck detekt :core:designsystem:lintLocalDebug`.
- [ ] **Step 5: Commit** `core: UI event sink on TpButton/TpChip; SocialSignInButton`.

---

### Task 11: Consent prompt (wave 2)

**Files:**
- Create: `core/ui/src/main/kotlin/io/github/rupakc/travelplanner/core/ui/privacy/ConsentPrompt.kt`
- Modify: `core/ui/src/main/res/values/strings.xml`, `values-es/strings.xml`
- Test: `core/ui/src/test/.../privacy/ConsentPromptTest.kt`

**Interfaces:**
- Consumes: `TpButton(tid)` (T10), `openInCustomTab` (existing).
- Produces:
```kotlin
object PrivacyLinks { const val POLICY_URL = "https://github.com/rupakc/travel-planner/blob/main/docs/Privacy.md" }
/** Non-modal card for the bottom of the screen. [currentAllowed] null = first run (no close button, no pressed state). */
@Composable fun ConsentPrompt(currentAllowed: Boolean?, onAllow: () -> Unit, onDeny: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier)
```
Strings (English from the web `privacy.json`):

| Name | English |
|---|---|
| `privacy_prompt_title` | "Help improve %1$s" (brand) |
| `privacy_prompt_body` | the web body verbatim |
| `privacy_allow` | "Allow" |
| `privacy_deny` | "No thanks" |
| `privacy_policy` | "Privacy policy" |
| `privacy_close` | "Close" |

  Spanish is taken from the web `es/privacy.json`. tids: `privacy.allow`, `privacy.deny`, `privacy.close`, `privacy.prompt-policy`. Semantics: `paneTitle` = the title, `liveRegion = Polite`. When reopened, the button in force has `selected = true`. Works at 200 % font scale (buttons wrap in a `FlowRow`).

- [ ] **Step 1: Write the failing tests**:
  - `first run has no close button`;
  - `reopened shows close and marks Allow selected when allowed`;
  - `allow and deny call their lambdas`;
  - `policy link opens the policy URL` (an intercepted Custom Tab launch through `Shadows`).
- [ ] **Step 2: Run** `$G :core:ui:testLocalDebugUnitTest --tests '*ConsentPromptTest'` → FAIL.
- [ ] **Step 3: Implement**, with light and dark previews.
- [ ] **Step 4: Run** → PASS, then `$G :core:ui:lintLocalDebug`.
- [ ] **Step 5: Commit** `core: consent prompt (web ConsentPrompt parity)`.

---

### Task 12: Consent and client-config repositories (wave 2)

**Files:**
- Create: `core/data/src/main/kotlin/io/github/rupakc/travelplanner/core/data/telemetry/{ConsentRepositoryImpl.kt, ClientConfigRepositoryImpl.kt}`, `core/data/src/main/kotlin/.../di/ConsentModule.kt`
- Modify: `core/data/src/main/kotlin/.../impl/AccountRepositories.kt` (`FeedbackRepositoryImpl.sendAnalytics` returns `AppResult.Success(Unit)` without a call when `!consent.allowedNow()`)
- Test: `core/data/src/test/.../telemetry/{ConsentRepositoryImplTest.kt, ClientConfigRepositoryImplTest.kt}`

**Interfaces:**
- Consumes: `ConsentRepository`, `ClientConfigRepository` (T0), `ConsentRules` (T4), `TelemetrySettings.fromServer` (T5), `ClientConfigApi` (T6), `TelemetryPrefsStore` (T8), `BaseUrlProvider` (existing).
- Produces:
  - `ConsentRepositoryImpl(prefs: TelemetryPrefsStore, zone: () -> String = { ZoneId.systemDefault().id }, @ApplicationScope scope)`. `prompt` is `FirstRun` when `required(zone()) && choice == null`, `Reopened(allowed)` after `openChoices()`, and `Hidden` after `choose` or `closeChoices`.
  - `ClientConfigRepositoryImpl(api, baseUrl: BaseUrlProvider)`: one call per base URL per process, behind a `Mutex`. 404, any error or `enabled != true` → null, cached.

- [ ] **Step 1: Write the failing tests** (Turbine; fake prefs; MockWebServer for config):
  - `EU zone with no choice: allowed false and prompt FirstRun`;
  - `non-EU with no choice: allowed true and prompt Hidden`;
  - `choose GRANTED: allowed true, prompt Hidden, choices emits GRANTED`;
  - `denied stays denied in any zone`;
  - `allowed follows the zone at call time` (zone lambda switches from America/New_York to Europe/Paris and `allowedNow()` flips to false);
  - `openChoices shows Reopened(current)`;
  - `404 is cached for the server` (two calls, one request);
  - `enabled false is null`;
  - `refetches after a base URL change`;
  - `clamps flush interval and max batch`;
  - `sendAnalytics is skipped when not allowed` (no request recorded).
- [ ] **Step 2: Run** `$G :core:data:testLocalDebugUnitTest --tests '*telemetry*' --tests '*Feedback*'` → FAIL.
- [ ] **Step 3: Implement** and bind both in `ConsentModule`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `core: consent and client config repositories; gate analytics on consent`.

---

### Task 13: Uploader, queue file and flush worker (wave 2)

**Files:**
- Create: `core/data/src/main/kotlin/io/github/rupakc/travelplanner/core/data/telemetry/{TelemetryUploader.kt, TelemetryQueueFile.kt, QueuedEventJson.kt, TelemetryFlushWorker.kt, WorkManagerFlushScheduler.kt}`, `core/data/src/main/kotlin/.../di/TelemetryWorkModule.kt`
- Modify: `core/data/build.gradle.kts`: add `implementation(libs.androidx.work.runtime)`, `implementation(libs.androidx.hilt.work)`, `ksp(libs.androidx.hilt.compiler)` and `testImplementation(libs.androidx.work.testing)` (mirrors `core/notifications/build.gradle.kts`)
- Test: `core/data/src/test/.../telemetry/{TelemetryUploaderTest.kt, TelemetryQueueFileTest.kt, TelemetryFlushWorkerTest.kt}`

**Interfaces:**
- Consumes: `TelemetrySender`, `TelemetryQueueStore`, `TelemetryFlushScheduler`, `ClientConfigRepository`, `ConsentRepository` (T0), `SendPolicy`, `TelemetryQueue.prepend` rules (T5), `TelemetryProps.cleanVersion` (T4), `TelemetryApi` (T6), `AppLanguages.intlTag` (T7), `TelemetryPrefsStore` (T8), `AppBuildInfo`.
- Produces:
  - **`TelemetryUploader : TelemetrySender`.**
    - Client block: `anonId()`; `sessionId`; `platform = "android"`; `cleanVersion(buildInfo.versionName)`; `intlTag(language.current())`.
    - Props become a `JsonObject` (String/Long/Double/Boolean primitives).
    - The `TravelJson` bytes are gzipped with okio `GzipSink` and sent with `Content-Encoding: gzip`.
    - `IOException` → `Retry`; otherwise `SendPolicy.classify(code, header("Retry-After"), now)`.
  - **`TelemetryQueueFile : TelemetryQueueStore`.**
    - File `context.noBackupFilesDir/telemetry/queue.json`, format `{"v":1,"events":[{"event","ts","screen","sid","props"}]}`, behind a `Mutex`.
    - `append` merges with what is on disk and keeps the newest 500.
    - `claim` reads, deletes and filters (7 days, tokens). A corrupt file is deleted and `[]` returned.
  - **`TelemetryFlushWorker`** (`@HiltWorker`, `CoroutineWorker`):
    - If consent is not allowed or the config is null: `clear()` and succeed.
    - Otherwise claim, then send batches (one session, at most `maxBatch` each).
    - `Retry`/`RetryAfter`: `append(rest)`, then `Result.retry()` while `runAttemptCount < 3`, else `success()` (the events stay on disk).
    - `Disabled`: `clear()`.
  - **`WorkManagerFlushScheduler`**: unique work `telemetry_flush`, `ExistingWorkPolicy.KEEP`, `NetworkType.CONNECTED`, exponential backoff 30 s. `cancel()` cancels the unique work.

- [ ] **Step 1: Write the failing tests**
  - Uploader (MockWebServer):
    - `body is gzip and decodes to platform android`;
    - `locale is zh-CN for zh`;
    - `204 is Sent, 404 Disabled, 422 Dropped, 429 with Retry-After 30 is RetryAfter(30000)`;
    - `socket failure is Retry`.
  - Queue file (Robolectric):
    - `append then claim round-trips`;
    - `claim removes the file so a second claim is empty`;
    - `append during a claimed drain is not overwritten` (claim, append B, append rest A, claim → A + B);
    - `events older than 7 days are dropped`;
    - `corrupt file yields empty and is deleted`.
  - Worker (`TestListenableWorkerBuilder` + fakes):
    - `sends and succeeds`;
    - `retry keeps the rest on disk`;
    - `not allowed clears and succeeds`.
- [ ] **Step 2: Run** `$G :core:data:testLocalDebugUnitTest --tests '*Telemetry*'` → FAIL.
- [ ] **Step 3: Implement** and bind them in `TelemetryWorkModule`.
- [ ] **Step 4: Run** → PASS, then `$G assembleLocalDebug :app:verifyHiltViewModelsLocalDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `core: telemetry uploader (gzip), queue file and WorkManager flush`.

---

### Task 14: Telemetry engine (wave 2)

**Files:**
- Create: `core/data/src/main/kotlin/io/github/rupakc/travelplanner/core/data/telemetry/TelemetryEngine.kt`, `core/data/src/main/kotlin/.../di/TelemetryModule.kt`
- Test: `core/data/src/test/.../telemetry/TelemetryEngineTest.kt` (local fakes of `TelemetrySender`, `TelemetryQueueStore` and `TelemetryFlushScheduler`; `FakeConsentRepository`, `FakeClientConfigRepository` and `FakeAppLanguage` from core:testing; a fake `TelemetryPrefsStore`)

**Interfaces:**
- Consumes: T0 contracts, `TelemetryQueue`/`TelemetrySession`/`Backoff` (T5), `TelemetryProps.clean` (T4), `TelemetryPrefsStore` (T8), `AppClock`, `@ApplicationScope`.
- Produces: `@Singleton class TelemetryEngine : Telemetry, TelemetryLifecycle`, bound to both. Behaviour (web `client.js`):
  - **Command loop.** `emit` and `screenView` do `trySend` on a `Channel(capacity = 1000, BufferOverflow.DROP_OLDEST)`. A single consumer on `Dispatchers.IO.limitedParallelism(1)` owns all state. Each command is wrapped in `runCatching`.
  - **`start()`.**
    - States: `PENDING` until `ClientConfigRepository.telemetrySettings()` answers. null → `OFF`: clear memory and the store, ignore everything after (a `@Volatile` flag checked in `emit`).
    - It restores the session from prefs and collects `consent.choices`. `GRANTED` → enqueue `consent {choice: granted}`, then activate (claim the store and enqueue `app_open`). `DENIED` → clear memory and the store, cancel the scheduler.
  - **Enqueue.** Not allowed (`consent.allowedNow()`) → drop. Otherwise:
    - `session.touch(now)`, with `session_start` first when `isNew`, and `setSession` when `persist`;
    - `screen` = the current template (`/` before the first);
    - `props = TelemetryProps.clean(props)`;
    - schedule a flush: immediate when `pending ≥ maxBatch`, else after `flushIntervalMs`; never before `backoff.blockedUntil`.
  - **Drain.** Repeat `takeBatch` → `send`:
    - `Sent`/`Dropped` → remove the batch;
    - `Retry` → `backoff.onFailure`, then `store.append(pending)`;
    - `RetryAfter` → the same, plus `block`;
    - `Disabled` → `OFF`.
  - **`onForeground()`.** `scheduler.cancel()`, `prepend(store.claim(now))`, reset the screen timer, enqueue `app_open`.
  - **`onBackground()`.** Enqueue `screen_time {route, ms}` for the current screen, then `app_background`. Then `store.append(pending)` (in-flight events are never written) and `scheduler.schedule()`.
  - **`screenView(t)`.** Same template → no-op. Otherwise `screen_time` for the previous screen, then `screen_view {route: t, referrer_route: previous?}`.
  - **`currentRoute()`.** Returns the last template (a `@Volatile` read).

- [ ] **Step 1: Write the failing tests** (`runTest` with `StandardTestDispatcher` injected in place of the IO confinement; virtual time for the 15 s timer):
  - `config off drops queued events and stops`;
  - `flushes at max_batch without waiting`;
  - `flushes after flush_interval`;
  - `session_start precedes the first event and again after 30 min idle`;
  - `screenView emits screen_time with ms and referrer`;
  - `EU zone without a choice sends and stores nothing`;
  - `grant emits consent granted then app_open`;
  - `deny clears the queue and store and sends nothing, not even the choice`;
  - `429 Retry-After blocks sends until it passes`;
  - `three failures back off`;
  - `onBackground persists pending events and schedules the worker`;
  - `in-flight events are never persisted`;
  - `onForeground cancels the worker and reclaims stored events`;
  - `emit never throws when the sender throws`;
  - `free text props never reach the sender`.
- [ ] **Step 2: Run** `$G :core:data:testLocalDebugUnitTest --tests '*TelemetryEngineTest'` → FAIL.
- [ ] **Step 3: Implement** and bind it in `TelemetryModule`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `core: telemetry engine (web client.js parity)`.

---

### Task 15: Settings: Privacy section, 7 languages, theme and language events (wave 2)

**Files:**
- Modify: `feature/settings/src/main/kotlin/.../root/{SettingsViewModel.kt, SettingsScreen.kt}`, `.../appearance/AppearanceViewModel.kt`, `feature/settings/src/main/res/values{,-es}/strings.xml`
- Modify: `core/domain/src/main/kotlin/.../globalsearch/SettingsEntry.kt` (add `PRIVACY("privacy", SettingsPage.ROOT)` before `ABOUT`), plus each `when` over `SettingsEntry` that the compiler flags (`GlobalSearch.kt`, `feature/home/.../search/SearchLabels.kt` with a `home_` string in en and es, `SettingsNavigation.kt`)
- Test: `feature/settings/src/test/.../{SettingsViewModelTest.kt, AppearanceViewModelTest.kt, PrivacySectionTest.kt}`

**Interfaces:**
- Consumes: `Telemetry`, `ConsentRepository` (T0), `AppLanguage` (T0), `TelemetryEvents` (T0), `openInCustomTab`, `PrivacyLinks` (T11, wave 2: if it has not landed yet, use the literal URL constant and leave a `TODO(T11)` for the reviewer to swap).
- Produces:
  - `SupportedLanguageTags = listOf(null, "en", "es", "de", "fr", "hi", "zh", "bn")`.
  - New strings `settings_language_de|fr|hi|zh|bn` with the native names from Global Constraints, `translatable="false"`.
  - Root Privacy section: heading `settings_privacy_heading` ("Privacy"); a switch row `settings_privacy_switch_label` ("Help improve %1$s") with `settings_privacy_switch_hint` (web `switch.hint`), tid `privacy.switch`, state description on/off; a `settings_privacy_policy` row (Custom Tab); a `settings_privacy_choices` row ("Privacy choices") calling `consentRepository.openChoices()`.
  - Events: `SettingsEvent.SetPrivacy(allowed: Boolean)` and `SettingsEvent.OpenPrivacyChoices`.
  - `SetLanguage(tag)` also emits `language_change {lang: tag ?: appLanguage.current()}`.
  - `AppearanceEvent.SetTheme(mode)` emits `theme_change {theme: mode.name.lowercase()}`.

- [ ] **Step 1: Write the failing tests**
  - `language dialog lists 8 options with Deutsch and বাংলা`;
  - `SetLanguage de emits language_change de`;
  - `SetTheme DARK emits theme_change dark`;
  - `privacy switch off chooses DENIED`;
  - `privacy choices opens the prompt` (`openCount == 1`);
  - `switch exposes its state description` (Compose).
- [ ] **Step 2: Run** `$G :feature:settings:testLocalDebugUnitTest :feature:home:testLocalDebugUnitTest :core:domain:test` → FAIL.
- [ ] **Step 3: Implement**, reusing the copy from the web `privacy.json` and `common.json`.
- [ ] **Step 4: Run** → PASS, then `$G :feature:settings:lintLocalDebug :feature:home:lintLocalDebug`.
- [ ] **Step 5: Commit** `feature(settings): Privacy section, seven languages, theme/language telemetry`.

---

### Task 16: Login social buttons and Privacy choices; share viewer Privacy choices (wave 2)

**Files:**
- Modify: `feature/auth/src/main/kotlin/.../login/{LoginScreen.kt, LoginViewModel.kt}`, `feature/auth/src/main/res/values{,-es}/strings.xml`
- Modify: the share viewer screen and ViewModel in `feature/share/src/main/kotlin/...`, `feature/share/src/main/res/values{,-es}/strings.xml`
- Test: `feature/auth/src/test/.../login/{LoginViewModelTest.kt, LoginSocialTest.kt}`, `feature/share/src/test/...` (the share ViewModel test)

**Interfaces:**
- Consumes: `SocialSignInButton`, `SocialProvider` (T10), `ConsentRepository` (T0).
- Produces:
  - `LoginEvent.SocialProviderTapped(provider: SocialProvider)`, `LoginEvent.OpenPrivacyChoices` and `LoginUiState.comingSoonProvider: String?`.
  - Strings:

| Name | English | Web key |
|---|---|---|
| `auth_continue_with` | "Continue with %1$s" | `auth:continueWith` |
| `auth_coming_soon` | "%1$s sign-in is coming soon — use your username and password for now." | `auth:comingSoon` |
| `auth_divider_or` | "or" | `auth:divider` |
| `auth_privacy_choices` | "Privacy choices" | |
| `share_privacy_choices` | "Privacy choices" | |

  - Layout (web order):
    - the four buttons Apple, Google, Facebook, GitHub;
    - the coming-soon note in a `liveRegion = Polite` container that is always composed;
    - the "or" divider;
    - the existing form;
    - a footer `TextButton` "Privacy choices" (tid `privacy.choices`).
  - Android does not call `/api/auth/providers` in Phase 0: every provider shows "coming soon".

- [ ] **Step 1: Write the failing tests**:
  - `tapping Google shows coming soon for Google`;
  - `privacy choices opens the prompt`;
  - `four provider buttons in order Apple, Google, Facebook, GitHub` (Compose);
  - `coming soon note is a polite live region`;
  - `share viewer privacy choices opens the prompt`.
- [ ] **Step 2: Run** `$G :feature:auth:testLocalDebugUnitTest :feature:share:testLocalDebugUnitTest` → FAIL.
- [ ] **Step 3: Implement**, with light and dark previews and a 200 % font-scale preview.
- [ ] **Step 4: Run** → PASS, then `$G :feature:auth:lintLocalDebug :feature:share:lintLocalDebug`.
- [ ] **Step 5: Commit** `feature(auth,share): social sign-in coming soon; Privacy choices`.

---

### Task 17: App integration (wave 3)

**Files:**
- Create: `app/src/main/kotlin/io/github/rupakc/travelplanner/telemetry/{ScreenTracker.kt, TelemetryUiBridge.kt, TelemetryProcessObserver.kt, ColdStart.kt}`
- Modify: `TravelPlannerApplication.kt`, `MainActivity.kt`, `MainViewModel.kt`, `navigation/TravelApp.kt`, `app/build.gradle.kts` (`localeFilters += listOf("en", "es", "de", "fr", "hi", "zh", "bn")`), `app/src/main/res/xml/locales_config.xml` (adds de fr hi zh bn), `app/src/main/res/xml/{data_extraction_rules.xml, backup_rules.xml}` (exclude `datastore/telemetry.preferences_pb` in cloud-backup and device-transfer)
- Test: `app/src/test/kotlin/io/github/rupakc/travelplanner/{BackupRulesTest.kt, telemetry/ScreenTrackerTest.kt, telemetry/ColdStartGateTest.kt, telemetry/TelemetryUiBridgeTest.kt}`

**Interfaces:**
- Consumes: `Telemetry`, `TelemetryLifecycle`, `ConsentRepository` (T0), `TelemetryRoutes` (T4), `LocalUiEventSink` (T10), `ConsentPrompt`, `PrivacyLinks` (T11), `TelemetryEvents`.
- Produces:
  - `fun routeTemplate(destination: NavDestination?): String`: the first `TelemetryRoutes.table` entry with `destination.hasRoute(kclass)`, else `TelemetryRoutes.OTHER`. `ScreenTracker` is a `NavController.OnDestinationChangedListener` that calls `telemetry.screenView(routeTemplate(dest))`.
  - `TelemetryUiBridge(telemetry) : UiEventSink`: TAP → `ui_tap {tid, route}`, TOGGLE → `ui_toggle {tid, route, on}`, with `route = telemetry.currentRoute()`. It is provided at the root of `TravelApp` through `LocalUiEventSink`. Nav bar and rail items report TAP `nav.<TopLevelDestination key lowercase>` through the sink.
  - `TelemetryProcessObserver`: `ProcessLifecycleOwner` `onStart` → `onForeground()`, `onStop` → `onBackground()`. It is added on the main thread in `Application.onCreate`. `telemetryLifecycle.start()` is launched on `appScope`.
  - `object ColdStartGate { fun shouldReport(startedInForeground: Boolean, firstActivity: Boolean, restored: Boolean, ms: Long): Boolean }`: true only when started in the foreground, for the first activity, not restored, and `0 ≤ ms ≤ 60_000`.
    - `startedInForeground` is captured in `Application.onCreate` via `ActivityManager.getMyMemoryState`: `importance == IMPORTANCE_FOREGROUND`.
    - `MainActivity` measures `SystemClock.uptimeMillis() - Process.getStartUptimeMillis()` in a `OneShotPreDrawListener` on the decor view, then emits `perf {metric: "cold_start", value: ms.toDouble(), route}`.
  - Consent host: `TravelApp` collects `consentRepository.prompt`. For anything other than `Hidden` it shows `ConsentPrompt` aligned to the bottom, above the navigation bar inset, over every destination including Login and Share. `onAllow`/`onDeny` → `choose`, `onClose` → `closeChoices`. `Reopened.allowed` → `currentAllowed`.
  - No new strings in `app`.

- [ ] **Step 1: Write the failing tests**
  - `ScreenTrackerTest` (Robolectric `TestNavHostController` with a graph of `HomeRoute`, `ShareRoute` and an unmapped test route):
    - `/home`;
    - `/share/:token` (the token never appears);
    - `/other`.
  - `ColdStartGateTest`:
    - `restored activity is not reported`;
    - `worker-started process is not reported`;
    - `61 s is not reported`.
  - `TelemetryUiBridgeTest`:
    - `tap maps to ui_tap with the current route`;
    - `toggle carries on`.
  - `BackupRulesTest`:
    - add `datastore/telemetry.preferences_pb` to the files expected in all three rule sections.
- [ ] **Step 2: Run** `$G :app:testLocalDebugUnitTest` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `$G spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug assembleProdRelease :app:verifyReleaseKeepRules` → BUILD SUCCESSFUL. This needs TR-1…TR-8 merged: this is the wave 3 gate.
- [ ] **Step 5: Commit** `app: wire telemetry lifecycle, screens, taps, cold start and the consent prompt; ship seven locales`.

---

### Translation tasks TR-1 … TR-8

All eight translation tasks follow this template. In each, `<M>` is one module of the task.

**Files (each `<M>`):**
- Create: `config/i18n/web-keys/<M with / → ->.json`
- Create (generated): `<M>/src/main/res/values-{de,fr,hi,zh,bn}/strings_web.xml`
- Create (hand): `<M>/src/main/res/values-{de,fr,hi,zh,bn}/<same file names as values/>`, holding only the keys not in the map

**Interfaces:**
- Consumes: `gen_strings.py` (T2), the web catalogues at the pinned SHA (extracted to `$WEBSRC` with the `git archive` line from T1 Step 4, for `frontend/src/i18n/locales`), and the module's final English strings.
- Produces: complete `de fr hi zh bn` resources for `<M>`.

- [ ] **Step 1: Suggest the map.** `python3 tools/web-sync/gen_strings.py suggest --web $WEBSRC/frontend/src/i18n/locales --module <M>`. Read every suggested pair and remove the ones whose web context differs (for example a one-word label used as a verb on one side and a noun on the other).
- [ ] **Step 2: Generate.** `python3 tools/web-sync/gen_strings.py generate --web … --module <M>`.
- [ ] **Step 3: Run the check to see what is missing.** `python3 tools/web-sync/gen_strings.py check --web … --module <M>` → FAIL listing the Android-only keys.
- [ ] **Step 4: Translate the Android-only keys** into the hand files for all five languages.
  - Keep the same names, the same `%n$s`/`%n$d` specifiers and the same file split as `values/`.
  - Plurals use the category table from Global Constraints.
  - `translatable="false"` entries are not copied.
  - Reuse the terms the module's `strings_web.xml` already uses. Brands (Voyager, Apple, Google, Facebook, GitHub, Taste Graph) are not translated.
  - Backend `detail` text is not in resources.
  - Translations are machine-drafted. The task reviewer checks meaning as the second model, per spec §9.
- [ ] **Step 5: Run the checks.** `python3 tools/web-sync/gen_strings.py check --web … --module <M>` → PASS, then `$G :<M as gradle path>:lintLocalDebug` → no `MissingTranslation`, `ExtraTranslation`, `StringFormat*` or `MissingQuantity` errors.
- [ ] **Step 6: Commit** `i18n(<M>): de fr hi zh bn (generated from the web catalogue + Android-only translations)`.

| Task | Wave | Modules | Android-only strings (approx., before Phase 0 additions) |
|---|---|---|---|
| TR-1 | 2 | `feature/results` | 164 |
| TR-2 | 2 | `feature/plan`, `feature/chat` | 121 |
| TR-3 | 2 | `feature/onboarding`, `feature/nearby` | 96 |
| TR-4 | 2 | `feature/search`, `feature/alerts` | 92 |
| TR-5 | 2 | `core/notifications`, `core/designsystem`, `app` | 26 |
| TR-6 | 3 | `core/ui` (after T11) | 100 |
| TR-7 | 3 | `feature/settings` (after T15) | 102 |
| TR-8 | 3 | `feature/home`, `feature/auth`, `feature/share` (after T15, T16) | 114 |

(1,089 of the 1,904 translatable English strings matched a web value exactly when this plan was written, so roughly 57 % are generated. The 80 `<plurals>` are matched per item.)

---

### Task D1: i18n and theming docs (wave 4)

**Files:** `docs/Localization.md`, `docs/Design-System-and-Theming.md`, `.claude/skills/add-translation/SKILL.md`, `CLAUDE.md` (the Strings line under Conventions, plus a Gotcha: "`Palette.kt`, `DarkPalette.kt` and `values-*/strings_web.xml` are generated; edit the web and bump `config/web-source.properties`"), `README.md` (languages, privacy line).

Content to cover:
- **Languages and folders.** The seven shipped locales, the `values-zh` choice (Simplified Chinese; zh-TW falls back to it, as on the web), and system Noto fonts covering Devanagari, Bengali and Han (nothing bundled).
- **Generator flow.** Pin → `suggest` → review → `generate` → hand files → `check`, plus how to bump `web.sha` (`git -C ~/travel-planner fetch`, edit the properties file, run `generate` for every module and the tokens, then commit).
- **Rules.** `strings_web.xml` is never edited by hand. Spanish stays hand-maintained. The plural category table.
- **Theming.** The light default, the dark role table from Task 9, and the token generator.

- [ ] **Step 1:** Write the pages.
- [ ] **Step 2:** `scripts/check-doc-links.sh` → OK.
- [ ] **Step 3: Commit** `docs: seven languages, web-sync generators, light default and dark tokens`.

### Task D2: Telemetry and privacy docs, decision log (wave 4)

**Files:** create `docs/Telemetry-and-Privacy.md`; modify `docs/Home.md` (link), `docs/Backend-Integration.md`, `docs/reference/api-contract.md` (`GET /api/config`, `POST /api/telemetry`, the `locale` field), `docs/Foundation-Interfaces.md` (the T0 interfaces, `UiEventSink`, `ConsentPrompt`), `docs/Modules.md` (`core:data` telemetry, `core:domain` telemetry/i18n), `MEMORY.md`.

`Telemetry-and-Privacy.md` points to the web `docs/Telemetry.md` and `docs/Privacy.md` as the contract and states what Android sends and when:
- events: `app_open` on every foreground, `app_background`, `session_start`, `screen_view`, `screen_time`, `ui_tap` (TpButton, nav, social), `ui_toggle` (TpChip), `theme_change`, `language_change`, `consent` (grant only) and `perf cold_start`;
- what it does not send yet: `ui_change`, `ui_submit`, `feature_used` and `error`. Crashes do not fit the registry's `js|promise|api` kinds; adding a kind is a backend-first follow-up;
- where the switch and Privacy choices are, the storage (prefs `telemetry`, `noBackupFilesDir/telemetry/queue.json`, unique work `telemetry_flush`), and that nothing survives a denial.

MEMORY lines to add (one each, dated 2026-11-11, under Foundation unless noted):
1. Telemetry lives in `core:data/telemetry` with pure rules in `core:domain/telemetry`; no new module.
2. `app_open` on every process foreground and `app_background` on stop. The background flush uses WorkManager unique work `telemetry_flush` (network, KEEP), and the queue file is in `noBackupFilesDir`.
3. Android gzips telemetry (the web sends plain JSON).
4. The event set Android sends in Phase 0, and why `error`, `feature_used`, `ui_change` and `ui_submit` are absent.
5. Android route templates: web names where the screen is the same, and `/other` for unmapped routes.
6. Light is the default for installs that never chose (supersedes the SYSTEM default).
7. Dark surfaces come from the web dark tokens (`onSurfaceVariant` dark gray-700, `outline` dark gray-600 for contrast on every container).
8. `Palette.kt`/`DarkPalette.kt` and `strings_web.xml` are generated from the web commit pinned in `config/web-source.properties` and checked in CI.
9. Spanish stays hand-maintained. The 5 new languages are generated where the English matches plus hand files; there is one map file per module; the literal `Voyager` maps to the brand placeholder.
10. `values-zh` is Simplified Chinese (zh-TW falls back, as on the web). Telemetry `locale` uses zh-CN, hi-IN and bn-IN.
11. `locale` is added by `AiLocaleInterceptor` from a path allowlist, so draft `searchKey`s are unchanged.
12. No `/api/auth/providers` call in Phase 0; all four providers show "coming soon".
13. Privacy choices sit on Login, the share viewer and Settings → Privacy. The consent prompt is a non-modal card in the shell.
14. The anon id and the consent choice survive sign-out and are excluded from backup and device transfer.
15. `cold_start` is reported only for a process started in the foreground, for its first activity, not restored, and 60 s or less.
16. `POST /api/analytics/events` is skipped while telemetry is not allowed.

Follow-ups go under Open items: web repo `docs/Feature-Parity.md` Android column and the Android path in `docs/Privacy.md`; the Play Data safety form (owner); a backend registry `error` kind for crashes; native-speaker review of hi, bn and zh; using `public_base_url` (Phase 1).

- [ ] **Step 1:** Write the pages and the MEMORY lines.
- [ ] **Step 2:** `scripts/check-doc-links.sh` → OK.
- [ ] **Step 3: Commit** `docs: telemetry and privacy on Android; decision log for Phase 0 parity`.

---

### Task F: Final verification and PR (wave 5)

- [ ] **Step 1: Full CI locally.** Run `$G spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug assembleProdRelease :build-logic:convention:test` and `$G :app:verifyReleaseKeepRules`, then `scripts/tests/test-release-scripts.sh`, `scripts/check-doc-links.sh` and `scripts/check-web-sync.sh ~/travel-planner` → all pass. Paste the `BUILD SUCCESSFUL` lines.
- [ ] **Step 2: Clean release APK.** `scripts/build-release-apk.sh` (a separate `clean`, then `--no-build-cache --rerun-tasks`) → prints the APK path, version and SHA-256.
- [ ] **Step 3: Backend and emulator** (`.claude/skills/verify-android/SKILL.md` §3).
  1. Start the fake SerpAPI and the local backend (`TELEMETRY_ENABLED` defaults to true), and create a test user.
  2. Start AVD `tp_pixel`.
  3. `adb install -r` the Step 2 APK (prod flavour, built with `-PPROD_BASE_URL=http://10.0.2.2:8001` if the prod build needs it; otherwise smoke the `localDebug` APK, built clean with `./gradlew clean && ./gradlew --no-build-cache assembleLocalDebug`).
- [ ] **Step 4: Smoke past sign-in.**
  1. The Login screen shows the four provider buttons; tapping GitHub shows "GitHub sign-in is coming soon…".
  2. Sign in, then run a search and see sections stream.
  3. Settings → Language → Deutsch: the UI is German and a search returns German AI text.
  4. Settings → Appearance → Dark: dark surfaces show #121a2b.
  5. Check with `adb logcat -d -s AndroidRuntime StrictMode` → no crash and no main-thread disk or network violation from telemetry.
- [ ] **Step 5: Telemetry arrives.** Background the app and wait about 1 min, then run `sqlite3 /tmp/tp-e2e/telemetry.db "select platform, event, count(*) from telemetry_events group by 1,2"` → rows for `android` with `app_open`, `screen_view`, `ui_tap`, `language_change`, `theme_change` and `app_background`. Then `select distinct screen from telemetry_events where platform='android'` → only templates, never a token.
- [ ] **Step 6: EU consent.** Run `adb shell cmd alarm set-timezone Europe/Berlin` (fallback: `adb root && adb shell setprop persist.sys.timezone Europe/Berlin`), clear the app data, then relaunch. The prompt shows and no new telemetry rows appear. "No thanks" → still no rows. Settings → Privacy shows the switch off. "Privacy choices" reopens the prompt with "No thanks" selected. Restore the time zone afterwards.
- [ ] **Step 7: Old-backend compatibility.** Start the backend with `TELEMETRY_ENABLED=false` (or point the app at a server without `/api/config`). The app works and `adb logcat` shows no telemetry retries.
- [ ] **Step 8: Clean up and open the PR.** Stop the background processes and remove `/tmp/tp-e2e*`. Push `feat/phase0-parity`, then run `gh pr create` with a body listing the waves, the decisions (D2) and the follow-ups, ending with the session's PR attribution line. Merge with `gh pr merge` once CI is green.
