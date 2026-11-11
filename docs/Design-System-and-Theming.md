# Design system and theming

`core:designsystem` holds tokens and components; feature code uses only these (no hard-coded colours or dp). The look follows the web: teal
primary, Sora for display and headlines, Inter for body, an accent colour per section.

## Tokens

| Token | Accessor | Notes |
|---|---|---|
| Colours | `TravelTheme.colors` (M3 `ColorScheme`) | `LightColors` / `DarkColors` in `theme/Color.kt`; palette constants in `theme/Palette.kt` and `theme/DarkPalette.kt` (generated) |
| Extended colours | `TravelTheme.extendedColors` | `success`, `warning` (Estimate, over budget), `info`, skeleton colours and containers |
| Typography | `TravelTheme.typography` | Sora display/headline, Inter title/body/label via Google downloadable fonts, system fallback (`theme/Type.kt`) |
| Shapes | `TravelTheme.shapes`, `TravelShape` | Input 8, ItemCard 12, LargeCard 24, Pill |
| Spacing | `TravelTheme.spacing` | xxs 2, xs 4, sm 8, md 12, lg 16, xl 24, xxl 32, gutter 16, minTouchTarget 48, accentBar 4 (dp) |
| Section accents | `TravelTheme.sectionAccents[SectionId]` | `SectionAccent(bar, icon, tint, text)`: `text` (light `-700`, dark `-300`) for words in the accent colour |

## Changing the brand

- Colours: `Palette.kt` and `DarkPalette.kt` are generated from the web's `frontend/design/tokens.json`; change the web tokens and bump
  `config/web-source.properties` (see [Token generator](#token-generator)), never edit them by hand. The two schemes in `theme/Color.kt` and
  the section accents in `theme/SectionAccent.kt` are hand-written.
  Run `:core:designsystem:testLocalDebugUnitTest`: `ContrastTest` fails on any text pair under 4.5:1 or border under 3:1.
- Typography: change the font families in `theme/Type.kt`. Fonts come from the Google Fonts provider; the certificates ship under our own
  names (`tp_google_fonts_certs`). For a bundled font, add it under `res/font` and swap the `FontFamily`.
- Never reference colours by literal in features; add a token instead.

## Dark theme and dynamic colour

`TravelTheme(themeMode, dynamicColor)` is applied once at the app root. `ThemeMode` is SYSTEM, LIGHT or DARK (Settings -> Appearance) and the
**default is LIGHT** (web parity: the web is light until the user chooses); an unreadable stored value also falls back to LIGHT.
Dynamic colour (Android 12+, off by default) replaces the M3 scheme only; extended colours and section accents stay brand colours so the
sections keep their identity.

Dark roles (`DarkColors` in `theme/Color.kt`; `DarkPalette` holds the web's dark neutrals, `Palette` the shared hues):

| Role | Dark source |
|---|---|
| primary / onPrimary | Teal400 / Teal950 |
| primaryContainer / onPrimaryContainer | Teal900 / Teal100 |
| secondary / tertiary | Sky400 / Violet400 (containers 800, on-colours 950 and 100) |
| error / errorContainer | Red400 / Red900 |
| background, surfaceDim, surfaceContainerLowest | DarkPalette Gray50 |
| surface, surfaceContainerLow | DarkPalette White (the web's dark card colour) |
| surfaceContainer, surfaceVariant | DarkPalette Gray100 |
| surfaceBright, surfaceContainerHigh, outlineVariant | DarkPalette Gray200 |
| surfaceContainerHighest | DarkPalette Gray300 |
| onBackground, onSurface | DarkPalette Gray900 |
| onSurfaceVariant, outline | DarkPalette Gray700, Gray600 |

`ContrastTest` checks light and dark schemes and every section accent against surface (text 4.5:1, borders 3:1).

## Token generator

`tools/web-sync/gen_tokens.py` turns the web's `frontend/design/tokens.json` at the pinned `web.sha` into `Palette.kt` (light) and
`DarkPalette.kt` (dark), each starting with a "Generated ... Do not edit" header naming the repo and sha.

```bash
python3 tools/web-sync/gen_tokens.py generate --tokens <web>/frontend/design/tokens.json
python3 tools/web-sync/gen_tokens.py check    --tokens <web>/frontend/design/tokens.json   # exit 1 and "stale: <file>" on drift
```

`scripts/check-web-sync.sh <web git dir>` runs this check with the string check against the pinned commit (CI does too). The bump procedure is
in [Localization](Localization.md#web-synced-strings-generators).

## Components

| Component | Use |
|---|---|
| `TpButton` | Variants Primary, Secondary, Tonal, Text, Destructive; `loading`, `leadingIcon` |
| `TpChip`, `TpCard` | Filter chips; item/large cards with optional accent and click |
| `TpTopBar` | For non-top-level screens (the shell draws the bar for top-level ones) |
| `SkeletonBlock`, `SkeletonList`, `Modifier.shimmer()` | Loading placeholders; shimmer is static under reduce-motion (`rememberReduceMotion()`) |
| `LoadingIndicator`, `EmptyState`, `ErrorState` | Every network screen needs loading, empty and error-with-retry |
| `SectionScaffold` (`core:ui`) | Accent bar, heading semantics, expand/collapse state description |
| `VerificationChip`, `PriceText`, `BookingLinkButton`, `AddToPlanButton`, `StreamingStatusAnnouncer` (`core:ui`) | Grounded display of backend data |

Icons: `material-icons-core` only (the extended set adds megabytes to debug builds). Add a vector drawable to your module for anything else.
Every composable that ships should have light and dark previews.
