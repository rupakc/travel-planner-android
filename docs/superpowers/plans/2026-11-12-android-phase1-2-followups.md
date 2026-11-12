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
