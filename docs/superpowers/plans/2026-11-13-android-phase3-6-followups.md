# Android Phase 3-6 follow-ups

Shipped (PRs #94-#111 plus the fix PRs after the smoke test): Task 0 (offline trips and expenses cache, Today "Add expense", stale hub, Recap tab), Phase 3 alerts 2.0 (price story, savings and drop moment, digest, one-tap watch, suggestions, alert prefs), Phase 4 sharing (share dialog and sheet, My shares, open share links), Phase 6 notifications (settings, catalog, category pushes and taps, on-device near-you text), Phase 5 social and play (profile, follows, blocks, ideas feed, quiz, head-to-head, boards, challenge, quiz and status shares). Everything is flag-gated and inert on an older backend.

## Owner actions
- Release signing key (APKs are debug-signed), `.local` client in `google-services.json` (the `localRelease` build fails without it), FCM credentials (all pushes are inert until then), SerpAPI key on the backend for live prices and one-tap watch, `/.well-known/assetlinks.json` with the release cert so share, verify and reset links open without a chooser.
- Decide: should "near you" use a non-grounded `/nearby` answer with walking minutes? Google-Maps-grounded `/nearby` v2 forbids scheduled calls, so today the notification posts the generic text (`NearbyArrivalSource` is unbound).
- Native-speaker review of the new hi, bn, zh strings (price story, share, notifications, social, play).
- Web: extend the telemetry registry with the Android-only prop values if any are dropped; add the `currency` field of digest watches to `Alerts-API.md`.

## Engineering
- Home shows no top or bottom bar right after sign-in until relaunch (open since the Phase 1+2 smoke test).
- The quiz accepts an answer at "0 seconds left" (the server times answers; the countdown is cosmetic).
- No watch button on My Plan items or chat cards, and no plan-scoped suggestions entry (`QuickWatcher.watch(planId, surface)` is ready).
- Share: no card preview and no "Download card" button on Android (the share sheet covers saving); a recap shares `recap.tripId` as `plan_id` (the contract is silent); the unknown-kind "Open in browser" is hidden when the build has no `WEB_BASE_URL`; the classic shell has no entry to the paste-a-link screen; a `YYYY-MM` plan date is not shown on the poster.
- Head-to-head: no single-match route (the match screen finds its match in the list); a creator who leaves mid-quiz re-enters through "Play now", which the server may refuse (`H2hMatch.sessionId` is not in the contract).
- Review leftovers: the Quiz notification channel is still created while the game is hidden; `PublicProfileViewModel` ignores `features.game`; the head-to-head screens rely on the server `403 game_hidden` for the `hide_game` setting; the morning-brief row shows off when the local trip switch is off but its own toggle does not change that switch.
- Price story chart and bottom sheet were smoke-tested only through a flow, not for drag feel or large font scale.
- Alerts-API omits `currency` in digest watches (the backend sends it; USD is the fallback).
- Early T6.1 commits carry a Sonnet trailer instead of Opus (not rewritten: merged history).
- Several PRs conflicted repeatedly on `MEMORY.md` and `TelemetryTypes`; consider one file per entry for the decision log. CI takes 20-30 minutes per PR.
- `web.sha` pin is still not bumped; the web `play` namespace copy was taken from a web worktree rather than generated.
