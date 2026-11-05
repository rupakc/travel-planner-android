# Scaling

Goal: the design should not collapse at 10 million users. The honest split is what the app does and what the backend must change.

## Client side (in place)

- **Stateless against the API**: the app holds a bearer token and caches per user; no server affinity is required.
- **Backoff on 429**: `Retry-After` is parsed (`AppError.RateLimited.retryAfterSeconds`) and the backend's message is shown; there is no tight retry loop. Explicit user retry only, plus one SSE retry per search.
- **Jitter**: the periodic alert poll (`AlertPollWorker`) starts after a random delay of up to half its period, so devices do not poll in lockstep.
- **Push over polling**: with FCM configured on both sides, alerts arrive by server push and the 30-minute poll drops to a 6 h safety net ([Notifications-and-Push](Notifications-and-Push.md)).
- **Cache first**: Room serves saved plans offline; the server's 30-minute search cache absorbs repeats. Near me results are cached in memory,
  per process, for an hour per ~5 km cell (`NearbyRepositoryImpl`); the cache does not survive into the background near-me worker's process start,
  whose 4 h spacing is longer than the TTL anyway.
- **Cheap chrome**: lightweight endpoints (`/health`, notifications unread count) are separate from AI calls, which sit in tight rate-limit buckets.

## Backend side (what must change; today it does not)

Grounded in the backend's `CLAUDE.md`: one SQLite file, an in-memory `cachetools` TTL cache (30 min, 500 entries), in-process sliding-window rate limits, a single uvicorn process (as documented), optional GCS backup, and a lifespan scheduler for fare/price watches. For 10 M users:

| Today | Needed |
|---|---|
| One SQLite file (`airports.db`, 21 tables) | Managed Postgres (users, plans, notifications, devices, watches) with migrations; read replicas for reads |
| In-process rate limits per node | Redis (or equivalent) shared limiter so limits hold across nodes |
| In-memory TTL cache per process | Redis/shared cache for search results and place lookups |
| One API process | Horizontal API nodes behind a load balancer (the JWT is stateless, so nodes are interchangeable) |
| Watch scheduler inside the API lifespan | A separate worker/queue with leases (the lease idea exists already) and the daily SerpAPI budget enforced centrally |
| FCM sent in a background task of the API | A push queue with batching, retries and token hygiene |
| The web frontend is a separate container; `GET /api/share/{token}` is JSON | CDN for the web frontend and any public share pages |
| LLM calls (Haiku 4.5, Sonnet 4.6) paced by `AGENT_MAX_CONCURRENCY` (default 6) per process | Capacity planning and provider rate limits per tenant; queueing for peak |
| Third-party quotas (SerpAPI free plan: 250 searches/month) | A paid plan sized to usage; the app already labels estimates when live data is absent |

Backend rate-limit defaults (per 10 minutes, per user or IP): search 10, chat 30, compare 30, discover 10, light_ai 60.

The app's contract does not change when the backend does: same paths, same SSE framing, same error shapes.
These are recommendations, not work done in either repo.
