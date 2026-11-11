# Telemetry and privacy

The wire contract is owned by the web repo: [Telemetry](https://github.com/rupakc/travel-planner/blob/main/docs/Telemetry.md)
(events, props, limits, `GET /api/config`) and [Privacy](https://github.com/rupakc/travel-planner/blob/main/docs/Privacy.md)
(what is collected and why), with the registry in `backend/app/telemetry/registry.py`. This page only states what Android sends and when. No tracker SDK is used.

## What Android sends

| Event | When | Props |
|---|---|---|
| `app_open` | every time the process comes to the foreground | none |
| `app_background` | the app stops (pending events are persisted and the flush worker scheduled) | none |
| `session_start` | first event of a session (a new one, or after 30 minutes idle) | none |
| `screen_view` | a route change | `route` (template), `referrer_route` |
| `screen_time` | leaving a route or going to the background | `route`, `ms` |
| `ui_tap` | `TpButton`, bottom navigation and the social sign-in buttons | `tid`, `route` |
| `ui_toggle` | `TpChip`, and the Settings privacy switch (`tid: privacy.switch`, `on`) | `tid`, `route`, `on` |
| `theme_change` | the theme mode changes | `theme` (`system`, `light`, `dark`) |
| `language_change` | the app language changes | `lang` (`en es de fr hi zh bn`) |
| `consent` | the user grants (grant only; a denial sends nothing) | `choice: granted` |
| `perf` | `cold_start`, only for a process started in the foreground, for its first activity, not restored, and 60 s or less | `metric: cold_start`, `value` (ms), `route` |

`screen` and `route` are route templates (`/results`, `/plans/:id`, `/share/:token`), never ids, tokens or arguments. The web name is used where the screen is the same;
anything unmapped is `/other` (`core:domain` `TelemetryRoutes`). A prop value is a token (`^[A-Za-z0-9_.:/-]{1,64}$`), an integer, a finite number or a boolean.
`client` carries `anon_id`, `session_id`, `platform: "android"`, `app_version` and `locale` (`zh-CN`, `hi-IN`, `bn-IN` for those languages, the bare code for the others).
The body is gzipped (`Content-Encoding: gzip`; the web sends plain JSON), one session per batch.

## What it does not send yet

`ui_change`, `ui_submit`, `feature_used` and `error`. The first two come from web form autocapture and the third mirrors the web's `track()` calls; Android has no equivalent yet.
Crashes do not fit the registry's `js|promise|api` error kinds, so they are not reported; adding an `error` kind for native crashes is a backend-first follow-up.

`POST /api/analytics/events` (the older feature tracker) is skipped while telemetry is not allowed.

## Consent and the switch

- Consent is required when the device zone is in Europe (the web list of exceptions applies) or one of the listed Atlantic zones, evaluated at collection time (and again on `ACTION_TIMEZONE_CHANGED`). Until the user chooses, nothing is queued or sent. Elsewhere telemetry is on and can be switched off.
- A grant stores the choice and sends `consent {choice: "granted"}`. A denial is stored only on the device, clears the queue and sends nothing. Nothing survives a denial.
- The first-run prompt is a non-modal card in the app shell (`ConsentPrompt` in `core:ui`, hosted by `app`). **Privacy choices** reopen it from Login, the share viewer and Settings → Privacy, where the switch also lives.
- The policy link is `https://github.com/rupakc/travel-planner/blob/main/docs/Privacy.md`.

## Server and delivery

- `GET /api/config` is fetched once per process per server (a change in Settings → Server refetches). A 404, an error or `telemetry.enabled != true` turns telemetry off with no retries and drops anything queued.
  `flush_interval_s` is clamped to 1..300 and `max_batch` to 1..100. An older backend therefore means silence.
- The queue holds at most 500 events; events older than 7 days are dropped. After 3 failures in a row the client backs off up to 5 min; `Retry-After` is honoured (cap 1 h);
  a 4xx other than 408/429 drops the batch and a 404 on POST disables telemetry.
- The flush worker re-appends unsent events on retry, cancellation or failure; a batch already handed to the sender is dropped on cancellation (at most once, never twice).

## Storage

| What | Where |
|---|---|
| anon id, consent choice | DataStore preferences `telemetry`; survive sign-out; excluded from cloud backup and device transfer |
| pending events | `noBackupFilesDir/telemetry/queue.json` (`{"v":1,"events":[...]}`) |
| background delivery | WorkManager unique work `telemetry_flush` (network required, policy KEEP) |

## Code map

`core:model` `config/ClientConfig`, `telemetry/TelemetryWire`, `settings/ConsentChoice`; `core:domain/telemetry` the pure rules (consent, queue, session, send policy, props, routes, plus `TelemetryEvents` and `TelemetrySettings` in `TelemetryTypes`);
`core:data/telemetry` `Telemetry`, `TelemetryLifecycle`, `ConsentRepository`, `ClientConfigRepository`, the queue file, uploader and worker;
`core:network` `ClientConfigApi`, `TelemetryApi`, `AiLocaleInterceptor`; `core:designsystem` `UiEventSink`; `core:ui` `ConsentPrompt`.

## Follow-ups

- Web repo: the Android column of `docs/Feature-Parity.md` and the Android path in `docs/Privacy.md`.
- Play Data safety form (owner).
- Backend registry `error` kind for native crashes.
- Native-speaker review of the hi, bn and zh translations.
- Use `public_base_url` from `/api/config` (Phase 1).
