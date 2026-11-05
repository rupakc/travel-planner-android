# Onboarding and traveller types

How the app learns how someone travels (spec §6) and what it does with it. The code is the
source of truth; this page mirrors it and must change in the same PR as the table.

| What | Where |
|---|---|
| Questions, options, weights, Preferences mapping (one table) | `core/domain/…/profile/TravellerQuiz.kt` |
| Classifier | `core/domain/…/profile/DefaultTravellerClassifier.kt` |
| Feature catalogue + visibility helpers | `core/domain/…/profile/DefaultFeatureCatalogue.kt` |
| Answers → server Preferences merge | `core/domain/…/profile/PreferencesMapping.kt` |
| Type / feature names, descriptions, icons (en + es) | `core/ui/…/profile/ProfileUi.kt`, `core/ui/src/main/res/values*/strings_profile.xml` |
| Hilt bindings (`FeatureCatalogue`, `TravellerClassifier`) | `feature/onboarding/…/di/ProfileDomainModule.kt` |
| Flow UI | `feature/onboarding` |

## Flow

```
Welcome → Q1 … Q6 → Result → Feature picker → (Notification rationale) → Home
             ↑ Back / Skip / Next      "Not you? Change answers" → Q1
```

- **When:** after the first successful sign-in on a device (the auth gate shows it while
  `TravellerProfile.onboardingCompleted` is false), and from Settings → "Retake quiz".
- **Questions:** one per screen, "Step n of 6" with a segmented bar, Back, Skip (clears that answer),
  Next (enabled once something is chosen; the last one reads "See my type"). Single-choice options
  are radio buttons, multi-choice options are checkboxes; each card announces "Selected" / "Not selected".
- **Result:** "You're a {primary}" with its description, then "Also a bit of" for each secondary type.
- **Feature picker:** "Recommended for you" (on by default for the result types) and "More features",
  every row a labelled switch; "You can change these any time in Settings."
- **Finish** (in this order):
  1. `TravellerProfile` (types + answers) and `FeatureOverrides` saved to DataStore;
  2. `PUT /api/preferences` with the merged answers (read-modify-write). On failure the screen shows
     the backend `detail` (or our copy) with **Retry** and **Continue without syncing** — local data is kept;
  3. first run on Android 13+ without the permission: a rationale screen, then the system
     `POST_NOTIFICATIONS` dialog ("Allow notifications") or "Not now";
  4. first run: `onboardingCompleted = true` (+ time) → the auth gate opens Home.
- **State:** stage, question, answers, toggles and the seeding basis live in `SavedStateHandle`, so
  rotation and process death resume where the user was.

### Edit mode (Settings → "Retake quiz")

Navigate to `OnboardingRoute(fromSettings = true)`. Differences from the first run:
starts with the saved answers and the current feature toggles; a ✕ in the top bar leaves without
saving; the last button reads "Save changes"; no notification rationale; `onboardingCompleted`
and its time are left alone; the graph's `onFinished(true)` is called (the app pops back to Settings).

## Questions and scoring (the table)

Score per type = sum of the weights of the chosen options. Unknown ids are ignored, duplicates count
once, and a single-choice question only counts its first valid answer.

