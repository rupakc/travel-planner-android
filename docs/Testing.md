# Testing

Test-first. No network in any test. Robolectric runs on **SDK 35** while compileSdk is 37 (SDK 36 images fail on JDK 21 with a
"raw FileDescriptor internals" error); the shared config is `config/robolectric/robolectric.properties`.

## Layers

| Layer | What | Where / tools |
|---|---|---|
| Unit | Pure functions (`core:domain`, `core:model`, SSE parser, reducers), ViewModels | JUnit, Turbine, `MainDispatcherRule`, fakes from `core:testing` |
| Data | Repositories against recorded responses | OkHttp MockWebServer (`TestNetwork.kt`), SSE fixtures (`Fixtures.kt`) |
| Database | Room DAOs | Robolectric in-memory database (`core:database` tests) |
| UI | Each screen's main states and key interactions, incl. semantics | Compose UI tests on Robolectric (`createComposeRule`) |
| Instrumented E2E | Real app on an emulator against a real backend (opt-in) | `app/src/androidTest`, Compose UI test + UiAutomator, `@E2eTest` |
| End to end | Manual or scripted run against the real backend | below |

## Commands

```bash
./gradlew testLocalDebugUnitTest                       # all unit + Robolectric tests
./gradlew :feature:search:testLocalDebugUnitTest       # one module
./gradlew spotlessCheck detekt lintLocalDebug          # static checks
./gradlew spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug assembleProdRelease   # CI
```

Pure-Kotlin modules alias `lintLocalDebug` / `testLocalDebugUnitTest` to `lint` / `test`, so the root tasks run everywhere.
Memory-friendly form when several builds share a machine:
`./gradlew -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.compiler.execution.strategy=in-process --max-workers=2 <tasks>`.

## Conventions

- ViewModel tests use fakes (`core:testing/fake`), `MainDispatcherRule` and Turbine; assert state transitions, not implementation.
- UI tests render `XxxScreen(state, onEvent)` directly with hand-built state; assert content, clicks, and a11y semantics.
- Streaming tests use recorded SSE streams from the contract, including a stream cut mid-way and a stream without `done`.
- Never assert on real prices or provider data; fixtures are synthetic.
- Golden files: `core/domain/src/test/resources/ics-parity/` holds three synthetic, realistic plans and the exact `.ics` the export
  produced for them (`PlanIcsParityTest`). A missing `.ics` is recorded on the next run and the test fails, so review a new golden file before committing it;
  never re-record an existing one to make a change pass.

## End to end with the local backend and a fake SerpAPI

Scripts in `tools/e2e/` (test use only, no secrets). They need the travel-planner web repo checked out with its backend dependencies installed
(`pip install -r backend/requirements.txt`), referred to below as `$WEB`.

- `fake_serpapi.py <fixtures_dir> <scale_file> <port>` replays the web repo's recorded SerpAPI responses (`$WEB/backend/tests/fixtures/serpapi`: hotels and activities;
  there is no flights fixture, so Google Flights calls get HTTP 500 and the backend falls back). Every price is multiplied by the number in `<scale_file>`
  (write `0.8` to simulate a 20 % drop between watch checks; a missing file means 1.0).
- `run_backend.py <fake_url> <port>` must run with cwd `$WEB/backend`. The backend has no setting for the SerpAPI URL, so it patches
  `serp_flights.SERP_URL`, `serp_flights._SERP_URL`, `hotel_prices.SERP_URL` and `activity_prices.SERP_URL` before importing `app.main`, then serves on `127.0.0.1:<port>`.

```bash
WEB=~/travel-planner; ANDROID=$PWD
echo 1.0 > /tmp/tp-e2e-scale
python3 -I tools/e2e/fake_serpapi.py $WEB/backend/tests/fixtures/serpapi /tmp/tp-e2e-scale 9100 &   # fake SerpAPI
cd $WEB/backend
DATA_DIR=/tmp/tp-e2e ADMIN_PASSWORD='e2e-Test-Pass1' SERPAPI_KEY=fake RATE_LIMIT_ENABLED=false CACHE_TTL_SECONDS=5 \
  python3 $ANDROID/tools/e2e/run_backend.py http://127.0.0.1:9100/search 8001 &                    # backend on 8001
curl -s localhost:8001/health      # expect serpapi_configured: true
```

