# Design system and theming

`core:designsystem` holds tokens and components; feature code uses only these (no hard-coded colours or dp). The look follows the web: teal
primary, Sora for display and headlines, Inter for body, an accent colour per section.

## Tokens

| Token | Accessor | Notes |
|---|---|---|
| Colours | `TravelTheme.colors` (M3 `ColorScheme`) | `LightColors` / `DarkColors` in `theme/Color.kt`; palette constants in `theme/Palette.kt` (Teal, Sky, ...) |
| Extended colours | `TravelTheme.extendedColors` | `success`, `warning` (Estimate, over budget), `info`, skeleton colours and containers |
| Typography | `TravelTheme.typography` | Sora display/headline, Inter title/body/label via Google downloadable fonts, system fallback (`theme/Type.kt`) |
| Shapes | `TravelTheme.shapes`, `TravelShape` | Input 8, ItemCard 12, LargeCard 24, Pill |
| Spacing | `TravelTheme.spacing` | xxs 2, xs 4, sm 8, md 12, lg 16, xl 24, xxl 32, gutter 16, minTouchTarget 48, accentBar 4 (dp) |
| Section accents | `TravelTheme.sectionAccents[SectionId]` | `SectionAccent(bar, icon, tint, text)`: `text` (light `-700`, dark `-300`) for words in the accent colour |

## Changing the brand

- Colours: edit the constants in `theme/Palette.kt` and the two schemes in `theme/Color.kt`; section accents are in `theme/SectionAccent.kt`.
  Run `:core:designsystem:testLocalDebugUnitTest`: `ContrastTest` fails on any text pair under 4.5:1 or border under 3:1.
- Typography: change the font families in `theme/Type.kt`. Fonts come from the Google Fonts provider; the certificates ship under our own
  names (`tp_google_fonts_certs`). For a bundled font, add it under `res/font` and swap the `FontFamily`.
- Never reference colours by literal in features; add a token instead.

## Dark theme and dynamic colour

`TravelTheme(themeMode, dynamicColor)` is applied once at the app root. `ThemeMode` is SYSTEM, LIGHT or DARK (Settings -> Appearance).
Dynamic colour (Android 12+, off by default) replaces the M3 scheme only; extended colours and section accents stay brand colours so the
sections keep their identity.

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
