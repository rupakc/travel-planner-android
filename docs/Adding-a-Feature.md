# Adding a feature module

Read [Modules](Modules.md) and [Foundation-Interfaces](Foundation-Interfaces.md) first. Existing features: the 11 modules under `feature/`.

1. **Plan the contract.** Which repositories do you need (interfaces in `core:data`)? Which route (in `core:model` `navigation`)? If a repository method or model field is missing, add the smallest additive change in its own commit (`core: ...`).
2. **Create the module** `feature/<name>`: copy a skeleton (`build.gradle.kts` applies the `travelplanner.android.feature` convention plugin; it wires the allowed core dependencies and the `local`/`prod` flavours).
3. **Register it** in `settings.gradle.kts` (`include(":feature:<name>")`) and add `implementation(projects.feature.<name>)` to `app/build.gradle.kts`.
4. **Route and graph.** Add `@Serializable data class/object XRoute : AppRoute` to `Routes.kt`; in your module export `fun NavGraphBuilder.xGraph(<callbacks>)` using `composable<XRoute>`; call it in `app/.../navigation/TravelApp.kt`. Add deep links via `DeepLinks` and the manifest if needed.
5. **Screens.** `XScreen(state, onEvent)` stateless + `XRoute` with `@HiltViewModel` exposing `StateFlow<XUiState>`; loading (skeleton), empty and error-with-retry states from `core:designsystem`; no hard-coded colours or dp.
6. **Strings.** `res/values/strings.xml` and `values-es/strings.xml`, prefix `x_`; plurals for counts ([Localization](Localization.md)).
7. **Accessibility** checklist ([Accessibility](Accessibility.md)).
8. **Tests first.** ViewModel tests (fakes, Turbine), pure-function tests, Compose tests on Robolectric ([Testing](Testing.md)).
9. **Docs.** Add a wiki page for the feature in `docs/` (TitleCase), link it from [Home](Home.md), add a decision line to [MEMORY](../MEMORY.md) for open calls, and list the module in [Modules](Modules.md).
10. **Verify** with `.claude/skills/verify-android` (or the CI command) and open a PR using the template.

The `add-feature-module` Claude skill automates the boilerplate steps.
