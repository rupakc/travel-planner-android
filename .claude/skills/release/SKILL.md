---
name: release
description: Prepare a store release of the prod flavour (version bump, checks, signed bundle, Play Console checklist).
---

# Release

Read `docs/Release.md` and `docs/Security.md`. Release steps change shared state; confirm each destructive or publishing step with the user. Never commit keystores, `keystore.properties` or `google-services.json`.

1. Branch from an up-to-date `main`; CI green.
2. Bump `versionCode` (always) and `versionName` in `app/build.gradle.kts`; update release notes.
3. Run the `verify-android` skill including `assembleProdRelease`.
4. Confirm the build is signed with the Voyager release key (`apksigner verify --print-certs` shows the certificate SHA-256 in docs/Release.md), not the debug fallback; for a store build pass `-PVOYAGER_REQUIRE_RELEASE_SIGNING=true`. If not, stop and report: a debug-signed APK must never be published.
5. `./gradlew bundleProdRelease -PPROD_BASE_URL=https://<host>` (HTTPS; the prod flavour rejects cleartext). Optional `app/google-services.json` for FCM.
6. Smoke-test the release build on a device (sign in, search stream, share link deep link, notification permission flow).
7. Security pass: no secrets in the diff, `local` flavour not included, deep links validated.
8. Upload the AAB to internal testing, complete the Play Console checklist in `docs/Release.md`, tag `v<versionName>`.
