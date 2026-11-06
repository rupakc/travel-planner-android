"""Process-death check (E2E flow 11), driven from the host.

Killing the app process also kills an instrumentation test (same process), so this script drives
the installed app with adb only: it signs in through one instrumented test that keeps its data
(Orchestrator's clearPackageData off), then for each case it navigates with taps, sends the app
to the background, kills the process with `am kill`, brings the task back with the launcher
intent and checks the restored screen in a `uiautomator dump`.

    GRADLE_FLAGS="..." python3 -I tools/e2e/process_death.py <admin password>

Needs the E2E backend + emulator from docs/Testing.md. Test use only.
"""

import os
import re
import subprocess
import sys
import time

PW = sys.argv[1]
SDK = os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk"))
ADB = os.path.join(SDK, "platform-tools", "adb")
PKG = "io.github.rupakc.travelplanner.local.debug"
ACTIVITY = f"{PKG}/io.github.rupakc.travelplanner.MainActivity"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
GRADLE_FLAGS = os.environ.get("GRADLE_FLAGS", "").split()


def adb(*args, check=True):
    return subprocess.run([ADB, *args], capture_output=True, text=True, check=check).stdout


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/e2e-ui.xml")
    return adb("shell", "cat", "/sdcard/e2e-ui.xml")


def nodes(ui):
    for m in re.finditer(r"<node [^>]*>", ui):
        n = m.group(0)
        text = re.search(r' text="([^"]*)"', n).group(1)
        desc = re.search(r'content-desc="([^"]*)"', n).group(1)
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", re.search(r'bounds="([^"]*)"', n).group(1)))
        yield text, desc, ((x1 + x2) // 2, (y1 + y2) // 2)


def tap(label, timeout=30):
    end = time.time() + timeout
    while time.time() < end:
        for text, desc, (x, y) in nodes(dump()):
            if label in (text, desc):
                adb("shell", "input", "tap", str(x), str(y))
                time.sleep(1.5)
                return
        time.sleep(1)
    raise SystemExit(f"FAIL: '{label}' not found")


def visible(label):
    return any(label in t or label in d for t, d, _ in nodes(dump()))


def wait_for(label, timeout=60):
    end = time.time() + timeout
    while time.time() < end:
        if visible(label):
            return True
        time.sleep(2)
    return False


def scroll_to_top():
    for _ in range(3):
        adb("shell", "input", "swipe", "540", "700", "540", "1900", "300")
        time.sleep(1)


def kill_and_restore():
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(2)
    adb("shell", "am", "kill", PKG)
    time.sleep(2)
    if adb("shell", "pidof", PKG, check=False).strip():
        raise SystemExit("FAIL: process still alive after am kill")
    adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", ACTIVITY)
    time.sleep(6)


def main():
    adb("shell", "pm", "clear", PKG, check=False)
    # Sign in once (server, login, onboarding) and keep the data.
    subprocess.run(
        ["./gradlew", *GRADLE_FLAGS, "-q", ":app:connectedLocalDebugAndroidTest", "-Pe2e=true", f"-Pe2eAdminPassword={PW}",
         "-Pandroid.testInstrumentationRunnerArguments.class=io.github.rupakc.travelplanner.e2e.OnboardingE2eTest",
         "-Pandroid.testInstrumentationRunnerArguments.clearPackageData=false",
         "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"],
        cwd=ROOT, check=True,
    )
    failures = 0
    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(5)

    # 1. Search form: the tab and a typed origin survive process death.
    tap("Search")
    tap("From")
    time.sleep(1.5)  # let the keyboard and the scroll-into-view settle
    # One key at a time: `input text` bursts faster than a typeahead field takes them (characters get dropped).
    for ch in "Lisbo":
        adb("shell", "input", "text", ch)
        time.sleep(0.4)
    time.sleep(2)
    if not visible("Lisbo"):
        raise SystemExit("FAIL: could not type the origin before the process-death check")
    kill_and_restore()
    # The form's scroll position is restored too: with a "Recent searches" block above it the From field can sit
    # just above the viewport, and uiautomator only lists what is on screen. Scroll to the top before looking.
    ok = wait_for("Lisbo", 10) or (scroll_to_top() or wait_for("Lisbo", 10))
    print(("ok  " if ok else "FAIL") + " search form keeps the typed origin after process death")
    failures += not ok

    # 2. Results of a saved trip: My trips -> trip -> Open in results, then process death.
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    tap("My trips")
    time.sleep(3)
    texts = [t for t, _, _ in nodes(dump())]
    # A trip saved with an itinerary snapshot by the E2E flows (Results then shows the Itinerary section).
    trip = next((t for t in texts if t.startswith("E2E Lisbon itinerary")), None)
    if trip is None:
        print("skip results case: no saved trip on the server")
    else:
        tap(trip)
        tap("Open in results")
        if not wait_for("Itinerary", 30):
            raise SystemExit(f"FAIL: '{trip}' opened in results without an Itinerary section")
        kill_and_restore()
        ok = wait_for("Itinerary", 20)
        print(("ok  " if ok else "FAIL") + f" results of '{trip}' come back after process death")
        failures += not ok
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
