---
name: add-api-endpoint
description: Add or change a backend endpoint integration (wire model, Retrofit method, repository, fake, tests, docs).
---

# Add an API endpoint

Read `docs/Backend-Integration.md`, `docs/Networking-and-SSE.md`, `docs/reference/api-contract.md`. Verify the endpoint in the backend source (`~/travel-planner/backend/app/api/routes`) and its `docs/API-Reference.md`; if the backend lacks it, that is a separate additive PR in the web repo first. Mark anything unverified as such; never guess a shape.

1. Wire models in `core:model` (kotlinx.serialization via `TravelJson`; `Lenient*` types for numbers that may be strings; nullable, unknown keys tolerated; keep raw JSON with `RawBacked` where plans round-trip).
2. Retrofit method in the matching interface in `core/network/.../api/Apis.kt`; pick the client by latency: default (30 s) or AI (300 s); streams use the streaming client and `SseParser`.
3. Repository method in `core:data` returning `AppResult<T>`; map errors with `HttpErrorMapper` (429 message + `Retry-After`, 422 codes in `AppError.Client.code/extras`). Tokens never in URLs.
4. Update the fake in `core:testing` and add a MockWebServer test with a recorded response; stream endpoints get fixtures including a cut stream.
5. Update the endpoint table in `docs/Backend-Integration.md` and `docs/Foundation-Interfaces.md` if a public interface changed; add a `MEMORY.md` line for contract decisions.
6. Run `verify-android`; use the `api-contract-checker` agent for a second look.
