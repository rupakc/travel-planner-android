---
name: add-translation
description: Add or update translated strings (new strings in en+es, or a whole new locale) and keep keys aligned across modules.
---

# Add translations

Read `docs/Localization.md`.

New or changed string: add it to the module's `src/main/res/values/strings.xml` and `values-es/strings.xml` in the same commit, same name, prefixed with the feature. Use `<plurals>` (Spanish: `one`, `many`, `other`) for counts, `%1$s` positional placeholders, and reuse the web's English copy (`docs/reference/web-ui-inventory.md`). Never concatenate translated fragments. Do not translate backend `detail` text.

New locale `<tag>`:
1. Create `values-<tag>/strings.xml` in every module that has `values/strings.xml`.
2. Add `<locale android:name="<tag>" />` to `app/src/main/res/xml/locales_config.xml`.
3. Add `"<tag>"` to `androidResources.localeFilters` in `app/build.gradle.kts`.
4. Run `./gradlew lintLocalDebug` (MissingTranslation is an error) and the unit tests; check RTL and 200 % font scale for the new language.

Diff keys quickly: `diff <(grep -o 'name="[^"]*"' values/strings.xml | sort) <(grep -o 'name="[^"]*"' values-es/strings.xml | sort)`.
