---
name: add-feature-module
description: Scaffold a new feature module (feature/<name>) wired into settings, app and navigation following the repo's rules.
---

# Add a feature module

Read `docs/Adding-a-Feature.md`, `docs/Modules.md`, `docs/Foundation-Interfaces.md` section 5 first. Confirm with the spec that the feature is in scope.

1. Copy an existing skeleton such as `feature/nearby` (`build.gradle.kts`, `consumer-rules.pro`, `src/main`, `src/test`), rename the namespace to `io.github.rupakc.travelplanner.feature.<name>`. The `travelplanner.android.feature` plugin supplies the allowed core dependencies and the `local`/`prod` flavours: add no other project dependency.
2. `settings.gradle.kts`: `include(":feature:<name>")`; `app/build.gradle.kts`: `implementation(projects.feature.<name>)`.
3. Route: add `@Serializable` route(s) to `core/model/.../navigation/Routes.kt` (and a `DeepLinks` pattern + manifest host if linkable).
4. Graph: `fun NavGraphBuilder.<name>Graph(...)` with `composable<XRoute>`; register in `app/.../navigation/TravelApp.kt`.
5. Screens: `XScreen(state, onEvent)` + `XRoute` with `@HiltViewModel`; states for loading, empty, error; previews light and dark.
6. Strings in `values/strings.xml` and `values-es/strings.xml` (prefix `<name>_`).
7. Tests first (ViewModel with fakes, Compose on Robolectric).
8. Docs: add a TitleCase page under `docs/`, link from `docs/Home.md` and `docs/Modules.md`, add a `MEMORY.md` line.
9. Run the `verify-android` skill.
