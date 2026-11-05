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

## Is it an optional feature (shown or hidden per traveller type)?

A new screen or results section that users can switch on and off also needs these, or it compiles but never
appears in onboarding, Settings, Home or Global search:

1. `FeatureId` in `core/domain/.../feature/FeatureCatalogue.kt` (the wire id is stored in the profile).
2. Its catalogue entry (icon, default traveller types) in `core/domain/.../profile/DefaultFeatureCatalogue.kt`.
3. Title and description strings in `core/ui/.../profile/ProfileUi.kt` (`titleRes()` and friends), English and Spanish.
4. Home: the tile's destination in `feature/home/.../HomeRoutes.kt` (`isTool`, `destination()`); a results
   section also maps to its `SectionId` in `feature/home/.../ProfileHelpers.kt` (`sectionId()`).
5. Results section: `SectionArrangement.FEATURE_OF` in `core/domain/.../results/SectionArrangement.kt`.
6. Global search reads the catalogue, so a new feature is found by its title once 1-3 are done; check
   `core/domain/.../globalsearch/GlobalSearch.kt` if it needs extra keywords.

Visibility has one rule for every screen: read `FeatureVisibility` from `core:data`
(`core/data/.../profile/FeatureVisibility.kt`); never re-derive it from the profile in a feature.

## Is it a top-level tab?

Add an entry to `TopLevelDestination` (`app/.../navigation/TopLevelDestination.kt`). Switch to a tab with
`navigateToTopLevel`/`navigateTo` in `TravelApp.kt` (save and restore tab state); never call `navigate()` with a tab route.

## Does it keep per-user state on the device?

Clear it on sign-out, a different user or a server change: add a step to `SessionCleanerImpl`
(`core/data/.../impl/SessionCleanerImpl.kt`) or contribute a `UserStateCleaner` with Hilt `@IntoSet`. If it lives in
its own SharedPreferences or DataStore file, decide whether it may be backed up and update
`app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`.

## Deep links

Patterns live in `DeepLinks` (`core/model/.../navigation/Routes.kt`) with a matching host in the manifest. Only
`travelplanner://` VIEW intents route (`DeepLinkIntents` in `app` strips everything else); a link that arrives while
signed out is kept by `ShellViewModel` and opened after sign-in (`DeepLinkHold`, unit-tested in `DeepLinkHoldTest`).

The `add-feature-module` Claude skill automates the boilerplate steps.
