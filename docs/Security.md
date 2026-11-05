# Security

## Token storage

The JWT (24 h, HS256 from the backend) and user object are sealed with Tink AES-256-GCM before they reach the app-private `session` DataStore file.
The Tink keyset lives in `shared_prefs/tp_keys.xml`, wrapped by the Android Keystore key `tp_session_master`; when the Keystore is unusable the app
keeps the session in memory only for the process and deletes any keyset Tink stored without the Keystore (details: [Foundation-Interfaces §4c](Foundation-Interfaces.md)).
The JWT `exp` is checked on the device, so an expired token reads as signed out. Session, keyset and the Room database are excluded from cloud backup and
device transfer (`data_extraction_rules.xml`, `backup_rules.xml`). Logout best-effort unregisters the push token, then clears the session and per-user caches;
a 401 or an expired token only signs out and keeps the user's local data.
Debug HTTP logs redact the `Authorization` header.

## Network and cleartext policy

- `prod`: `cleartextTrafficPermitted="false"`; the Server setting rejects `http`.
- `local`: cleartext allowed so the emulator (10.0.2.2) and a LAN phone can reach a dev backend. The `local` flavour must never be distributed.
- System trust anchors only; no custom certificate pinning is configured (it can be added in the prod network-security-config).
- A 401 clears the session (local data is kept); the app never retries with the old token. The token is attached only to requests for the server that issued it.

## Deep links

Scheme `travelplanner://` with hosts `share`, `alerts`, `plan`, `chat`, `nearby` (declared in the manifest and `DeepLinks`). Treat all link parameters as untrusted:
tokens and ids are validated by the backend, `kind` must be a known kind, numeric ids are parsed safely. `MainActivity` is `singleTask`; links arriving while running are handled with `handleDeepLink`.
Auth gating still applies (only Share is reachable signed out). Notification PendingIntents are `FLAG_IMMUTABLE` and restricted to this package.

## Outbound links

Booking and provider links open in a Custom Tab through `BookingLinkButton`, which allows only `http` and `https` URLs. Never launch a URL scheme taken from API data.

## Secrets

No secrets in the repo: `local.properties`, keystores, `keystore.properties` and `app/google-services.json` are git-ignored. CI has no signing key or Firebase file.
The FCM token only travels in request bodies. Pass `PROD_BASE_URL` via Gradle properties.

## Data minimisation

Location is coarse by default, foreground only and used for Near me; it is sent only to the user's own backend.
