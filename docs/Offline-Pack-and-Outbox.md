# Offline pack and outbox

Room storage (R6): tables `offline_pack` and `outbox`, keyed `userId:tripId`, with an explicit migration and a migration test. See also [Data-and-Offline](Data-and-Offline.md).

- **Offline pack**: "Save for offline" stores a trip snapshot so the trip opens in airplane mode. A full disk surfaces as `AppError.Unknown("storage_full")`. `OfflinePurger` is a `UserStateCleaner` multibinding, so sign-out wipes packs and queued writes. The pack destination is registered via `offlinePackDestination`.
- **Outbox**: expenses and journal writes made offline are queued and replayed in order. Flush is a WorkManager job, single-flight per user, and also runs on reconnect. `OutboxApi`/`OutboxHttp` carry the calls; tests use `FakeOutbox`.
- **Online only** (R7): dreaming-trip creation, rename, dates, phase override and delete.
- Strings live in `strings_offline.xml`.
