# Release

Every green push to `main` (and a manual **Run workflow** of *Android CI* on `main`) publishes a signed release APK.
PR and local builds without a key are still signed with the **debug** key (so `assembleProdRelease` always works and
catches shrinker problems early); those APKs must never be published.

## Download

- **GitHub Releases**: the latest `android-v<versionName>` release has `Voyager-<versionName>.apk` and a `.sha256` file.
- **GitHub Packages (GHCR)**, as an OCI artifact: `oras pull ghcr.io/rupakc/voyager-android:latest` (or `:<versionName>`),
  then `sha256sum -c Voyager-*.apk.sha256`. If the package is private, `oras login ghcr.io` first, or make it public once
  in the package settings.
- The CI run's `voyager-release-apk` artifact (kept 90 days).

Check the signer before installing by hand: `apksigner verify --print-certs Voyager-*.apk` must show certificate SHA-256
`1C:B8:29:4D:D5:7B:8D:E7:7D:B2:77:99:F3:1A:86:3C:F8:97:56:D1:CC:E6:02:34:C8:E5:85:50:6E:1E:38:B0`.

## How CI publishes (`.github/workflows/android.yml`)

1. `build` runs every check (Spotless, detekt, lint, unit tests, debug + release assembles, guards). The release jobs
   `need` it, so nothing is published if any check fails. `main` runs queue (no cancellation), so two pushes never race a version.
2. `release-apk` (read-only token): decodes `VOYAGER_KEYSTORE_BASE64` to `$RUNNER_TEMP` (mode 0600), optionally
   `GOOGLE_SERVICES_JSON_BASE64` to `app/google-services.json`, runs `scripts/build-release-apk.sh` (clean,
   `--no-build-cache`, Hilt guard, keep-rule check) with `-PVOYAGER_REQUIRE_RELEASE_SIGNING=true`, verifies the APK with
   `apksigner` against the expected certificate (fails on mismatch), writes the SHA-256 and release notes, deletes the
   key and Firebase file in an `always()` step, and uploads the artifact.
3. `github-release` (`contents: write` only): creates the release `android-v<versionName>`, marked latest.
4. `ghcr` (`packages: write` only): pushes the same files with `oras` (pinned 1.3.6, checksum-verified) to
   `ghcr.io/rupakc/voyager-android:<versionName>` and `:latest`, with `org.opencontainers.image.source` pointing at this
   repo so the package shows under its Packages.

Secrets: `VOYAGER_KEYSTORE_BASE64` (base64 PKCS12), `VOYAGER_KEYSTORE_PASSWORD` (store and key password),
`VOYAGER_KEY_ALIAS`; optional `GOOGLE_SERVICES_JSON_BASE64`. Without it, push notifications are inactive in that build and
the release notes say so.

## Building a release APK

Release APKs for distribution (sideloading, sharing, testers) must come from `scripts/build-release-apk.sh` or from CI,
**never from an incremental local build**. The script runs `./gradlew clean`, then `assembleProdRelease` with
`--no-build-cache --rerun-tasks` plus `:app:verifyHiltViewModelsProdRelease` and `:app:verifyReleaseKeepRules`, and prints the APK path,
version and SHA-256. Extra arguments go to Gradle (e.g. `scripts/build-release-apk.sh -PPROD_BASE_URL=https://host`).

Why: a locally built release APK once crashed right after sign-in (`Cannot create an instance of class sv3` /
`NoSuchMethodException: <init> []`). The KSP output for `:feature:alerts` lacked the Hilt aggregated deps of
`NotificationBellViewModel` and `NotificationsViewModel`, so the app's Hilt component never bound them and
`hiltViewModel()` fell back to the no-arg factory. Root cause: KSP 2.3.13 (KSP2, in-process) silently writes empty or
partial output when several KSP tasks run at once; the task still succeeds. Reproduced on clean, `--no-build-cache`
builds locally and in CI; with KSP serialized every output is complete. Defences:

