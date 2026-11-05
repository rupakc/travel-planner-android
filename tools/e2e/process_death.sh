#!/usr/bin/env bash
# Process-death check for the instrumented E2E suite (flow 11): killing the app process also kills the
# instrumentation, so the kill and the relaunch happen here, from the host.
#
#   tools/e2e/process_death.sh <admin password>      # backend + emulator as in docs/Testing.md
#   (GRADLE_FLAGS="-Dorg.gradle.jvmargs=-Xmx2g ..." passes extra Gradle flags)
#
# For each case it runs a "prepare" test that leaves the app in the background (APKs kept installed), kills the
# process with `am kill` (Orchestrator's clearPackageData is turned off so the data survives the test), relaunches it
# with the launcher intent and checks the restored screen with `uiautomator dump`.
set -euo pipefail
PW="${1:?admin password}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG=io.github.rupakc.travelplanner.local.debug
CLS=io.github.rupakc.travelplanner.e2e.StateRestorationE2eTest
cd "$(dirname "$0")/../.."
fail=0

check() { # $1 test method, $2.. texts expected after the restore
  local method=$1; shift
  ./gradlew ${GRADLE_FLAGS:-} -q :app:connectedLocalDebugAndroidTest -Pe2e=true -Pe2eAdminPassword="$PW" \
    -Pandroid.testInstrumentationRunnerArguments.class="$CLS#$method" \
    -Pandroid.testInstrumentationRunnerArguments.clearPackageData=false \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true >/dev/null
  "$ADB" shell am kill "$PKG"
  sleep 2
  if "$ADB" shell pidof "$PKG" >/dev/null; then echo "FAIL $method: process still alive"; fail=1; return; fi
  "$ADB" shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    -n "$PKG/io.github.rupakc.travelplanner.MainActivity" >/dev/null
  sleep 6
  "$ADB" shell uiautomator dump /sdcard/e2e-ui.xml >/dev/null
  local ui; ui=$("$ADB" shell cat /sdcard/e2e-ui.xml)
  for text in "$@"; do
    if grep -q -- "$text" <<<"$ui"; then echo "ok   $method: '$text' restored"; else echo "FAIL $method: '$text' missing"; fail=1; fi
  done
}

check processDeath_prepareSearchForm "JFK — New York" "LIS — Lisbon"
check processDeath_prepareResults "Travel Ease Score" "JFK"
exit $fail
