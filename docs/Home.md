# Travel Planner for Android

Wiki index for the native Android app (Kotlin, Compose, Material 3). The app is a companion to the
[travel-planner web app](https://github.com/rupakc/travel-planner): same backend, account and plans.
Source of truth for scope: the [design spec](superpowers/specs/2026-11-05-android-app-design.md).

## Start here

- [README](../README.md) - what it is and quick start
- [Architecture](Architecture.md), [Modules](Modules.md)
- [Play (game)](Play.md)
- [Adding-a-Feature](Adding-a-Feature.md), [Foundation-Interfaces](Foundation-Interfaces.md)

## Building blocks

- [Networking-and-SSE](Networking-and-SSE.md), [Backend-Integration](Backend-Integration.md)
- [Data-and-Offline](Data-and-Offline.md)
- [Design-System-and-Theming](Design-System-and-Theming.md), [Localization](Localization.md), [Accessibility](Accessibility.md)
- [Search-and-Filter](Search-and-Filter.md)
- [Telemetry-and-Privacy](Telemetry-and-Privacy.md)
- [Onboarding-and-Traveller-Types](Onboarding-and-Traveller-Types.md) (written by the onboarding team)
- [Notifications-and-Push](Notifications-and-Push.md), [Firebase-Setup](Firebase-Setup.md) (written by the push team)

## Quality and operations

- [Testing](Testing.md), [Performance](Performance.md), [Security](Security.md)
- [Scaling](Scaling.md), [Release](Release.md), [Troubleshooting](Troubleshooting.md)

## Reference

- `reference/api-contract.md` - backend contract read from code: [api-contract](reference/api-contract.md)
- Web UI inventory (copy, tokens): [web-ui-inventory](reference/web-ui-inventory.md)
- Decision log: [MEMORY](../MEMORY.md)

Pages marked "Planned" describe work that is specified but not yet merged. The pages describe `main` after the review-round-4 fix pass FP3;
the notifications/push performance fix pass (FP1) has landed; the UI/accessibility/i18n fix pass (FP2) may land separately and update its pages then.