- **KSP tasks are serialized and never build-cached** (`build-logic` `configureKspSafety`: a shared build service with
  `maxParallelUsages = 1`, `cacheIf { false }`, never up to date with an empty output), so an incomplete output can
  neither happen nor spread through the build cache (local or CI), and an earlier empty run cannot look up to date
  after `clean`. Measured cost: none (a clean `--no-build-cache assembleLocalDebug` took 28–29 s serialized vs
  32–41 s parallel, and every parallel run was broken).
- `ksp.incremental=false` in `gradle.properties`, as defence in depth (cost within noise).
- `verifyHiltViewModels<Variant>` (every variant; `assemble`, `bundle` and `package` depend on it) collects every
  `@HiltViewModel` in all modules' `src/main` and fails unless each one is bound in the variant's generated
  `*_HiltComponents_SingletonC.java`; release variants also require its R8 name to be a string in the shrunk dex (the
  key Hilt's `LazyClassKey` map looks up). The failure names the missing ViewModels; fix with `./gradlew clean` and a
  rebuild. The pure checks live in `build-logic` (`HiltViewModelGuard`, tested by `./gradlew :build-logic:convention:test`).

## Versioning

Local builds use `versionCode` 1 and `versionName` `0.1.0`. CI passes `-PVOYAGER_VERSION_CODE=<100 + run number>` and
`-PVOYAGER_VERSION_NAME=0.1.<run number>`, so every published build has a higher `versionCode` (Android refuses to
install a lower one over a higher one) and a tag `android-v0.1.<run number>`. Changing the minor/major version means
editing the `Version` step in the workflow; `versionCode` must never go down.

## Flavours and the production URL

- `prod` has no host baked in: pass `-PPROD_BASE_URL=https://your-host` (HTTPS only; the `prod` network-security-config forbids cleartext and Settings -> Server rejects `http`).
- Optional `-PWEB_BASE_URL=https://...` sets the web origin used for share links.
- `local` is for development only (cleartext allowed); never upload it.

## Signing

`app/build.gradle.kts` has a `release` signing config, used when a key is configured:

- environment variables `VOYAGER_KEYSTORE_FILE` (path), `VOYAGER_KEYSTORE_PASSWORD`, `VOYAGER_KEY_ALIAS`
  (optional `VOYAGER_KEY_PASSWORD`, default: the store password), or
- a git-ignored `keystore.properties` in the repo root with `storeFile`, `storePassword`, `keyAlias` (optional `keyPassword`).

Without either, release falls back to the debug key; `-PVOYAGER_REQUIRE_RELEASE_SIGNING=true` (CI) turns that into a
build failure. Keystores (`*.jks`, `*.keystore`, `*.p12`) and `keystore.properties` are git-ignored; never commit them.

**Key backup and rotation.** The signing key is the app's identity: Android only installs an update signed with the same
key. If the key is lost or replaced, every user must uninstall the app (losing local data) and install the new build.
Keep the offline backup of the keystore and its password safe and separate from the repo; rotating is a deliberate,
announced break (update the secrets and the expected certificate in the workflow and in this page together).

For Play: `./gradlew bundleProdRelease -PPROD_BASE_URL=https://...` with the key configured (output
`app/build/outputs/bundle/prodRelease/`); enrol in Play App Signing with this key as the upload key. Optional: add
`app/google-services.json` to enable FCM (see [Firebase-Setup](Firebase-Setup.md)); without it push stays inert.

## Play Console steps

1. Create the app, enable Play App Signing, upload the AAB to an internal testing track first.
2. Fill the store listing (EN and ES), content rating, target audience, and Data safety (account credentials, plan content, optional coarse/precise location for Near me, optional FCM token; data goes to the user's own backend). Location: declare it as shared with a third party — when the user opens Near me, the coordinates (rounded to ~100 m) go via the backend to Google Maps (Gemini Grounding with Google Maps) to find nearby places; not stored, not used for ads, not collected in the background.
3. Declare permissions in context: `POST_NOTIFICATIONS`, location (foreground only, Near me).
4. Provide a test account (the backend has no self-registration) in the App access section.
5. Review the pre-launch report (accessibility, crashes), then promote to production.

Run [Security](Security.md) and the `release` Claude skill checklist before each upload.
