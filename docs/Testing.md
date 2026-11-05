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
| Instrumented | A few smoke tests on an emulator | `androidTest` (connected) |
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

## End to end with the local backend

1. In the web repo, start the backend with an isolated data dir so you do not touch real data (`DATA_DIR=/tmp/tp-e2e`) and create a test user via the admin API.
2. For flight data without spending SerpAPI quota (the free plan is 250 searches a month), run a fake SerpAPI server and point the backend's SerpAPI base at it (project convention: never burn the real quota in E2E); leave `SERPAPI_KEY` unset to exercise the "no live price" paths.
3. Run the emulator (`local` flavour reaches `http://10.0.2.2:8001`), install `./gradlew installLocalDebug`, sign in and walk: login -> onboarding -> search stream -> add to plan -> save -> confirm the plan appears on the web.
4. Use the `verify-android` skill for the scripted commands.

Tests run with the backend's `RATE_LIMIT_ENABLED=false` when scripting many requests.
