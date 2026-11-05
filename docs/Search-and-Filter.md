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

Saved plans (My trips) and watches support text search and filters over the locally cached lists (Room for plans), so they work offline.
Planned - lands with the plan and alerts teams; this page is updated in the accuracy pass.
