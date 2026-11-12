# Android Phase 3-6 parity (alerts 2.0, sharing, notifications, social + play)

> **For agentic workers:** use superpowers:subagent-driven-development. Ledger: `.superpowers/sdd/` (git-ignored). Author and committer: `rupakc <4364388+rupakc@users.noreply.github.com>`. One PR per task, each in its own worktree; merge when `gh pr checks <n> --watch --fail-fast --interval 60` exits 0.

**Goal:** the Android app adopts the frozen web contracts: `Alerts-API.md` (P3), `Sharing-API.md` (P4), `Notifications-API.md` (P6), `Social-API.md` (P5), all in `~/travel-planner/docs/`. Order: P3, P4, P6, P5. Scope and web rulings: `~/travel-planner/docs/superpowers/plans/2026-11-12-phase{3,4,5,6}-*.md`, their follow-ups, and `docs/Feature-Parity.md`.

**Task 0 (done first, separate PR):** the four top Phase 1+2 follow-ups (cold-start offline cache of trips and expenses, Today "Add expense" no-op, stale trips list after Back, Recap tab state after a phase change).

## Global constraints

- **Mixed versions.** Read `GET /config` `features`: `alerts_api` (2), `price_alerts_v2`, `live_prices`, `sharing` (0/1), `notifications_api`, `notifications_v2`, `social`, `play` (0/1). Never call a route whose flag is absent or 0; an older backend gets today's screens. Unknown JSON keys, kinds and categories are ignored; unknown share `kind` is a plain link.
- **Server decides.** The app never recomputes verdicts, advice codes, savings or scores, and never calls SerpAPI or any price source. It maps server codes and numbers to localized copy. Golden fixtures are used as parse and render fixtures (copied into `core:testing` resources).
- **Wording.** "Caught a drop of $X", never "saved". Every price shows its age (`checked_days_ago`); `checks_paused` says live checks are paused; an estimate is labelled "estimate" and never counts. No currency conversion: `total_usd` plus `by_currency` lines.
- **hide_game** (setting, or `features.game` false) hides XP, badges, levels, boards, Play and any game wording, including in notifications.
- **Credentials do not exist** (SerpAPI, FCM, OAuth, SMTP). Everything config-gated and inert; tests use fakes (MockWebServer, fake repositories, a fake FCM payload).
- **Strings** in all 7 languages (en, es hand; de/fr/hi/zh/bn via `strings_web.xml` where a web key exists, else hand), per `CLAUDE.md` and `docs/Localization.md`. Server error codes (listed per contract) are translated; backend `detail` is the fallback.
- **Telemetry** through the existing SDK, event names and enum props exactly as the web registry in `~/travel-planner/docs/Telemetry.md` (`alerts_*`, `share_*`, `notif_*`, `social_*`, `play_*`); no PII, no handles, ids or text; every control gets a tid.
- **Privacy.** No location is sent to any new endpoint or telemetry.
- **A11y and adaptive** per `CLAUDE.md`. Each network screen: loading, empty, error-with-retry; 429 shows the backend message. Light default, dark supported.
- **Local data** is per user and cleared by `SessionCleaner` on sign-out.
- **Each task:** tests first, then `spotlessApply`, `detekt`, module `:testLocalDebugUnitTest`, `lintLocalDebug`; the PR also touches `docs/` pages it affects (`Notifications-and-Push.md`, `Play.md`, `Deep-Links.md`, `Telemetry-and-Privacy.md`, `Localization.md`) and `MEMORY.md` for each ruling.

## Rulings

