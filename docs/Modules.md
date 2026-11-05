# Modules

Declared in `settings.gradle.kts`. Convention plugins in `build-logic/convention` (`android-application`, `android-library`,
`android-feature`, compose, hilt, room, `jvm-library`, unit tests) set SDK levels, flavours and lint. Versions live only in
`gradle/libs.versions.toml`.

## Core

| Module | Kind | Purpose | May depend on | Key types |
|---|---|---|---|---|
| `core:common` | pure Kotlin | Shared primitives | none | `AppResult`, `AppError`, `AppException`, `AppDispatchers`, `AppClock`, `AppLogger`, `AppBuildInfo` |
| `core:model` | pure Kotlin | Wire models and routes (kotlinx.serialization, tolerant) | common | `TravelSearchRequest`, `SectionId`, `SectionData` subtypes, `SearchEvent`, `ChatEvent`, `Plan`, `PlanSelections`, `AppRoute`, `TravelJson` |
| `core:domain` | pure Kotlin | Ported web logic and catalogues (itinerary layout + schedule, plan export, alerts rules, global search, results arrangement, chat history) | common, model | `TravellerType`, `FeatureId`, `FeatureCatalogue`, `ItineraryLayouts`, `ItinerarySchedule`, `PlanItinerary`, `PlanIcs`, `formatPrice` |
| `core:network` | Android | OkHttp, Retrofit APIs, SSE | common, model, datastore (auth token) | `BaseUrlProvider`, `AuthInterceptor`, `SseParser`, `HttpErrorMapper` |
| `core:database` | Android | Room cache | common, model | `TravelDatabase`, `CachedPlanEntity`, `PlanDraftEntity` |
| `core:datastore` | Android | Preferences DataStore | common, model | `SessionStore`, `SettingsStore` |
| `core:data` | Android | Repositories, plan draft, session cleanup, feature visibility | all of the above | `SearchRepository`, `PlansRepository`, `CurrentPlanStore`, `SessionCleaner`, `FeatureVisibility` |
| `core:designsystem` | Compose | Theme and components | model | `TravelTheme`, `TpButton`, `TpCard`, `TpChip`, `TpTopBar`, `EmptyState`, `ErrorState` |
| `core:ui` | Compose | Shared feature UI | designsystem, model, domain | `SectionScaffold`, `VerificationChip`, `PriceText`, `BookingLinkButton`, `AddToPlanButton`, `ItineraryDayCard`, `ItinerarySlotRow`, fare/price watch sheets, profile strings |
| `core:notifications` | Android | Channels, notifier, alert poll, trip reminders, Near me suggestions, optional FCM | common, model, data, domain | `TravelChannel`, `Notifier`, `NotificationsInitializer`, `AlertPollWorker`, `TripReminderScheduler`, `NearbySuggestionWorker`, `TravelMessagingService` |
| `core:testing` | Android (tests) | Fakes and fixtures | common, model, data, network, datastore | `MainDispatcherRule`, fake repositories, SSE fixtures |

`core:network`, `core:database` and `core:datastore` are implementation details of `core:data`; features cannot see them. Exception: `core:testing` exposes network and datastore through `api` for tests, so use it only as a test dependency.

## Features

A feature module depends on exactly `core:{ui,designsystem,data,domain,model,common}` (the `android-feature` plugin wires this; do not add
other project dependencies). Each exports one graph function that `app` calls in `TravelApp.kt`.

| Module | Screens | Routes |
|---|---|---|
| `feature:auth` | Login, server setup, change password (forced and from Settings) | `LoginRoute`, `ChangePasswordRoute`, `AccountChangePasswordRoute` |
| `feature:onboarding` | Traveller-type quiz, result, feature picker | `OnboardingRoute` |
| `feature:home` | Personalised home, global search | `HomeRoute`, `GlobalSearchRoute` |
| `feature:search` | Search form (4 modes), recent searches | `SearchRoute` |
| `feature:results` | Streamed results | `ResultsRoute` |
| `feature:plan` | My trips, plan detail, My Plan (current draft) | `PlansRoute`, `PlanDetailRoute`, `MyPlanRoute` |
| `feature:chat` | Chat threads and streaming | `ChatRoute` |
| `feature:alerts` | Watches, notification inbox | `AlertsRoute`, `NotificationsRoute` |
| `feature:settings` | Preferences, taste graph, features, appearance, server, notifications | `SettingsRoute`, `PreferencesRoute`, `TasteProfileRoute`, `FeaturesRoute`, `AppearanceRoute`, `ServerRoute`, `NotificationSettingsRoute`, `SettingsEntryRoute` |
| `feature:nearby` | Near me | `NearbyRoute` |
| `feature:share` | Public share viewer | `ShareRoute` |

Every feature screen is implemented; `core:ui` still has the foundation's `FeaturePlaceholder`, which no route uses.
The graph signatures are listed in [Foundation-Interfaces](Foundation-Interfaces.md) section 5.

## Rules

- Features never depend on each other; cross-feature navigation uses route objects from `core:model`.
- Need something new from a shared core module? Make the smallest additive change in its own commit and say so in the PR; never rename or remove shared APIs.
- Adding a module: [Adding-a-Feature](Adding-a-Feature.md).
