# Travel Planner for Android ("Voyager")

Native Android companion to the [travel-planner](https://github.com/rupakc/travel-planner) web app: the same
account, the same backend, the same saved plans. Kotlin, Jetpack Compose, Material 3. MIT licence.

Status: foundation merged (build system, core modules, app shell); feature screens land module by module
(see [Modules](docs/Modules.md) for what exists today).

## Screenshots

<!-- Filled with real emulator screenshots (light + dark, phone + tablet) once the features are merged. -->
_Screenshots will be added here._

## Features

Parity with the traveller-facing web flows, plus what a phone is good at (design: [spec](docs/superpowers/specs/2026-11-05-android-app-design.md)).

- Sign in and change password (the backend has no self-registration; admins create accounts).
- Search in four modes: known destination, Surprise me, Compare (2-3 destinations), From a link.
- Streamed results, section by section, each with the backend's verification chip; Estimates stay labelled.
- My Plan: select items, save, load, share by link, export (.ics, print).
- Chat with plan commands, streamed.
- Alerts: fare and hotel/activity/trip price watches, notification inbox.
- Traveller-type onboarding, a personalised Home and per-feature visibility (device-local).
- Notifications: local WorkManager polling now, Firebase Cloud Messaging wired but inert until configured.
- Near me recommendations, public share-link viewer (deep link), English and Spanish, dark theme, dynamic colour.
- Booking is always "open the provider's page in a Custom Tab"; there are no in-app payments.

## Architecture

```mermaid
flowchart TD
    app["app: Application, NavHost, adaptive shell"] --> feature["feature:* screens + ViewModels"]
    feature --> ui["core:ui"]
    feature --> ds["core:designsystem"]
    feature --> data["core:data: repositories, CurrentPlanStore"]
    feature --> domain["core:domain"]
    data --> network["core:network: OkHttp, Retrofit, SSE"]
    data --> db["core:database: Room"]
    data --> store["core:datastore: DataStore"]
    network --> model["core:model"]
    data --> model
    domain --> model
    model --> common["core:common"]
```

Unidirectional data flow: `XxxRoute` (Hilt ViewModel, `StateFlow<UiState>`) -> stateless `XxxScreen(state, onEvent)`.
Features see repository interfaces only. Details: [Architecture](docs/Architecture.md).

## Module map

| Module | Purpose |
|---|---|
| `app` | Application, MainActivity, NavHost, auth gate, adaptive navigation, deep links |
| `core:common`, `core:model`, `core:domain` | Pure Kotlin: results/errors, wire models, ported web logic |
| `core:network`, `core:database`, `core:datastore` | Retrofit/OkHttp/SSE, Room, DataStore (internal to `core:data`) |
| `core:data` | Repository interfaces and implementations, `CurrentPlanStore` |
| `core:designsystem`, `core:ui` | Theme, components; shared feature UI |
| `core:notifications`, `core:testing` | Channels/notifier/FCM; fakes and fixtures |
| `feature:auth` ... `feature:share` | One module per screen group (11 in total) |

Full table with allowed dependencies: [Modules](docs/Modules.md).

## Quick start

Requirements: JDK 21 (Temurin), Android SDK with platforms 36 and 37 (compileSdk 37, targetSdk 36, minSdk 26).

```bash
export JAVA_HOME=/path/to/jdk-21 ANDROID_HOME=$HOME/Library/Android/sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleLocalDebug
```

1. Start the backend from the [web repo](https://github.com/rupakc/travel-planner): `./start.sh` (backend on port 8001).
2. Emulator: the `local` flavour already points at `http://10.0.2.2:8001` (the emulator's alias for the host).
3. Physical phone on your LAN: open Settings -> Server and enter `http://<your-computer-LAN-IP>:8001`.
4. Sign in with an account an admin created on the backend.

Push notifications are optional: they need `app/google-services.json` ([Firebase-Setup](docs/Firebase-Setup.md), written by the push team).

## Build variants

| Variant | Backend | Cleartext |
|---|---|---|
| `localDebug` / `localRelease` | `http://10.0.2.2:8001` (override in Settings) | allowed |
| `prodDebug` / `prodRelease` | `-PPROD_BASE_URL=https://...` | HTTPS only |

`./gradlew assembleProdRelease -PPROD_BASE_URL=https://your-host`. Release signing: [Release](docs/Release.md).

## Tests and checks

```bash
./gradlew spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug assembleProdRelease   # what CI runs
./gradlew spotlessApply                                                                                         # fix formatting
scripts/check-doc-links.sh                                                                                      # relative doc links
```

See [Testing](docs/Testing.md). Contributions: [CONTRIBUTING](CONTRIBUTING.md), [CODE_OF_CONDUCT](CODE_OF_CONDUCT.md).

## Documentation

Start at [docs/Home.md](docs/Home.md) (also the GitHub wiki index). Decisions: [MEMORY.md](MEMORY.md).
Claude Code users: [CLAUDE.md](CLAUDE.md) and `.claude/`.

## Licence and attributions

MIT, see [LICENSE](LICENSE). Place data shown by the backend contains data from GeoNames (geonames.org), CC BY 4.0;
the app credits it in its attribution footer where the web does.