| # | Ruling | Why | Cost if wrong |
|---|---|---|---|
| R1 | Shared DTOs, APIs, repository interfaces, fakes and flag reads land once per phase in the phase's first task (`core: ...` commits), so UI tasks own disjoint files | avoids merge conflicts between parallel tasks | rebase churn |
| R2 | The price story chart is drawn with Compose Canvas (no chart library); scrubbing by touch and TalkBack steps, first/latest jump actions | no new dependency; a11y | a library swap later |
| R3 | One-tap watch and suggestions appear only when `live_prices` is true and `price_alerts_v2` or `alerts_api >= 2`; otherwise stories, savings and the digest still show from stored data | contract client notes | none |
| R4 | Drop moment: show the notification's `game.xp` total only when game is not hidden; it is the notification's number, not a client sum | web follow-up leaves it open; the server value is the truth | a copy tweak |
| R5 | Sharing: download the card PNG (`size=story` for chat apps, `link` for the preview) with the existing OkHttp client to cache, expose via a `FileProvider`, and launch the system share sheet with image plus link text; if the download fails share the link only. Instagram Stories and TikTok Share Kit are out (need developer apps) | contract defers them; owner credentials | a later add-on |
| R6 | Opening share links: non-autoVerify `https://<host>/share/<token>` filter plus a paste-a-link box, like verify/reset links (R3 of Phase 1+2); the in-app view reuses `feature:share` `GET /share/{token}`; `assetlinks.json` stays an owner action | no release cert yet | chooser dialog shown |
| R7 | Notification settings screen lives in Settings > Notifications; it reads `GET /notification-settings` (flag `notifications_api`) and shares quiet hours with alert prefs (same value on the server, so one UI edits both and refetches). The `nearby` category is a local switch honoured by the on-device notifier, also sent to the server | contract | none |
| R8 | Push handling: unknown `kind` shows nothing special (the default channel), `category` picks the channel, a `trip_*` tap opens the trip's Today tab via `plan_id`, an empty `plan_id` opens the notification list; the web `link` path is parsed only through `DeepLinkIntents.sanitize`-style allow-listing | Phase 6 follow-up asked to confirm this | a wrong screen on tap |
| R9 | "Near you" text: built on-device from `GET /notifications/catalog?locale=` (cached per locale, bundled English fallback) plus the existing `NearbyApi` result already used by the nearby module; the server receives no location beyond that existing call and telemetry carries none | contract privacy section | none |
| R10 | New module `feature:social` (profile, follows, blocks, feed, ideas, comments, reports); Play stays in `feature:play` (quiz, head-to-head, boards, challenge). Entry points: You tab (profile, My shares), Explore (feed), Play tab. Admin moderation queue UI is a non-goal (like admin), the API models exist only if needed | module rules; admin non-goal | a later module |
| R11 | Social text is rendered as plain `Text` only (no links, no markdown); report reasons and the held-content notice are enum-mapped copy; all of social and play is hidden when `features.social`/`play` is 0, and Play also when `hide_game` | contract | none |
| R12 | Quiz answers are sent immediately and timed by the server; the app shows a countdown for feel only and never computes points | contract | none |
| R13 | Quiz and status shares reuse the Phase 4 share flow (`kind: quiz` with `session_id`, `kind: status` with `plan_id`); names hidden unless `show_name` | Social-API | none |
| R14 | Contract gaps found while building are logged in the ledger and the follow-ups doc, not worked around by guessing fields | contracts are frozen | none |

## Phase 3: price alerts 2.0 (`feature:alerts`, `core:*`)

- **T3.1 Core.** DTOs and `AlertsV2Api` (summary, story, advice, quick-watch, suggestions, preferences), `Lenient*` numbers, `PendingFirstCheck` on watches, notification `drop` and `game` data and `price_digest`, config flags, interfaces and fakes. Parse tests from the contract examples and the golden fixtures.
- **T3.2 Price story + advice** (needs T3.1). Story card per watch: Canvas chart of `points`, typical band, verdict chip, advice line from `advice.code` with params (8 codes, `checks_paused` wording), "Checked N days ago", estimate label, pending state, AI tip button (`422 advice_*`, `502 advice_failed`, `503 ai_limit|ai_billing` messages).
- **T3.3 Savings, drop moment, digest** (needs T3.1). Savings tally header with progress to `next_milestone`, "caught a drop of $X" drop card from `data.drop` (target-hit variant, game line per R4), `price_digest` rendering, the "live checks paused" banner when `live_prices` is false, notification list handling of pending and unknown rows.
- **T3.4 One-tap watch, suggestions, prefs** (needs T3.1). Watch button on result cards and plan items (existing/pending/limit/off states: `live_prices_off`, `watch_limit`), suggestions sheet (`reason` copy), alert preferences (digest weekly/push/day, instant, quiet hours, caps, group window, read-only timezone with `alert_prefs_invalid` handling).

