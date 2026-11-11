# Localization

Seven languages ship: `en` (default), `es`, `de`, `fr`, `hi`, `zh`, `bn`. The picker lists them by their native names (English, Español,
Deutsch, Français, हिन्दी, 中文（简体）, বাংলা). The per-app language list is `app/src/main/res/xml/locales_config.xml`, referenced by
`android:localeConfig` in the manifest, and the build keeps only those resources (`androidResources.localeFilters` in `app/build.gradle.kts`).
The five new languages are switched on in one final change (locale filters, `locales_config`, picker entries), so until it lands the
translated files exist in the repo but are not in the APK.

## Folders and fonts

- `values/` is English, `values-es/` Spanish, then `values-de`, `values-fr`, `values-hi`, `values-zh`, `values-bn`.
- `values-zh` is Simplified Chinese. Traditional Chinese devices (`zh-TW`, `zh-HK`) fall back to it, as on the web; `AppLanguages.resolve`
  maps `zh-TW` to `zh`. There is no `values-zh-rTW`.
- The AI `locale` field sent to the backend is the base code. Telemetry `client.locale` is `zh-CN`, `hi-IN` or `bn-IN` for those three
  languages and the bare code for the others (the web's `intlLocale`).
- No font is bundled for the new scripts: the system Noto fonts cover Devanagari (hi), Bengali (bn) and Han (zh). Sora and Inter stay for Latin text.

## Rules

- Every user-facing string lives in the owning module's `res/values/strings.xml`, prefixed with the feature (`search_...`), with a Spanish translation
  in `res/values-es/strings.xml` and translations for de, fr, hi, zh and bn (generated or hand-written, see below). Lint treats
  `MissingTranslation` and hard-coded text as errors.
- `values-<lang>/strings_web.xml` (de fr hi zh bn) is generated and never edited by hand. Spanish stays hand-maintained and is never generated.
- Every other string for de fr hi zh bn is hand-translated in files with the same names as the module's `values/` files (`strings.xml`, ...).
- The brand name is `brand_name` and reaches copy through `%1$s`; it is never translated.
- "Taste Graph" follows the web catalogue's translated term in de fr hi zh bn (for example Geschmacksprofil); Spanish and English keep "Taste Graph".
- English copy reuses the web's wording (see [web-ui-inventory](reference/web-ui-inventory.md)).
- Counts use `<plurals>`; use `pluralStringResource`. Categories per language: see the plural table below.
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

## Plural categories

| Language | Categories | Language | Categories |
|---|---|---|---|
| en | one, other | hi | one, other |
| es | one, many, other | zh | other |
| de | one, other | bn | one, other |
| fr | one, many, other | | |

The generator writes exactly these, converting the web's i18next `_one` / `_other` forms; a plural form without `{{count}}` keeps the other
placeholders. Hand-written `<plurals>` must use the same categories (lint flags missing ones).

## Web-synced strings (generators)

The web repo (`rupakc/travel-planner`) is the source of the de, fr, hi, zh and bn copy. The pinned commit is `web.sha` in
`config/web-source.properties`. Stdlib-only Python lives in `tools/web-sync/`: `gen_strings.py` (strings) and `gen_tokens.py` (design tokens,
see [Design system and theming](Design-System-and-Theming.md)). Each module opts in with a map file `config/i18n/web-keys/<module>.json`
(`feature/results` becomes `feature-results.json`):

```json
{"strings": {"nav_chat": "common:nav.chat"}, "plurals": {}}
```

A mapping is valid only when the Android English equals the web English once placeholders and escapes are normalised (the web literal
`Voyager` may stand for `%1$s`). Apostrophes, `%`, `<`, `&` and a leading `@` are escaped; `%` becomes `%%` only in strings with arguments.

Flow for a module (`W` is the web checkout's `frontend/src/i18n/locales` at the pinned sha, for example from `git -C ~/travel-planner archive`):

1. Pin: `web.sha` is already set.
2. `python3 tools/web-sync/gen_strings.py suggest --web W --module feature/results`: proposes pairs. Run it once per module, then prune; it
   re-adds deleted pairs if run again.
3. Review every pair for the same meaning and context, and delete wrong ones from the map.
4. `python3 tools/web-sync/gen_strings.py generate --web W --module feature/results`: writes `values-<lang>/strings_web.xml` (4-space indent,
   like every other strings file).
5. Hand files: translate every remaining string in `values-<lang>/` under the same file names as `values/`.
6. `scripts/check-web-sync.sh <web git dir>`: runs the generator unit tests, `gen_tokens.py check` and `gen_strings.py check` against the
   pinned sha. CI runs it.

Bumping `web.sha`:

```bash
git -C ~/travel-planner fetch
# edit config/web-source.properties: web.sha=<new commit>
git -C ~/travel-planner archive <sha> frontend/design/tokens.json frontend/src/i18n/locales | tar -x -C "$TMP"
python3 tools/web-sync/gen_tokens.py generate --tokens "$TMP/frontend/design/tokens.json"
python3 tools/web-sync/gen_strings.py generate --web "$TMP/frontend/src/i18n/locales"   # every module
scripts/check-web-sync.sh ~/travel-planner
```

Then review the diff (changed web copy may break an English match; fix the map or the hand string) and commit.

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

1. Add `values-<tag>/strings.xml` in **every** module that has a `values/strings.xml` (app, `core:*` with strings, features). For a web
   language add it to `LANGS` and `PLURAL_CATEGORIES` in `tools/web-sync/gen_strings.py` and generate.
2. Add `<locale android:name="<tag>" />` to `app/src/main/res/xml/locales_config.xml`.
3. Add the tag to `androidResources.localeFilters` in `app/build.gradle.kts`.
4. Run `./gradlew lintLocalDebug` (fails on any missing key) and the unit tests.
5. Use the `add-translation` Claude skill (`.claude/skills/add-translation/SKILL.md`) to keep the keys aligned.

## Testing locales

- Per-app language: Android 13+ Settings -> System -> Languages -> App languages -> Voyager; Settings -> Appearance in the app stores the tag
  (`SettingsRepository.setLocaleTag`).
- Pseudo-locales: enabled on the debug build type (`isPseudoLocalesEnabled = true` in `AndroidApplicationConventionPlugin`). In a debug build pick "English (XA)" (accented, expanded text) or "Arabic (XB)" (RTL) in system language settings.
- Font scale: set to 200 % and walk the screen ([Accessibility](Accessibility.md)).
