# Release

There is no release keystore yet. The `release` build type is signed with the **debug** key so CI can run
`assembleProdRelease` (R8 full mode, resource shrinking) and catch shrinker problems early. That APK must never
be published.

Before the first store release:

1. Create an upload keystore outside the repo (`*.jks` is git-ignored) and keep its passwords in CI secrets.
2. Add a `release` signing config read from `keystore.properties` / environment variables in `app/build.gradle.kts`
   and point `buildTypes.release.signingConfig` at it (remove the debug fallback).
3. Build with `-PPROD_BASE_URL=https://…` (HTTPS only; the `prod` flavour rejects cleartext).
4. Optional: add `app/google-services.json` to enable FCM (the google-services plugin applies automatically).
