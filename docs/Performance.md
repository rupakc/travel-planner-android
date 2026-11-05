# Performance

Targets (spec): cold start under 1 s on a mid-range device in release, 60 fps scrolling, first streamed section painted as it arrives.

## In place

- **R8 full mode** (`android.enableR8.fullMode=true`) and resource shrinking in release; `app/proguard-rules.pro` keeps the serializable route classes; libraries ship consumer rules. CI builds `assembleProdRelease` to catch shrinker problems.
- **Compose stability**: `config/compose/stability.conf` marks `core.model.**`, `java.time.*`, kotlinx JSON elements and read-only collections stable. Keep UI state immutable.
- **Lists**: `LazyColumn`/`LazyRow` with stable `key`s and `contentType`; no work in composition (`remember`, `derivedStateOf`).
- **Threading**: SSE parsing and decoding off the main thread; the base URL and token lookups hit DataStore's in-memory value.
- **Debug StrictMode** (thread and VM policies, log only) in `TravelPlannerApplication`.
- Fonts: Google downloadable fonts with a system fallback so first paint never waits.

## Planned

- **Baseline profile and startup profile**: a `baselineprofile` module (macrobenchmark generator) is not yet in the repo. When added, generate with
  `./gradlew :baselineprofile:generateBaselineProfile` on an emulator and commit `app/src/main/baseline-prof.txt`.

## Measuring startup

```bash
./gradlew installProdRelease -PPROD_BASE_URL=https://example.invalid   # release-like build (debug-signed in CI only)
adb shell am force-stop io.github.rupakc.travelplanner
adb shell am start -W -n io.github.rupakc.travelplanner/.MainActivity   # TotalTime = cold start
```

Also check `adb logcat -s StrictMode` on debug builds, the Layout Inspector's recomposition counts, and Compose compiler reports
(`-P...compose.reports`) when a screen recomposes too often. Compare before and after on the same device.
