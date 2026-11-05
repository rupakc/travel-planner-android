# Security

## Token storage

The JWT (24 h, HS256 from the backend) and user object live in the app-private `session` DataStore file, not encrypted. The file is excluded from
cloud backup and device transfer (`data_extraction_rules.xml`, `backup_rules.xml`). EncryptedSharedPreferences is deprecated and the token is short-lived;
revisit Keystore-wrapped storage before a store release (decision in [MEMORY](../MEMORY.md)). Logout best-effort unregisters the push token, clears the session and per-user caches.
Debug HTTP logs redact the `Authorization` header.

## Network and cleartext policy

- `prod`: `cleartextTrafficPermitted="false"`; the Server setting rejects `http`.
- `local`: cleartext allowed so the emulator (10.0.2.2) and a LAN phone can reach a dev backend. The `local` flavour must never be distributed.
- System trust anchors only; no custom certificate pinning is configured (it can be added in the prod network-security-config).
- A 401 clears the session; the app never retries with the old token.

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
