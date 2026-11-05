# Architecture

## Layers

```mermaid
flowchart LR
    subgraph UI["UI layer"]
        screen["XxxScreen - stateless Compose"]
        route["XxxRoute + HiltViewModel"]
    end
    subgraph DataLayer["Data layer - core:data"]
        repo["Repository interfaces"]
        impl["Implementations"]
        plan["CurrentPlanStore"]
    end
    subgraph Sources["Sources"]
        net["core:network - Retrofit, OkHttp, SSE"]
        room["core:database - Room"]
        ds["core:datastore - DataStore"]
    end
    screen -- events --> route
    route -- StateFlow UiState --> screen
    route --> repo
    repo --> impl
    impl --> net
    impl --> room
    impl --> ds
    route --> plan
    plan --> room
```

- **app** owns the Application, `MainActivity` (`singleTask`), the `NavHost`, the auth gate and the adaptive navigation suite
  (bottom bar on compact windows, rail on medium and expanded).
- **feature:\*** modules contain screens and ViewModels only. They inject repository *interfaces* and never touch Retrofit, DAOs or DataStore.
- **core:data** exposes repositories (all suspend calls return `AppResult<T>`, never throwing except cancellation) and the shared `CurrentPlanStore`.
- **core:model / core:domain / core:common** are pure Kotlin, so logic is unit-testable without Android.

## Unidirectional data flow

Each screen is `XxxScreen(state, onEvent, modifier)`, a stateless composable, plus `XxxRoute` which obtains a
`@HiltViewModel` (`androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`), collects its `StateFlow<XxxUiState>`
with `collectAsStateWithLifecycle()` and forwards events. UI state is an immutable data class. Navigation out of a
feature goes through lambdas the feature's graph function receives; `app` turns them into routes from
`core:model` (`core.model.navigation`). Features never depend on each other.

## Dependency injection

Hilt (KSP). `app` is the graph root. Modules: `NetworkModule` (three OkHttp clients and the Retrofit APIs),
`DataModule` (repository bindings), `DataStoreModule`, the Room module, `NotifierModule` and `PushModule`, `AppModule`.
`@ApplicationScope` provides the long-lived coroutine scope (draft writes, push registration).

```mermaid
flowchart TD
    Singleton["SingletonComponent"] --> NetworkModule
    Singleton --> DataModule
    Singleton --> DataStoreModule
    Singleton --> NotifierModule
    Singleton --> PushModule
    NetworkModule --> OkHttp["DefaultHttp / AiHttp / StreamingHttp clients"]
    OkHttp --> Retrofit["Retrofit APIs"]
    Retrofit --> DataModule
    DataStoreModule --> DataModule
    DataModule --> VM["Feature ViewModels"]
```

## Data flow of a search stream

```mermaid
sequenceDiagram
    participant UI as Search / Results screen
    participant VM as ViewModel
    participant Repo as SearchRepository
    participant SSE as Streaming client (core:network)
    participant API as POST /api/search
    UI->>VM: onEvent(Submit)
    VM->>Repo: start(request)
    Repo->>SSE: open stream (no read timeout)
    SSE->>API: POST TravelSearchRequest
    API-->>SSE: data: {"type":"visa",...} (static, instant)
    API-->>SSE: data: {"type":"flights",...} as each agent completes
    SSE-->>Repo: SearchEvent items
    Repo->>Repo: reduce into SearchResultsState (replace by type)
    Repo-->>VM: current: StateFlow
    VM-->>UI: UiState, sections appear one by one
    API-->>SSE: data: {"type":"done"}
```

`SearchRepository.current` is app-wide, so a running search survives navigation (web parity). The reducer applies
replace-by-type, keeps old data when a later AI result is an error, and retries once if the stream ends without
`done` (sections received before the interruption are kept). See [Networking-and-SSE](Networking-and-SSE.md).

## Where things live

| Concern | Location |
|---|---|
| Interfaces features build on | [Foundation-Interfaces](Foundation-Interfaces.md) |
| Errors | `AppError` sealed hierarchy in `core:common`; backend `detail` is shown as-is |
| Plan draft shared by results, chat and plan screens | `CurrentPlanStore` (`core:data`), persisted to Room |
| Tolerant JSON | `TravelJson` in `core:model`; `RawBacked` keeps original JSON for lossless web round trips |