- `CACHE_TTL_SECONDS=5`: the backend caches SerpAPI answers (30 min by default), so a second "Check now" would
  otherwise never see the fake's lowered prices.
- `DATA_DIR` isolates the SQLite file from real data; `ADMIN_PASSWORD` is a throwaway test value that creates the `admin` user (username `admin`); create a normal user through the admin endpoints
  (`POST /api/admin/users`, see the web repo's API-Reference) and sign in with it.
- The emulator reaches the host's loopback backend at `http://10.0.2.2:8001` (the `local` flavour default). A physical phone cannot: `run_backend.py` binds 127.0.0.1, so use the emulator for E2E.
- Install and walk through: `./gradlew installLocalDebug`, launch `io.github.rupakc.travelplanner.local.debug`, then login -> onboarding -> search stream -> add to plan -> save -> confirm the plan appears on the web.
- Price-drop test: create a hotel or activity price watch, `echo 0.8 > /tmp/tp-e2e-scale`, trigger "Check now"; expect an alert notification.
- Stop with `kill %1 %2` (or the PIDs); delete `/tmp/tp-e2e`. Never use the real SerpAPI key in E2E (the free plan's 250 searches a month is exhausted).
- The `verify-android` skill has the scripted commands.

Tests run with the backend's `RATE_LIMIT_ENABLED=false` when scripting many requests.

## Instrumented end to end (`app/src/androidTest`)

The E2E suite drives the real app on an emulator against the backend above. It is opt-in: every class is annotated
`@E2eTest`, and the `travelplanner.android.e2e` convention plugin filters them out unless `-Pe2e=true` is set, so a
plain `connectedLocalDebugAndroidTest` (and CI, which runs no device tests) never needs a backend.

```bash
# backend on 8031 + fake SerpAPI on 9100 as above, emulator booted (one emulator only)
./gradlew :app:connectedLocalDebugAndroidTest -Pe2e=true -Pe2eAdminPassword='<the ADMIN_PASSWORD you started it with>'
# one class
./gradlew :app:connectedLocalDebugAndroidTest -Pe2e=true -Pe2eAdminPassword=... \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.rupakc.travelplanner.e2e.OnboardingE2eTest
```

| Property | Default | Meaning |
|---|---|---|
| `e2e` | `false` | Run the `@E2eTest` classes (otherwise they are skipped) |
| `e2eBaseUrl` | `http://10.0.2.2:8031` | Backend as the emulator sees it; the tests type it into the Login screen's Server sheet |
| `e2eAdminPassword` | `e2e-Test-Pass1` | The backend's `ADMIN_PASSWORD` (a throwaway test value) |
| `e2eFakeSerpUrl` | `http://10.0.2.2:9100` | The fake SerpAPI as the emulator sees it (price-drop flow) |
| `e2eScreenshots` | `false` | `true` saves PNGs to `app/build/outputs/connected_android_test_additional_output/` |

- Android Test Orchestrator runs each test in its own process with `clearPackageData`, so every test starts signed out
  and tests share state only through the backend. Animations are disabled for the run.
- `BackendApi` (OkHttp, on the device) arranges state and checks what the app stored (Preferences, plans, watches);
  the second account (`e2e-traveller`) is created through the admin API when missing.
- Waits poll the semantics tree (`AppRobot.waitFor…`): network work is not an Espresso idling resource. System UI
  (permission dialogs, the notification shade) is driven with UiAutomator.
- The price-drop flow lowers prices through the fake SerpAPI's test-control endpoint
  `GET http://10.0.2.2:9100/__scale?value=0.8` (`-Pe2eFakeSerpUrl` overrides the base), which rewrites its scale file,
  and sets it back to 1.0 afterwards.
