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

1. Backend: in `~/travel-planner` run `./start.sh` (port 8001); confirm `curl -s localhost:8001/health`. Use an isolated `DATA_DIR` and the fake SerpAPI; do not spend the real SerpAPI quota.
2. Emulator: `$ANDROID_HOME/emulator/emulator -list-avds`, start one, `adb wait-for-device`.
3. `$G installLocalDebug && adb shell am start -n io.github.rupakc.travelplanner.local/io.github.rupakc.travelplanner.MainActivity` (check the exact id with `adb shell pm list packages | grep travelplanner`).
4. Sign in with a test user, run a search, watch sections stream, add an item to My Plan, save, open `adb shell am start -a android.intent.action.VIEW -d "travelplanner://nearby"` to check deep links.
5. `adb logcat -s AndroidRuntime StrictMode` for crashes; `adb exec-out screencap -p > shot.png` for screenshots (light and dark: `adb shell cmd uimode night yes|no`).