| # | Question id (choice) | Option id | Weights | Preferences / search |
|---|---|---|---|---|
| 1 | `companions` (single; counts apply only to the default party) "Who do you usually travel with?" | `solo` | BUDGET_BACKPACKER 2, ADVENTURE 1 | adults 1, children 0 |
| | | `partner` | RELAXATION 2, LUXURY 1, CULTURE_FOODIE 1 | adults 2, children 0 |
| | | `family_kids` | FAMILY 4 | adults 2, children 1, interest `family` |
| | | `friends` | ADVENTURE 2, BUDGET_BACKPACKER 1 | adults 3, children 0 |
| | | `work` | BUSINESS 4 | adults 1, children 0 |
| 2 | `budget` (single) "What's your typical budget?" | `shoestring` | BUDGET_BACKPACKER 3 | budget_category `low` |
| | | `mid_range` | CULTURE_FOODIE 1, FAMILY 1 | `medium` |
| | | `comfort` | RELAXATION 1, BUSINESS 1 | `medium` |
| | | `luxury` | LUXURY 4 | `high` |
| 3 | `interests` (multi) "What do you love doing?" | `food` | CULTURE_FOODIE 2 | `food` |
| | | `history_culture` | CULTURE_FOODIE 2 | `history`, `culture` |
| | | `nature` | ADVENTURE 1, RELAXATION 1 | `nature` |
| | | `adventure` | ADVENTURE 2 | `adventure` |
| | | `nightlife` | BUDGET_BACKPACKER 1 | `nightlife` |
| | | `shopping` | LUXURY 1 | `shopping` |
| | | `art_museums` | CULTURE_FOODIE 1 | `art` |
| | | `beaches` | RELAXATION 2 | `nature`, `wellness` |
| | | `photography` | ADVENTURE 1, CULTURE_FOODIE 1 | `art` |
| | | `wellness` | RELAXATION 1, LUXURY 1 | `wellness` |
| 4 | `pace` (single) "What pace do you like?" | `relaxed` | RELAXATION 2, FAMILY 1 | search `pace` = `relaxed` (device only) |
| | | `balanced` | CULTURE_FOODIE 1 | `balanced` |
| | | `packed` | ADVENTURE 1, BUSINESS 1, BUDGET_BACKPACKER 1 | `packed` |
| 5 | `planning` (single) "How do you plan?" | `ahead_deals` | BUDGET_BACKPACKER 1, FAMILY 1 | — |
| | | `loose` | CULTURE_FOODIE 1, RELAXATION 1 | — |
| | | `spontaneous` | ADVENTURE 1, BUDGET_BACKPACKER 1 | — |
| 6 | `needs` (multi) "Anything we should plan around?" | `wheelchair` | — (adds ACCESSIBLE) | accessibility `wheelchair` |
| | | `visual_impairment` | — (adds ACCESSIBLE) | `visual_impairment` |
| | | `hearing_impairment` | — (adds ACCESSIBLE) | `hearing_impairment` |
| | | `cognitive_disability` | — (adds ACCESSIBLE) | `cognitive_disability` |
| | | `infants` | FAMILY 3 | infants ≥ 1 |
| | | `dietary` | — | — (no server field) |

### Classification rules

1. **Primary** = the type with the highest score.
2. **Ties** are broken by the fixed `TravellerType` order: BUDGET_BACKPACKER, FAMILY, LUXURY,
   ADVENTURE, CULTURE_FOODIE, BUSINESS, RELAXATION (ACCESSIBLE never scores).
3. **Secondary** = the runner-up (same tie order) when its score ≥ **60 %** of the primary's
   (integer check `second × 100 ≥ primary × 60`). At most one scored secondary.
4. **ACCESSIBLE** is appended as a secondary whenever any of the four accessibility needs is chosen;
   it never replaces or hides another type.
5. **Nothing scored** (everything skipped, or only "Dietary needs"/accessibility answers):
   primary = **CULTURE_FOODIE** (the broad "see and taste the place" traveller), no scored secondary;
   the result screen says so and points to Settings.

`TravellerProfile` stores `primaryType` and `secondaryTypes` as `TravellerType.name` strings and the
raw `answers` (question id → option ids); `TravellerProfile.types()` reads them back.

## Server Preferences merge (`Preferences.mergeQuizAnswers`)

`PUT /api/preferences` replaces every field, so the app always reads first and merges:

- `interests`, `accessibility_needs`: union (existing first), only the web's ids
  (interests `food, history, adventure, culture, nature, shopping, nightlife, wellness, art, family`;
  accessibility `wheelchair, visual_impairment, hearing_impairment, cognitive_disability`). Nothing is removed.
- `budget_category`: set when question 2 is answered (`shoestring` → low, `mid_range`/`comfort` → medium, `luxury` → high).
- `adults`/`children`: filled from question 1 **only while the server still has the default party** (1 adult, 0 children/seniors/infants);
  a party the user already set (e.g. 2 adults + 3 children, or solo + 1 senior) is left untouched. `seniors` is never changed.
- `infants`: raised to at least 1 for "Travelling with infants" (a minimum, on any party).
- `num_travelers` = adults + children + seniors + infants, clamped 1…20, only when a count changed.
- Everything else (nationality, residence, permits, visas, email) is kept. There is no nationality question.
- Pace has no Preferences field: it stays in `TravellerProfile.answers["pace"]` (`quizPace(answers)`) for the search form.

## Feature catalogue and visibility

