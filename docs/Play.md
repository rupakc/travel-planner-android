# Play (game)

`feature:play` shows `GET /game/me` and `GET /game/catalog` (cached 5 minutes): level and XP bar, badges (tier colour plus text, progress, earned date), the `daily` and `trip_checkin` streaks, the last awards, and, when trip stats load, countries visited and the bucket-list goal.

- A `404` or `hidden: true` on `/game/me` hides the screen (`PlayRoute(onHidden)`) and silences celebrations and `game_*` telemetry (`GameEventsImpl.setVisible`).
- Every repository that receives a `game` field calls `GameEvents.publish(delta)`; the shell collects `GameEvents.deltas` and feeds `GameDeltaHost` (`core:ui`): an XP snackbar per award, then a sheet for the level-up and each badge. Under "remove animations" the sheets are plain dialogs.
- Names come from `core:ui` `strings_game.xml` (`game_badge_<id>_name|desc`, `game_rule_<id>`, `game_level_<n>`), copied from the web `game` namespace; an unknown id shows its words ("Mystery badge"). The web pin predates `game.json`, so these files are hand-maintained, not generated.
- The Trips section needs a bound `TripsRepository` (`@BindsOptionalOf` in `GameDataModule`) and a successful `/trips/stats`; otherwise it is hidden.
- Telemetry: `game_view{surface: nav|celebration}`, `game_award`, `badge_earned`, `level_up`.

## Head-to-head, boards and the challenge (T5.5)

All of this needs `features.play >= 1` and a visible game (`hide_game` off); `403 game_hidden` or `play_disabled` hides every screen (the shell leaves the Play tab). Nothing here scores anything: points, ranks and winners are the server's.

- **Cards** on the Play tab (after the quiz hub): Head-to-head (with the open count from `GET /play/state`) and Leaderboards. The weekly challenge ("Know before you go") is the hub card: start (`POST /play/quiz {mode: challenge}`) or "done this week".
- **List** (`GET /play/h2h`): sections Your turn, Waiting for them, Finished, Declined, Expired (unknown states are not shown). Scores and the winner show only for a finished match where the server sent both scores.
- **Create** (`POST /play/h2h`): opponent handle (trimmed, lowercase, no `@`, `3-20` of `[a-z0-9_]`) plus a pack. The creator plays at once: the created session is fetched by id (`GET /play/quiz/{id}`) by the quiz screen (`QuizDestination(mode = h2h, sessionId)`), so a retry never creates a second match. `422 limit_reached`, `422 cannot_challenge_self`, `restricted`, `profile_required` and `email_unverified` have their own text; a missing, private or blocking opponent (`404 profile_not_found`) all say the same "can't be challenged" line (nothing about whether the handle exists).
- **Match screen** (no single-match route, so it is found in the list): invite = Play now (`accept`, then the quiz on the returned session) or Decline; waiting; result with the winner; declined; expired. `410 match_expired` and `409 invalid_state` reload the match.
- **Leaderboards** (`GET /play/leaderboard`): Global or Friends, this or last week, handles only (level only when the server sends it), own rank/score, an opt-in card on the global board when the caller is not opted in (button opens the profile, where `leaderboard_opt_in` is edited; hidden when social is off), and a "create a profile" line when `you` is null.
- **Notifications**: `quiz_h2h_invite|result` push and inbox rows open the match (`Deep-Links.md`).
- Telemetry: `play_h2h_create`, `play_h2h_open`, `play_h2h_result{winner}`, `play_board_view{scope}`, `play_challenge_view`; no handles, ids or text.
