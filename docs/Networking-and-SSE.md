# Networking and SSE

## Base URL and flavours

Requests are built against a placeholder host (`http://travelplanner.invalid/`). `BaseUrlInterceptor` rewrites them to the effective
base URL: the Settings -> Server override when set, otherwise the flavour default (a base path prefix is kept).

| Flavour | Default | Cleartext |
|---|---|---|
| `local` | `http://10.0.2.2:8001` (emulator to host backend) | allowed (`src/local/res/xml/network_security_config.xml`) |
| `prod` | `-PPROD_BASE_URL=https://...` build property, no host baked in otherwise | HTTPS only (`src/prod/...`) |

The cleartext policy is fixed at build time by Android, hence two flavours. `BaseUrlProvider.validate` rejects `http` URLs in `prod`.
A phone on your LAN: Settings -> Server -> `http://<LAN-IP>:8001` (local flavour).

## Auth

`AuthInterceptor` adds `Authorization: Bearer <token>` from the session store. A 401 for a request that carried the *current* token
clears the session and emits `AuthEvent.SessionExpired`; the shell then shows Login with a snackbar. Stale 401s for a replaced token are ignored.
`requires_password_change` routes to Change password. Anonymous calls are allowed by the backend for search and single-agent routes.

## Clients and timeouts

| Client (qualifier) | Read timeout | Used for |
|---|---|---|
| `@DefaultHttp` | 30 s (connect 15 s) | plans, preferences, notifications, devices, auth, reference data |
| `@AiHttp` | 300 s | single agents, discover, compare, import, nearby, watch checks (agents can run 240 s) |
| `@StreamingHttp` | none (read and call timeouts off) | `/api/search`, `/api/chat` |

Debug builds log `BASIC` HTTP lines with the `Authorization` header redacted.

## SSE parsing rules

Implemented by `SseParser` (pure, unit-tested) over `data:` lines as the backend emits them ([api-contract](reference/api-contract.md) section 3.1):

- One event = one or more `data:` lines ended by a blank line; multiple `data:` lines are joined with `\n`.
- Lines starting with `:` are comments (keepalives) and ignored; `event:`, `id:`, `retry:` are ignored.
- A final event without its trailing blank line is still delivered at end of stream.
- Parsing and decoding run off the main thread. Failures cross the IO `flowOn` boundary as values (`SseItem.Failure`) and are rethrown in the collector, because throwing upstream cancelled the collector and dropped buffered events ([MEMORY](../MEMORY.md)).
- Search ends on `done`; chat ends on `done` **or** `planning_done`. Unknown event types decode to `Unknown` and are ignored.
- Search: section events replace earlier ones by type; the static (phase 0) result is kept if the AI result is an error. A stream that ends without `done` is retried once and replays into the sections already received.

## Errors

`HttpErrorMapper` maps status plus body into `AppError`: `detail` as string, list or `{code,message}`.

| Error | When | UI |
|---|---|---|
| `Unauthorized` | 401 | session cleared, Login |
| `RateLimited(message, retryAfterSeconds)` | 429 | show the backend string as-is; honour `Retry-After` |
| `Client` (status, code, fieldErrors, extras) | other 4xx incl. 422 `no_live_price` | feature-specific copy |
| `NotFound`, `Forbidden`, `Server` | 404, 403, 5xx | message or own string with Retry |
| `Network`, `Interrupted`, `Decoding`, `NotConfigured` | I/O, cut stream, bad JSON, no base URL | own strings |

Rate limits are per user (or IP when anonymous) in 10-minute windows; defaults (search 10, chat 30, compare 30, discover 10, light_ai 60) are in [Scaling](Scaling.md) and the backend's `docs/Configuration.md`.
Chat-triggered full plans over the search limit produce an in-stream message, not a 429.

## Retries

Only the SSE single retry above. There is no blind retry of non-idempotent calls; UI states offer an explicit Retry. Background
work uses WorkManager backoff ([Notifications-and-Push](Notifications-and-Push.md)).
