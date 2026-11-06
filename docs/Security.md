# Security

## Token storage

The JWT (24 h, HS256 from the backend) and user object are sealed with Tink AES-256-GCM before they reach the app-private `session` DataStore file.
The Tink keyset lives in `shared_prefs/tp_keys.xml`, wrapped by the Android Keystore key `tp_session_master`; when the Keystore is unusable the app
keeps the session in memory only for the process and deletes any keyset Tink stored without the Keystore (details: [Foundation-Interfaces §4c](Foundation-Interfaces.md)).
The JWT `exp` is checked on the device, so an expired token reads as signed out. Session, keyset and the Room database are excluded from cloud backup and
device transfer (`data_extraction_rules.xml`, `backup_rules.xml`). Logout best-effort unregisters the push token, then clears the session and per-user caches;
a 401 or an expired token only signs out and keeps the user's local data.
Debug HTTP logs redact the `Authorization` header. Saved plans drop this search's link evidence (`link_verified`, `link_checked`) on save, like the web.

## Network and cleartext policy

- `prod`: `cleartextTrafficPermitted="false"`; the Server setting rejects `http`.
- `local`: cleartext allowed so the emulator (10.0.2.2) and a LAN phone can reach a dev backend. The `local` flavour must never be distributed.
- System trust anchors only; no custom certificate pinning is configured (it can be added in the prod network-security-config).
- A 401 clears the session (local data is kept); the app never retries with the old token. The token is attached only to requests for the server that issued it.

## Deep links

Scheme `travelplanner://` with hosts `share`, `alerts`, `plan`, `chat`, `nearby` (declared in the manifest and `DeepLinks`). Treat all link parameters as untrusted:
tokens and ids are validated by the backend, `kind` must be a known kind, numeric ids are parsed safely. `MainActivity` is `singleTask` and exported, so
`DeepLinkIntents.sanitize` runs on every incoming intent (before `super.onCreate` and in `onNewIntent`): it removes Navigation's internal deep-link extras
(`deepLinkIds`/`Args`/`Extras`/`Intent`, which would otherwise let another app open any destination with arguments of its choosing) and keeps data only on a
`travelplanner://` VIEW intent (no https/App Links: web share links do not open the app). Links arriving while running are handled with `handleDeepLink`.
Auth gating still applies (only Share is reachable signed out); a link the gate redirects away from is kept by `ShellViewModel` and opened once signed in
(`DeepLinkHold`); a link that was already shown (the public share viewer, a link opened while signed in) is dropped and never replayed.
A chat thread opens by id only if it is one of the signed-in user's threads. Notification PendingIntents are `FLAG_IMMUTABLE` and restricted to this package.

## Outbound links

Booking and provider links open in a Custom Tab through `BookingLinkButton`, which allows only `http` and `https` URLs. Never launch a URL scheme taken from API data.

## CI

`.github/workflows/android.yml` runs with a read-only `GITHUB_TOKEN` (`permissions: contents: read`); third-party actions
(`android-actions/setup-android` v3.2.2, `gradle/actions/setup-gradle` v4.4.3) are pinned by commit SHA and `.github/dependabot.yml` proposes action updates weekly. `wiki-sync.yml` is read-only at workflow level and
grants `contents: write` only to the job that pushes the wiki.

## Secrets

No secrets in the repo: `local.properties`, keystores, `keystore.properties` and `app/google-services.json` are git-ignored. CI has no signing key or Firebase file.
The FCM token only travels in request bodies. Pass `PROD_BASE_URL` via Gradle properties.

## Data minimisation

Location is coarse by default, foreground only and used for Near me, and only when the user opens Near me (the background invitation makes no
request). The coordinates are rounded to 3 decimals (~100 m) on the phone and sent to the user's own backend, which passes them to Google Maps
(Gemini Grounding with Google Maps) to find nearby places; neither the app nor the backend stores them or the results (the device keeps only a
~5 km cell, for 24 h, to gate the invitation). Grounded links (Maps `maps_uri`, Search sources) open only if they are absolute http(s) URLs
(`core:domain` `safeWebUri`); Near me results and live web answers are never persisted.
