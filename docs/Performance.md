# Performance

Targets (spec): cold start under 1 s on a mid-range device in release, 60 fps scrolling, first streamed section painted as it arrives.

## In place

- **R8 full mode** (`android.enableR8.fullMode=true`) and resource shrinking in release; `app/proguard-rules.pro` keeps the serializable route classes,
  the `core:model` serializers and the Retrofit API interfaces except `AdminApi` (no admin UI, so R8 removes it). CI builds `assembleProdRelease` and runs
  `:app:verifyReleaseKeepRules`, which fails if R8 strips a model serializer or keeps `AdminApi`.
- **Build**: `ksp.incremental=false` (a stale incremental KSP output once broke a release APK; the measured cost on debug
  builds was within noise). Release variants are gated by `verifyHiltViewModels<Variant>` ([Release](Release.md)).
- **Startup**: `TravelPlannerApplication.onCreate` creates the notification channels and starts `NotificationsInitializer` lazily on the
  application scope (Dispatchers.Default), so the session DataStore, Retrofit, Room and push graph are not built on the main thread; Retrofit uses
  a lazy call factory, so OkHttp initialises on the first request.
- **Compose stability**: `config/compose/stability.conf` marks `core.model.**`, `java.time.*`, kotlinx JSON elements and read-only collections stable. Keep UI state immutable.
- **Lists**: `LazyColumn`/`LazyRow` with stable `key`s and `contentType`; no work in composition (`remember`, `derivedStateOf`).
- **Threading**: SSE parsing and decoding off the main thread. The auth interceptor reads the session on OkHttp's thread
  (`runBlocking { sessionStore.current() }`: DataStore's cached value, then a Tink decrypt and JSON decode of the sealed session), which is cheap but not free.
- **Debug StrictMode** (thread and VM policies, log only) in `TravelPlannerApplication`.
- Fonts: Google downloadable fonts with a system fallback so first paint never waits.

## Planned

- **Baseline profile and startup profile** (the biggest remaining cold-start and jank lever before a store release): a `baselineprofile` module
  (macrobenchmark generator) is not yet in the repo. When added, generate with
  `./gradlew :baselineprofile:generateBaselineProfile` on an emulator and commit `app/src/main/baseline-prof.txt`.

## Measuring startup

```bash
./gradlew installProdRelease -PPROD_BASE_URL=https://example.invalid   # release-like build (debug-signed in CI only)
adb shell am force-stop io.github.rupakc.travelplanner
adb shell am start -W -n io.github.rupakc.travelplanner/.MainActivity   # TotalTime = cold start (prodRelease; localDebug is io.github.rupakc.travelplanner.local.debug)
```

Also check `adb logcat -s StrictMode` on debug builds, the Layout Inspector's recomposition counts, and Compose compiler reports
(`-P...compose.reports`) when a screen recomposes too often. Compare before and after on the same device.
