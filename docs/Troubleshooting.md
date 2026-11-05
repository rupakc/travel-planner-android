# Troubleshooting

| Symptom | Cause and fix |
|---|---|
| App cannot reach the backend on the emulator | Start the backend on port 8001 (`./start.sh` in the web repo); the emulator uses `http://10.0.2.2:8001`. Check `curl localhost:8001/health` on the host. |
| Phone on LAN cannot connect | Settings -> Server -> `http://<LAN-IP>:8001`; same Wi-Fi; the backend must listen on `0.0.0.0` and the firewall allow 8001. The `local` flavour only (prod forbids cleartext). |
| "No server configured" in `prod` | `PROD_BASE_URL` was not passed at build time and no Server override is set. |
| Cleartext not permitted error | You are on a `prod` build with an `http` URL; use HTTPS or the `local` flavour. |
| Signed out unexpectedly | Any 401 clears the session (JWT expires after 24 h). Sign in again. |
| Search shows "429" message | Per-user limit (search: 10 per 10 minutes by default). The backend's message and `Retry-After` are shown; wait. |
| Search stalls, then shows fewer sections | The stream was cut; the app retries once and keeps received sections. Check backend logs. |
| No flight prices / "no live price" | `SERPAPI_KEY` unset or the free quota (250 searches a month) is used up; prices are then estimates and labelled so. |
| Push never arrives | Expected unless `app/google-services.json` was present at build and the backend has `FCM_SERVICE_ACCOUNT_FILE` (`/health` `push_configured: true`). Polling still delivers alerts. See [Firebase-Setup](Firebase-Setup.md). |
| Notifications do not show | Android 13+ needs the `POST_NOTIFICATIONS` grant; check the channel is not blocked in system settings. |
| Robolectric "raw FileDescriptor internals" | A test requested SDK 36 on JDK 21. Tests must run on SDK 35 (`config/robolectric/robolectric.properties`). |
| `Failed to find platform android-37` | Install SDK platform 37 (`sdkmanager "platforms;android-37.0"`). compileSdk is 37 on purpose (AndroidX AARs require it). |
| Build runs out of memory with several builds open | Use the capped form in [Testing](Testing.md). |
| spotlessCheck fails | `./gradlew spotlessApply`. |
| Fonts look like system sans | Google Fonts provider unavailable (offline emulator without Play Services); the fallback is intentional. |
| Doc link check fails | `scripts/check-doc-links.sh` prints the broken `file:line`. |
