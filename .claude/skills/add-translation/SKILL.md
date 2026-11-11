---
name: add-translation
description: Add or update translated strings (new strings in en+es, or a whole new locale) and keep keys aligned across modules.
---

# Add translations

Read `docs/Localization.md`.

Seven languages ship: en es de fr hi zh bn. `values-es` is hand-maintained. For de fr hi zh bn, strings whose English matches a web key are generated into `values-<lang>/strings_web.xml` (never edit it by hand); every other string is hand-translated in files with the same names as the module's `values/` files. Plural categories: de one/other, fr one/many/other, hi one/other, zh other, bn one/other. Brand name via `%1$s` (`brand_name`), never translated.

Web-synced flow (`W` = web `frontend/src/i18n/locales` at the pin in `config/web-source.properties`): `python3 tools/web-sync/gen_strings.py suggest --web W --module <m>` (once per module, then prune, review each pair) -> `generate --web W --module <m>` -> write the hand files -> `scripts/check-web-sync.sh <web git dir>`. To bump the pin: `git -C ~/travel-planner fetch`, edit `web.sha`, run `gen_tokens.py generate` and `gen_strings.py generate` for every module, commit.

New or changed string: add it to the module's `src/main/res/values/strings.xml` and `values-es/strings.xml` in the same commit (then the hand files for de fr hi zh bn, or a web mapping), same name, prefixed with the feature. Use `<plurals>` (Spanish: `one`, `many`, `other`) for counts, `%1$s` positional placeholders, and reuse the web's English copy (`docs/reference/web-ui-inventory.md`). Never concatenate translated fragments. Do not translate backend `detail` text.

New locale `<tag>`:
1. Create `values-<tag>/strings.xml` (web languages: add to `LANGS` and `PLURAL_CATEGORIES` in `gen_strings.py`) in every module that has `values/strings.xml`.
2. Add `<locale android:name="<tag>" />` to `app/src/main/res/xml/locales_config.xml`.
3. Add `"<tag>"` to `androidResources.localeFilters` in `app/build.gradle.kts`.
4. Run `./gradlew lintLocalDebug` (MissingTranslation is an error) and the unit tests; check RTL and 200 % font scale for the new language.

Diff keys quickly: `diff <(grep -o 'name="[^"]*"' values/strings.xml | sort) <(grep -o 'name="[^"]*"' values-es/strings.xml | sort)`.
