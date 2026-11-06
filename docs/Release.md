# Release

There is no release keystore yet. The `release` build type is signed with the **debug** key so CI can run
`assembleProdRelease` (R8 full mode, resource shrinking) and catch shrinker problems early. That APK must never
be published.

## Versioning

`versionCode` and `versionName` are set in `app/build.gradle.kts` (currently 1 and `0.1.0`). Bump `versionCode` on every upload
(Play rejects reuse) and use semantic `versionName`. Tag releases `v<versionName>` and list changes in the GitHub release notes.

## Flavours and the production URL

- `prod` has no host baked in: pass `-PPROD_BASE_URL=https://your-host` (HTTPS only; the `prod` network-security-config forbids cleartext and Settings -> Server rejects `http`).
- Optional `-PWEB_BASE_URL=https://...` sets the web origin used for share links.
- `local` is for development only (cleartext allowed); never upload it.

## Signing

Before the first store release:

1. Create an upload keystore **outside the repo** (`*.jks`, `*.keystore` and `keystore.properties` are git-ignored) and keep passwords in CI secrets or a local `keystore.properties`.
2. Add a `release` signing config to `app/build.gradle.kts` that reads `keystore.properties` or environment variables
   (`TP_KEYSTORE_FILE`, `TP_KEYSTORE_PASSWORD`, `TP_KEY_ALIAS`, `TP_KEY_PASSWORD`; names are a recommendation, pick and document one set) and point `buildTypes.release.signingConfig` at it, removing the debug fallback.
3. Build the bundle: `./gradlew bundleProdRelease -PPROD_BASE_URL=https://...` (output `app/build/outputs/bundle/prodRelease/`).
4. Optional: add `app/google-services.json` to enable FCM (the google-services plugin applies automatically; see [Firebase-Setup](Firebase-Setup.md)). Without it the build is identical but push stays inert.

## Play Console steps

1. Create the app, enable Play App Signing, upload the AAB to an internal testing track first.
2. Fill the store listing (EN and ES), content rating, target audience, and Data safety (account credentials, plan content, optional coarse/precise location for Near me, optional FCM token; data goes to the user's own backend). Location: declare it as shared with a third party — when the user opens Near me, the coordinates (rounded to ~100 m) go via the backend to Google Maps (Gemini Grounding with Google Maps) to find nearby places; not stored, not used for ads, not collected in the background.
3. Declare permissions in context: `POST_NOTIFICATIONS`, location (foreground only, Near me).
4. Provide a test account (the backend has no self-registration) in the App access section.
5. Review the pre-launch report (accessibility, crashes), then promote to production.

Run [Security](Security.md) and the `release` Claude skill checklist before each upload.