## Phase 4: sharing (`feature:share`, `core:*`)

- **T4.1 Core + share dialog.** `SharesApi` (`POST/GET /shares`, `DELETE /shares/{token}`), flag `sharing`, options (dates hidden/month/exact, show origin, show name; private defaults), share sheet per R5, entry points from plan, recap and savings; without the flag the legacy plan share stays. Errors `sharing_disabled`, `plan_not_found`, `invalid_kind`, 429.
- **T4.2 My shares** (needs T4.1). List (incl. `legacy_plan_shares`), copy, share again, revoke with confirm and Undo-free wording, empty and error states; reachable from You.
- **T4.3 Open share links** (needs T4.1). Per R6; public view for plan, recap, savings and unknown kinds (plain link); `DeepLinkIntents` tests; `docs/Deep-Links.md`.

## Phase 6: notifications (`feature:settings`, `core:notifications`)

- **T6.1 Core + settings.** `NotificationSettingsApi`, catalog API with per-locale cache, flag `notifications_api`; Settings > Notifications (category switches, quiet hours, daily cap, timezone display, push status/devices, `notification_settings_invalid`); `devices` registration keeps `platform: android`.
- **T6.2 Push + rows** (needs T6.1). `category` and `plan_id` in push data and rows, channels per category, `trip_t30|t7|t1|morning` rows and taps per R8, grouped push `group_count`, hide_game wording, config-gated FCM (a fake payload test; inert without `google-services.json`).
- **T6.3 Near you** (needs T6.1). On-device notification per R9, honours the `nearby` category and quiet hours locally, no location in telemetry or new calls.

## Phase 5: social and play (`feature:social`, `feature:play`)

- **T5.1 Core.** DTOs, `SocialApi`, `PlayApi`, interfaces, fakes, flags (`social`, `play`), catalog rows for `social_*` and `quiz_*` notification kinds and their link mapping (`/u/{handle}`, `/feed/{id}`, `/play/h2h/{id}`), new `feature:social` module via the add-feature-module skill.
- **T5.2 Profile, follows, blocks** (needs T5.1). Create profile (handle check, once per 30 days, opt-in toggles default off, visibility, bio), public profile view (optional fields only when present), follow/pending/accept/decline, followers/following, block list, report profile.
- **T5.3 Ideas feed** (needs T5.2). Feed scopes, publish from a plan or recap (`profile_required`, `email_unverified`, `held` notice, `limit_reached`), like, save as remix (new plan), comments with helpful, delete, report (reasons), blocked and hidden handling.
- **T5.4 Quiz** (needs T5.1; parallel with T5.3). Play state, daily and pack quiz, answer flow per R12, summary with streak and XP (hide_game aware), history, `409 already_done`, resume.
- **T5.5 Head-to-head, boards, challenge** (needs T5.4 and T5.2 for handles). h2h create/accept/decline list states, global and friends leaderboards with opt-in, weekly challenge, `403 game_hidden`.
- **T5.6 Shares and links** (needs T4.1 and T5.4). Quiz and status shares (R13), notification taps for social and quiz kinds.

## Finish

- **F1** One opus whole-branch review of everything merged since the Task 0 merge; fix findings.
- **F2** `scripts/build-release-apk.sh` (clean, no cache), install on the emulator against the local backend with the fake SerpAPI (`python -m scripts.run_with_fake_serpapi`, `--drop-after`), sign in, and run one flow per phase: a watch drop, a share, notification settings, a profile plus daily quiz.
- **F3** `docs/superpowers/plans/2026-11-13-android-phase3-6-followups.md`.
- **F4** Small `~/travel-planner` PR updating `docs/Feature-Parity.md` (Android column for Phases 3-6).

## Waves (max 3 agents at once, Sonnet implementers and reviewers, Haiku re-reviews)

P3: T3.1, then T3.2 / T3.3 / T3.4 in parallel. P4: T4.1, then T4.2 / T4.3. P6: T6.1, then T6.2 / T6.3. P5: T5.1, then T5.2 and T5.4, then T5.3 and T5.5, then T5.6. Phases overlap only when file sets are disjoint.
