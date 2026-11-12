# Android Phase 1+2 follow-ups

Started with the plan; extend as tasks land.

## Owner actions
- Provide OAuth (Google, GitHub, Facebook), SMTP and FCM credentials; the app is inert without them.
- Publish `/.well-known/assetlinks.json` with the release cert SHA-256 so verify/reset links open the app without a chooser (R3).
- Native-speaker review of hi/bn/zh UI text and the phrase list.
- Native Google ID-token sign-in needs the owner's client ID (R2); Apple sign-in is not offered on Android.

## Engineering
- Plan PR #66 was merged before CI finished; confirm it passed.
- Sessions list depends on backend PR B1 (R1); the app degrades to "Sign out everywhere" until it deploys.
- T15: "Save as trip" from a search result does not set trip dates from the request explicitly (no separate code path; verify the dates arrive on the trip) and has no test.
- T17: coach-mark state is not purged on sign-out (per-user key, survives); standalone Today/Expenses/Journal/Recap routes and `onLinkProvider` are not wired (trip tabs cover them); `gameEnabled` ignores the user's `hide_game` setting; Home's `homeGraph` stays registered in journey mode.
- T11: `offlinePackDestination` registration relies on T9/T17 wiring; confirm in the smoke test.
- Backend docs: note that a revoked session's access token stays valid until expiry.
- `web.sha` pin not bumped (password min 10 vs 8); map strings when the pin moves.
- CI build takes ~17 min per PR; local builds need JDK 21 (`~/Library/Java/temurin-21`).
- MEMORY.md "Web repo" open-items line was not updated by T19.
- Final review: linking a sign-in method from the account area is not wired (`onLinkProvider` missing; `OauthInbox` LINKED results are dropped outside Login).
- Final review: `VerifyEmailViewModel` redeems a verify link automatically even when another user is signed in; add a confirmation step when a session exists.

## Release smoke test (2026-11-12, localRelease on emulator; prod APK built clean, SHA-256 f5e4a953...0819, debug-signed)
- Trips hub after a cold start offline shows an error: `TripsRepositoryImpl.cache` is in-memory only; persist the trip list in Room.
- Money tab offline fails if never opened online, and "Add expense" on Today jumps to that failed tab; load from the outbox/cache so the add form works offline.
- Reopening a trip shows "Check in for today" although the check-in exists, and the "Memories, set by you" stage override is lost (persist per trip).
- Right after login the Home screen has no top/bottom bar until relaunch (check `TravelApp.kt` gate).
- `localRelease` build needs a `.local` client in `google-services.json` (owner: add the package in Firebase) or the build fails at `processLocalReleaseGoogleServices`.
- No release signing key configured: release APKs are debug-signed (owner action, see Release.md).

## Re-smoke after #91 (prod APK SHA-256 315d8500...5e9d; tab crash fixed)
- Today "Add expense" does nothing when the trip is already on top (`TravelApp.kt` ~346, `launchSingleTop` no-op): switch the tab via state instead of navigating.
- Trips list is stale after Back from a new trip ("No trips yet" until re-selecting the tab): refresh on resume.
- After a stage change to Memories the Recap tab shows selected but the content is still Today (stale selected tab).
- Cold-start offline (see above) remains the top gap: persist the trips list and expenses in Room.
- Not verified: offline_pack row count just before sign-out, offline re-login.
