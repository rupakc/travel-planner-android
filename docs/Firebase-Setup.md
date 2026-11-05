# Firebase setup (push notifications)

Push is optional. Without Firebase the app still gets price alerts by polling
`/api/notifications` every 30 minutes (`AlertPollWorker`), and trip reminders are
scheduled on the device. With Firebase on both sides, the server pushes each alert as it
happens and the poll drops to a 6-hour safety net.

Two pieces must come from the **same Firebase project**:

| Side | File | Where |
|---|---|---|
| App | `google-services.json` | `app/google-services.json` (never commit it) |
| Backend | service-account key (JSON) | any path; `FCM_SERVICE_ACCOUNT_FILE` in `backend/.env` |

## 1. Create the Firebase project

1. Open <https://console.firebase.google.com> and choose **Add project**. Analytics is not needed.
2. In **Project settings → General**, note the **Project ID**. The backend reads it from the service-account key, so the two must match.

## 2. Add the Android app(s)

The application id depends on flavour and build type (see `app/build.gradle.kts`):

| Variant | Application id |
|---|---|
| `prodRelease` | `io.github.rupakc.travelplanner` |
| `localDebug` | `io.github.rupakc.travelplanner.local.debug` |
| `localRelease` | `io.github.rupakc.travelplanner.local` |
| `prodDebug` | `io.github.rupakc.travelplanner.debug` |

1. In **Project settings → Your apps**, choose **Add app → Android** once for every id you build (one `google-services.json` can hold several apps).
2. You can leave the SHA-1 empty: FCM doesn't need it.
3. Download `google-services.json` and put it at `app/google-services.json`.

The build applies the Google Services plugin and sets the resource `fcm_enabled=true` **only when that file exists**, which turns on `TravelMessagingService` in the manifest. Without the file the app builds and runs normally and push stays inert.

## 3. Backend: service account

1. In **Project settings → Service accounts**, choose **Generate new private key**. A JSON key downloads; keep it secret.
2. Add `FCM_SERVICE_ACCOUNT_FILE=/absolute/path/to/firebase-service-account.json` to `backend/.env` in the travel-planner repo, then restart the backend.
3. Check that `GET /health` now returns `"push_configured": true`.

## 4. What the app does

- After every sign-in (and at app start while signed in) the app sends its FCM token with `POST /api/devices` (`PushRegistrar`). Tokens go only in request bodies, never in URLs.
- On sign-out it calls `POST /api/devices/unregister` before dropping the session (`AuthRepository.logout`).
- `onNewToken` re-registers while signed in.
- With the app in the background, Android shows the push itself, and tapping it opens the app. In the foreground, `TravelMessagingService` posts it on the **Price alerts** channel if that toggle is on, with a deep link to `travelplanner://alerts?kind=…&watch=…`. It also marks the id as seen, so the poller never posts it twice.
- When both `/health.push_configured` and the app's Firebase are on, `AlertPollWorker` runs every 6 h instead of every 30 min.

## 5. Verify end to end

1. Build `localDebug` with `google-services.json` present, install it, and sign in.
2. `GET /api/devices` (with your token) lists one device with a masked token (`…abc123`).
3. Create a watch with a target above today's price, then **Check now** in Alerts. The server stores an alert, and a push arrives within seconds.
4. Sign out: the device disappears from `GET /api/devices`.

## Troubleshooting

- **No push, but alerts appear in the app after a while:** the poller is working, so push isn't. Check `push_configured` and that both files come from the same project.
- **Backend logs `UNREGISTERED`:** the server prunes that token. The app registers a fresh one on its next start or sign-in.
- **A bare 404 from FCM:** usually the project ids don't match. The token is kept; fix the key.
