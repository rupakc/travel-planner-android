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
