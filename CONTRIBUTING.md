# Contributing

Thanks for helping. Please read the [Code of Conduct](CODE_OF_CONDUCT.md) first.

## Setup

Follow the quick start in [README.md](README.md). You need JDK 21 and the Android SDK; the backend is only needed to run the app, not the tests.

## Workflow

1. Branch from `main` (`feat/<topic>` or `fix/<topic>`). Keep PRs small and focused.
2. Write the test first, then the code. No network in tests; use the fakes in `core:testing` ([Testing](docs/Testing.md)).
3. Run the full check before pushing (same as CI):
   `./gradlew spotlessApply && ./gradlew spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug assembleProdRelease`
   and `scripts/check-doc-links.sh` if you touched docs.
4. Open a PR using the template; attach light and dark screenshots for UI changes.

## Rules reviewers enforce

- Module boundaries: features depend only on `core:{ui,designsystem,data,domain,model,common}` and never on each other ([Modules](docs/Modules.md)).
- Grounding: show only API data; never invent prices, links or facts; Estimates keep their label.
- Every user-facing string is a resource with a Spanish translation; plurals use `<plurals>` ([Localization](docs/Localization.md)).
- Accessibility: content descriptions, 48 dp targets, headings, works at 200 % font scale ([Accessibility](docs/Accessibility.md)).
- Design tokens only; no hard-coded colours or dp in feature code ([Design-System-and-Theming](docs/Design-System-and-Theming.md)).
- Versions live only in `gradle/libs.versions.toml`. Never commit `local.properties`, keystores or `google-services.json`.
- Record decisions the spec leaves open in [MEMORY.md](MEMORY.md).

Adding a module or endpoint: [Adding-a-Feature](docs/Adding-a-Feature.md), [Backend-Integration](docs/Backend-Integration.md).
