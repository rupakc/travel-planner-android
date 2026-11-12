# Accounts

Sign-up, email verification, password reset, social sign-in, sessions and the account area. Contract: the web repo's `docs/Accounts-API.md` (frozen, additive only).

- **Sign-up and reset** are config-gated by the backend; when sign-up is off the app shows the sign-in form only. Verify and reset links are opened in the app ([Deep-Links](Deep-Links.md)) or pasted as a link or code.
- **Social sign-in** (Google, GitHub, Facebook; no Apple on Android, ruling R2): system browser (Custom Tab) with PKCE, returning on `voyager://auth/callback?code=|error=`. The 64-char verifier lives in a process-scoped holder for that one attempt. Buttons come from `/auth/providers` in served order; unconfigured shows "coming soon". Native Google ID-token sign-in is deferred.
- **Session**: access token plus mobile refresh token, kept encrypted (Tink, Keystore). `SessionManager` (core:network) recovers a 401 inside `AuthInterceptor` with a single refresh (rotation, 30 s grace on the server). Logout-all revokes the family.
- **Sessions list** (R1): backend `GET /api/auth/sessions` and `DELETE /api/auth/sessions/{id}`. The app shows "this device" plus the list when the endpoint exists, otherwise only "Sign out everywhere". A revoked session's access token stays valid until it expires.
- **Account area** (`feature:account`): profile and avatar, settings, data export and delete, and onboarding. Existing users see the 5-step onboarding once when `onboarding.done == false`; Skip sends `skipped:true` (R5). The local `TravellerProfile` is kept and synced to server preferences.
- **Game**: `GET /api/game/me` drives XP, badges and streaks; hidden when the response is 404 or `hide_game` is set.