Visible set = defaults for the profile's types ∪ user-enabled − user-disabled (`FeatureCatalogue.visible`).
Overrides are `FeatureId.id` strings in DataStore (device-local; the server has no field). Core surfaces —
Search, Results, My Plan, Settings (`CoreSurface`) — are not in the catalogue and are never hidden.
Before onboarding (no type yet) `visibleFeatures(profile, overrides)` shows every feature except explicit
disables. `overridesFor(types, wanted)` turns a picked set into minimal overrides.

| Feature id | Title | Icon name | On by default for |
|---|---|---|---|
| `chat` | Chat | chat | every type |
| `visa_checker` | Visa | verified | every type |
| `deal_alerts` | Deal alerts | notifications | BUDGET_BACKPACKER, FAMILY, BUSINESS |
| `best_value` | Best-value package | thumb_up | BUDGET_BACKPACKER, FAMILY |
| `budget_planner` | Budget planner | wallet | BUDGET_BACKPACKER, FAMILY |
| `compare` | Compare | compare | BUDGET_BACKPACKER, CULTURE_FOODIE, RELAXATION, LUXURY |
| `surprise_me` | Surprise me | surprise | BUDGET_BACKPACKER, ADVENTURE, CULTURE_FOODIE, RELAXATION |
| `import_link` | From a link | link | ADVENTURE, CULTURE_FOODIE, LUXURY |
| `near_me` | Near me | near_me | BUDGET_BACKPACKER, ADVENTURE, CULTURE_FOODIE, BUSINESS |
| `itinerary_editor` | Itinerary editor | edit_calendar | FAMILY, LUXURY, CULTURE_FOODIE, BUSINESS, RELAXATION |
| `walking_tours` | Free walking tours | walk | BUDGET_BACKPACKER, CULTURE_FOODIE |
| `events` | Events | event | BUDGET_BACKPACKER, LUXURY, CULTURE_FOODIE |
| `packing_list` | Packing list | luggage | FAMILY, ADVENTURE, RELAXATION, ACCESSIBLE |
| `emergency_card` | Emergency card | emergency | FAMILY, ADVENTURE, ACCESSIBLE |
| `trip_health_check` | Trip health check | health | FAMILY, LUXURY, BUSINESS, ACCESSIBLE |
| `forex` | Forex | currency | BUDGET_BACKPACKER, LUXURY, BUSINESS |
| `sim` | SIM cards | sim | BUDGET_BACKPACKER, ADVENTURE, BUSINESS |
| `accessibility_info` | Accessibility info | accessibility | ACCESSIBLE |

Row order is the display order (Home tiles, Settings → Features, the picker). Titles/descriptions are
string resources `feature_<id>_title` / `feature_<id>_description` (`FeatureId.titleRes()` /
`descriptionRes()`); icons resolve with `FeatureDescriptor.icon()` (material-icons-core only).

## Traveller types

| Type | Name (en) | One-liner |
|---|---|---|
| BUDGET_BACKPACKER | Budget Backpacker | Stretching every dollar: hostels, street food, free walking tours and deal alerts. |
| FAMILY | Family Traveller | Trips that work for everyone, with easy pacing, packing lists and safety info. |
| LUXURY | Luxury Traveller | Premium hotels, fine dining and private tours. |
| ADVENTURE | Adventurer | Outdoors, active days and room for the unexpected. |
| CULTURE_FOODIE | Culture Seeker & Foodie | History, art, local food and what's on in town. |
| BUSINESS | Business Traveller | Efficient trips with flights, connectivity and money sorted. |
| RELAXATION | Relaxation Seeker | Beaches, wellness and slow days. |
| ACCESSIBLE | Accessible Traveller | Plans that account for your accessibility needs. |

## Tests

- `core/domain`: `TravellerClassifierTest` (every type reachable, ties, 60 % rule, ACCESSIBLE, defaults,
  unknown ids, determinism), `FeatureCatalogueTest`, `PreferencesMappingTest` (web ids only, merge never wipes).
- `core/ui`: `ProfileUiTest` (every icon/string resolves, no duplicates).
- `feature/onboarding`: `OnboardingViewModelTest` (navigation, persistence, sync failure, edit mode,
  process death), `OnboardingScreenTest` (Next/Back/Skip, multi-select semantics, result, switches, rationale),
  `QuizCopyTest` (copy covers the table).
