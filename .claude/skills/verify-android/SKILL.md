---
name: verify-android
description: Run the full verification for the Android app (format, detekt, lint, unit tests, assemble) and optionally smoke-run the app on an emulator against the local backend. Use before every commit or PR.
---

# Verify the Android app

## 1. Environment

```bash
export JAVA_HOME=$HOME/Library/Java/temurin-21/Contents/Home ANDROID_HOME=$HOME/Library/Android/sdk   # adjust
test -f local.properties || echo "sdk.dir=$ANDROID_HOME" > local.properties
G="./gradlew -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.compiler.execution.strategy=in-process --max-workers=2"
```

Never run `./gradlew --stop` or kill other Java processes.

## 2. Static + tests + build (what CI runs)

```bash
$G spotlessApply
$G spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug
$G assembleProdRelease          # before PRs touching R8, manifest or build files
scripts/check-doc-links.sh      # when docs changed
```

Iterate on one module first: `$G :feature:search:testLocalDebugUnitTest`. Paste the final `BUILD SUCCESSFUL` line and the task list in your report.
Failures: reports are under `*/build/reports/` (tests, lint, detekt). Robolectric must stay on SDK 35.

## 3. Emulator smoke run against the local backend (optional, for UI changes)

Full explanation: `docs/Testing.md` ("End to end"). Scripts: `tools/e2e/fake_serpapi.py`, `tools/e2e/run_backend.py`. `$WEB` = the travel-planner web repo.

1. Fake SerpAPI and backend (never the real key):
   ```bash
   WEB=~/travel-planner; ANDROID=$PWD; echo 1.0 > /tmp/tp-e2e-scale
   python3 -I tools/e2e/fake_serpapi.py $WEB/backend/tests/fixtures/serpapi /tmp/tp-e2e-scale 9100 &
   (cd $WEB/backend && DATA_DIR=/tmp/tp-e2e ADMIN_PASSWORD='e2e-Test-Pass1' SERPAPI_KEY=fake RATE_LIMIT_ENABLED=false \
      python3 $ANDROID/tools/e2e/run_backend.py http://127.0.0.1:9100/search 8001) &
   curl -s localhost:8001/health
   ```
2. Create a test user with the admin account (`admin` / the ADMIN_PASSWORD above) via `POST /api/admin/users`.
3. Emulator: `$ANDROID_HOME/emulator/emulator -list-avds`, start one, `adb wait-for-device`; it reaches the backend at `http://10.0.2.2:8001` (the `local` default).
4. `$G installLocalDebug && adb shell am start -n io.github.rupakc.travelplanner.local.debug/io.github.rupakc.travelplanner.MainActivity`
   (application id = `io.github.rupakc.travelplanner` + `.local` flavour suffix + `.debug` build-type suffix).
5. Sign in, run a search, watch sections stream, add an item to My Plan, save. Deep link check: `adb shell am start -a android.intent.action.VIEW -d "travelplanner://nearby"`.
6. `adb logcat -d -s AndroidRuntime StrictMode` for crashes; `adb exec-out screencap -p > shot.png`; dark mode `adb shell cmd uimode night yes|no`.
7. Clean up: stop the two background processes and remove `/tmp/tp-e2e`, `/tmp/tp-e2e-scale`.

Note: `$G ...` commands in this skill do not match the settings.json allow-list patterns, so they prompt; that is intended for builds.
