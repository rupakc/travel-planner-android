# Search and filter

## Search form modes

The Search screen has four modes (`SearchModes` in `core:model`): `known` (destination), `surprise` (Surprise me, `POST /api/discover`),
`compare` (2-3 destinations, one `POST /api/compare/destination` each in parallel) and `import` (From a link, `POST /api/import/extract`).
Chat can open Search with a mode or an import URL through `SearchRoute(mode, importUrl)`. Visible modes follow the user's feature visibility
([Onboarding-and-Traveller-Types](Onboarding-and-Traveller-Types.md)).

Typeaheads: `GET /api/airports/search` and `GET /api/nationalities/search` (`SearchRepository.airports` / `nationalities`).
The request model is `TravelSearchRequest` ([api-contract](reference/api-contract.md) section 1).

## Results filters

Flights, hotels and activities have server-side filter endpoints (`/api/flights|hotels|activities/filtered`, `SearchRepository.flightsFiltered` etc.)
using `FlightFilters`, `HotelFilters`, `ActivityFilters`. Filtered sections carry the same `verification` object as streamed ones.
The budget planner is client-side and checks already-loaded results; only "Find cheaper hotels" calls the API.

## Searching lists in the app

All of these search the lists already on the device, so they work offline and never call the API:

- **Global search** (top bar, `GlobalSearchRoute`; logic in `core:domain` `globalsearch/GlobalSearch.kt`): one box over saved trips,
  chat threads, recent searches, features and Settings entries, ranked and grouped by kind, with filter chips that show each kind's count.
- **My trips** (`feature:plan`, `TripQuery`): text search over name and destination (accent- and case-insensitive), Upcoming / Past / All,
  "Shared only", and sort by trip date or last update, over the Room-cached plans.
- **Alerts** (`feature:alerts`): kind tabs (`AlertTab`: All, Flights, Hotels, Activities, Trips) plus text search over each watch's
  route, airport codes, title and subtitle, over the loaded watches.
- **Chat threads** (`feature:chat`): search by title and by message text (Room `LIKE` over the user's own threads).
