# Journey (Trips, Prep, Today, Money, Journal, Recap)

Enabled by the server's journey flag (`JourneyFlags`); off keeps the classic tabs.

- **Tabs** (R4): journey on gives Trips, Explore, Alerts, Play, You; off gives Home, Search, Chat, My trips, Alerts. Explore is Search with Chat and Near me entries; saved plans stay reachable from Trips.
- **Trips hub** (`feature:trips`): list cached per user in memory; the trip phase (dreaming, planning, on the road, back home) is recomputed on the device and passes `trip_phase_cases.json` (R8). Place keys follow `place_key_cases.json`, including the FNV-1a suffix for non-ASCII names. Both fixtures sit in `core/domain/src/test/resources/journey/`.
- **Trip screen**: tabs are plugged in via `tabContent`; an empty tab is hidden. Prep checklist and a completeness meter (ProgressRing in core:ui).
- **Today**: check-in and "I'm here" on nearby items (hidden for Maps-grounded v2 items).
- **Money**: expenses and budget; offline-capable through the outbox.
- **Journal**: entries, offline-capable through the outbox.
- **Recap**: stats and highlights for a finished trip; share cards are Phase 4.
- Dreaming-trip creation, rename, dates, phase override and delete are online only (R7).
- Known gaps: standalone Today/Expenses/Journal/Recap routes are not wired, `gameEnabled` ignores `hide_game`, "Save as trip" does not copy dates from the request. See the follow-ups file in `docs/superpowers/plans/`.
