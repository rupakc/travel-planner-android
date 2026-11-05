# Data and offline

The backend is the source of truth for preferences, plans, watches and notifications. Local storage is a cache plus device-only settings.

## Room (`core:database`, `TravelDatabase` version 1)

| Table | Purpose |
|---|---|
| `cached_plans` | Saved plans as the API's JSON, per username; offline read, newest first |
| `chat_threads`, `chat_messages` | Chat history per user (the server is stateless for chat; the web keeps it in localStorage) |
| `recent_searches` | Last 8 searches per user |
| `seen_notifications` | Notification ids already posted on this device (dedupes the poller and FCM) |
| `plan_draft` | Single row: the current My Plan draft, so it survives process death |

Schemas are exported to `core/database/schemas/`. Changing an entity requires a version bump and a migration test; never edit an
exported schema by hand.

## DataStore (`core:datastore`)

Five Preferences DataStore files: `session` (JWT and user, sealed with Tink), `settings` (server override, theme mode, dynamic colour,
locale tag, notification toggles), `traveller_profile`, `feature_overrides` and `last_location` (the coarse cell of the last Near me
lookup, for background suggestions). Traveller profile and feature overrides are device-local because the server Preferences schema has no
field for them. The session and last-location files, the Room database, the Tink keyset and the notification bookkeeping
SharedPreferences (`alert_poll`, `trip_reminders`, `nearby_notifications`, `push_registration`, and `server_capabilities`, the cached
`/health` capabilities) are excluded from cloud backup and device transfer
(`data_extraction_rules.xml`, `backup_rules.xml`). Token encryption: [Security](Security.md).

## Caching rules

- Plans are offline-first: `PlansRepository.plans` / `plan(id)` read Room; network calls refresh it.
- Result items and plan selections keep their original JSON (`RawBacked`, `extras` for unknown keys), so a plan saved on the web and re-saved on the phone loses nothing.
- Sign-out, a different user signing in and a server change run `SessionCleaner`: the current search, the Near me cache and last location,
  the My Plan draft, cached plans, recent searches, seen notifications and every contributed `UserStateCleaner` (e.g. the alert-poll baseline).
  Chat history is kept: it is stored per username and only that user can list or open it. A 401 or an expired token keeps all local data.
- Near-me results are cached in memory (not Room) for 1 hour per roughly 5 km cell (0.05 degree grid) in `NearbyRepository`; the cache is lost with the process.
- Search results are held in memory (`SearchRepository.current`), not persisted; the 30-minute server cache makes a repeat search cheap.

## CurrentPlanStore

Hilt singleton shared by results, chat and plan screens. Holds a `PlanDraft` (`planId`, `name`, `searchRequest`, `searchData`, `selections`, `dirty`).
`toggle(item)` follows the web rules (single-choice categories replace, lists add/remove); writes to Room go through a conflated channel (latest wins);
`save()` creates or updates via `PlansRepository`, snapshotting the live itinerary into `selections.itinerary` and dropping link evidence
(`link_verified`, `link_checked`) like the web; `snapshot()`/`restore()` back the "Undo" for adding the whole best-value package. Details: [Foundation-Interfaces](Foundation-Interfaces.md) section 3.1.

## Unparseable numbers

Lenient serializers turn unparseable numbers into `null`, never `0`, so the UI hides a value instead of inventing one.
