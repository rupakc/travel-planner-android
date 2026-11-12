# Play (game)

`feature:play` shows `GET /game/me` and `GET /game/catalog` (cached 5 minutes): level and XP bar, badges (tier colour plus text, progress, earned date), the `daily` and `trip_checkin` streaks, the last awards, and, when trip stats load, countries visited and the bucket-list goal.

- A `404` or `hidden: true` on `/game/me` hides the screen (`PlayRoute(onHidden)`) and silences celebrations and `game_*` telemetry (`GameEventsImpl.setVisible`).
- Every repository that receives a `game` field calls `GameEvents.publish(delta)`; the shell collects `GameEvents.deltas` and feeds `GameDeltaHost` (`core:ui`): an XP snackbar per award, then a sheet for the level-up and each badge. Under "remove animations" the sheets are plain dialogs.
- Names come from `core:ui` `strings_game.xml` (`game_badge_<id>_name|desc`, `game_rule_<id>`, `game_level_<n>`), copied from the web `game` namespace; an unknown id shows its words ("Mystery badge"). The web pin predates `game.json`, so these files are hand-maintained, not generated.
- The Trips section needs a bound `TripsRepository` (`@BindsOptionalOf` in `GameDataModule`) and a successful `/trips/stats`; otherwise it is hidden.
- Telemetry: `game_view{surface: nav|celebration}`, `game_award`, `badge_earned`, `level_up`.
