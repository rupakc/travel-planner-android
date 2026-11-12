# Phase 0 parity: follow-ups

Deferred from the final whole-branch review and smoke run of the Phase 0 parity plan
(`2026-11-11-phase0-parity.md`). Items 1 and 2 need a backend change in the web repo.

## Backend (travel-planner web repo)

1. **Crash `error` kind for telemetry.** Add an `error` event kind to `POST /api/telemetry` (name, exception
   class, top frames without messages or user data, app version, locale) so the Android app can report crashes
   under the same consent rules. Until then the app reports no crashes. Ship the backend first (accept and store
   it), then the app.
2. **Mobile OAuth finish.** A PKCE or one-time-code redirect back into the app (`travelplanner://auth`), so the
   social buttons can complete sign-in instead of showing "coming soon".

## Android, Important findings not fixed in the review PR

3. **Social sign-in is a dead end when a server has providers configured.** `LoginViewModel` opens the server's
   OAuth start URL in a Custom Tab, but nothing returns to the app. Until item 2 ships, show "coming soon" for
   every provider (the plan's original behaviour) and update `LoginViewModelTest`.

## Minor

4. Do not report the `privacy.deny` tap or the switch-off toggle before `choose(DENIED)` has finished
   (`ConsentPrompt.kt`, `SettingsScreen.kt`); with a full queue they can be flushed first.
5. `cold_start` should be measured at the first content frame after the DataStore gate leaves `LOADING`, not at
   the splash predraw (`MainActivity.kt`).
6. `AppCompatAppLanguage` should fall back to the stored `AppSettings.localeTag` before the system locale, for
   process starts made by WorkManager.
7. `TelemetryUploader` should map any non-cancellation exception to `SendOutcome.Retry`, not only `IOException`.
8. `AiLocaleInterceptor.AI_PATHS` omits `/api/layover`, `/api/nearby`, `/api/live-check` and JSON
   `/api/import/extract`; confirm what the web client sends `locale` on and add them.
9. After a POST 404 the engine stays off for the rest of the process even if the user switches to a server that
   has telemetry (restart fixes it); reset the flag when the base URL changes.
10. The failed-config answer is cached for the whole process; a transient failure keeps telemetry off until the
    next start. Distinguish error from 404/disabled in `ClientConfigRepository` and retry with backoff.

## Smoke-test notes

- The prod flavour refuses cleartext HTTP by design, so it cannot talk to the local backend at
  `http://10.0.2.2:8001`; the end-to-end run used the clean `localDebug` APK. The prod release APK was verified
  to build, install and show the Login screen (four provider buttons, consent prompt, "coming soon" note).
- The local test backend has no LLM credentials, so a full search was not run; German AI text and the
  `locale` request field are covered by unit tests only.
