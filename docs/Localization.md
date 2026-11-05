# Localization

Shipped locales: `en` (default) and `es`. The per-app language list is `app/src/main/res/xml/locales_config.xml`, referenced by
`android:localeConfig` in the manifest, and the build keeps only those resources (`androidResources.localeFilters += listOf("en", "es")` in `app/build.gradle.kts`).

## Rules

- Every user-facing string lives in the owning module's `res/values/strings.xml`, prefixed with the feature (`search_...`), with a Spanish translation
  in `res/values-es/strings.xml`. Lint treats `MissingTranslation` and hard-coded text as errors.
- English copy reuses the web's wording (see [web-ui-inventory](reference/web-ui-inventory.md)).
- Counts use `<plurals>`; Spanish needs `one`, `many` and `other`. Use `pluralStringResource`.
- Backend `detail` messages are shown as-is (web parity); they are not translated.
- Layouts are RTL-safe: use start/end, never left/right.
- Dates, numbers and prices are formatted per locale; money goes through `formatPrice` / `PriceText`.

## Adding a language

1. Add `values-<tag>/strings.xml` in **every** module that has a `values/strings.xml` (app, `core:*` with strings, features).
2. Add `<locale android:name="<tag>" />` to `app/src/main/res/xml/locales_config.xml`.
3. Add the tag to `androidResources.localeFilters` in `app/build.gradle.kts`.
4. Run `./gradlew lintLocalDebug` (fails on any missing key) and the unit tests.
5. Use the `add-translation` Claude skill (`.claude/skills/add-translation/SKILL.md`) to keep the keys aligned.

## Testing locales

- Per-app language: Android 13+ Settings -> System -> Languages -> App languages -> Voyager; Settings -> Appearance in the app stores the tag
  (`SettingsRepository.setLocaleTag`).
- Pseudo-locales: enabled on the debug build type (`isPseudoLocalesEnabled = true` in `AndroidApplicationConventionPlugin`). In a debug build pick "English (XA)" (accented, expanded text) or "Arabic (XB)" (RTL) in system language settings.
- Font scale: set to 200 % and walk the screen ([Accessibility](Accessibility.md)).
