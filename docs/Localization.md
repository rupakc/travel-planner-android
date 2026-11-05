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
- Dates, numbers and prices are formatted in the app language: on screen use `localPrice` / `localUsd` / `PriceText`
  (core:ui) and `currentLocale()` for dates ("1.480 US$", "mié, 1 abr" in Spanish). Exports (share text, .ics) stay
  en-US on purpose. Never write an amount or a currency symbol into copy: pass it as `%1$s`, formatted the same way.
- Typed numbers (budget, price targets, costs) go through `parseLocalizedAmount(text, locale)` in core:domain with the
  UI locale, so "1.500" is 1500 in Spanish and "1,500.50" / "1.500,50" both work.
- Counts of the form "N of M" are phrased without agreement ("Vuelos dentro del presupuesto: 1 de 5") instead of a
  plural keyed on one of the two numbers.
- Decorative emoji at the start of a label are hidden from TalkBack (`core:ui` `emojiFreeLabel` /
  `clearAndSetSemantics`), so "🍜 Gastronomía" is read as "Gastronomía".

## Spanish glossary

Spanish copy is peninsular Spanish with *tú*. Use one word per idea across every module:

| Concept | Use | Not |
|---|---|---|
| Weather (section, compare row, badges) | Tiempo | Clima (only for historical/climate averages: "Estimación climática histórica") |
| Live prices / rates | en directo | en vivo |
| To plan | Planificar / Planifica | Planear / Planea |
| Expand / collapse (actions) | Expandir / Contraer | Desplegar / Plegar |
| Expanded / collapsed (TalkBack states) | Expandido / Contraído | Desplegado / Plegado |
| Confidence (visa, pricing advisor) | Confianza | Fiabilidad |
| Taste profile | Taste Graph (product name) | perfil de gustos |
| Destination field ("To") | Destino | A |
| Estimated cost | ~25 US$ | "25 US$ est." |
| Quotes | «…» | “…” |
| Joining two clauses | colon or full stop ("No se encontraron vuelos: prueba…") | English-style " — " |
| Status lines about an item | impersonal "Se movió %1$s al día 2" | "%1$s movida" (forces a gender on any name) |

The brand name is **Voyager** everywhere (`app_name` / `brand_name`, not translated).

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
