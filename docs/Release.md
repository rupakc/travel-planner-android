# Release

There is no release keystore yet. The `release` build type is signed with the **debug** key so CI can run
`assembleProdRelease` (R8 full mode, resource shrinking) and catch shrinker problems early. That APK must never
be published.

## Building a release APK

Release APKs for distribution (sideloading, sharing, testers) must come from `scripts/build-release-apk.sh` or from CI,
**never from an incremental local build**. The script runs `./gradlew clean`, then `assembleProdRelease` with
`--no-build-cache` plus `:app:verifyHiltViewModelsProdRelease` and `:app:verifyReleaseKeepRules`, and prints the APK path,
version and SHA-256. Extra arguments go to Gradle (e.g. `scripts/build-release-apk.sh -PPROD_BASE_URL=https://host`).

Why: a locally built release APK once crashed right after sign-in (`Cannot create an instance of class sv3` /
`NoSuchMethodException: <init> []`). A stale incremental KSP output for `:feature:alerts` had dropped
`NotificationBellViewModel` and `NotificationsViewModel` from Hilt's aggregated deps, so the app's Hilt component never
bound them and `hiltViewModel()` fell back to the no-arg factory. CI builds from scratch and was green. Two defences:

- `ksp.incremental=false` in `gradle.properties` (KSP 2.3 reads it; it is an `@Input` of the KSP task, so build-cache
  entries are always from full runs). Measured cost on a debug build: none beyond noise.
- `verifyHiltViewModels<Variant>` (every release variant; `assemble`, `bundle` and `package` depend on it) collects every
  `@HiltViewModel` in all modules' `src/main` and fails unless each one is bound in the variant's generated
  `*_HiltComponents_SingletonC.java` **and** its R8 name is a string in the shrunk dex (the key Hilt's `LazyClassKey` map
  looks up). The failure names the missing ViewModels; fix with `./gradlew clean` and a `--no-build-cache` build.
  The pure checks live in `build-logic` (`HiltViewModelGuard`, tested by `./gradlew :build-logic:convention:test`).

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
