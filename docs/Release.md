# Release

Every green push to `main` (and a manual **Run workflow** of *Android CI* on `main`) publishes a signed release APK.
PR and local builds without a key are still signed with the **debug** key (so `assembleProdRelease` always works and
catches shrinker problems early); those APKs must never be published.

## Download

- **GitHub Releases**: the latest `android-v<versionName>` release has `Voyager-<versionName>.apk` and a `.sha256` file.
- **GitHub Packages (GHCR)**, as an OCI artifact: `oras pull ghcr.io/rupakc/voyager-android:latest` (or `:<versionName>`),
  then `sha256sum -c Voyager-*.apk.sha256`. The package must be made public once in its settings; until then
  `oras login ghcr.io` first.
- The CI run's `voyager-release-apk` artifact (kept 90 days).

Check the signer before installing by hand: `apksigner verify --print-certs Voyager-*.apk` must show certificate SHA-256
`1C:B8:29:4D:D5:7B:8D:E7:7D:B2:77:99:F3:1A:86:3C:F8:97:56:D1:CC:E6:02:34:C8:E5:85:50:6E:1E:38:B0`.

## How CI publishes (`.github/workflows/android.yml`)

1. `build` runs every check (Spotless, detekt, lint, unit tests, debug + release assembles, guards, the release script
   tests) and finally `scripts/ci-release-config-check.sh`: the release path with **no secrets** (throwaway key, dummy
   `google-services.json`): clean unsigned build with Firebase applied, apksigner signing, verification, and checks that
   a wrong certificate, a wrong versionCode and an unsigned APK are rejected. So a broken release config fails on the PR.
   The publishing jobs `need` `build`, so nothing is published if any check fails. `main` runs queue (no cancellation).
2. The three publishing jobs run in the GitHub Environment **`release`**, whose deployment branch policy allows only
   `main`. The secrets are still repository secrets referenced from those jobs; the owner can move them into the
   environment (Settings -> Environments -> release) for extra hardening without changing the workflow.
3. `release-apk` (`contents: read`):
   - optionally decodes `GOOGLE_SERVICES_JSON_BASE64` to `app/google-services.json`;
   - builds **unsigned** with `scripts/build-release-apk.sh -PVOYAGER_UNSIGNED_RELEASE=true` (clean, `--no-build-cache
     --rerun-tasks`, Hilt guard, keep rules). Gradle never sees the key or its password;
   - decodes `VOYAGER_KEYSTORE_BASE64` to `$RUNNER_TEMP` (0600); only the **Sign** step gets the password:
     `scripts/sign-release-apk.sh` (zipalign 4 / 16 KB pages, apksigner v2 + v3, password passed as `env:` name);
   - deletes the key and Firebase file in an `always()` step;
   - `scripts/verify-apk.sh`: apksigner verify, v2 and v3, exactly one signer, every signer certificate line equals the
     expected SHA-256, and the APK manifest's versionCode/versionName equal the expected values exactly (aapt2);
   - writes `Voyager-<v>.apk`, `.sha256` and the release notes (`scripts/release-notes.sh`: commit range since the last
     `android-v*` tag, `@mentions` neutralised, an HTML comment carrying the versionCode), uploads the artifact.
4. `ghcr` (`contents: read`, `packages: write`): pushes the APK with `oras` (pinned 1.3.6, checksum-verified) to
   `ghcr.io/rupakc/voyager-android:<versionName>`, with `org.opencontainers.image.source` pointing at this repo so the
   package shows under its Packages, and `io.github.rupakc.voyager.version-code`. **The package is created private: make
   it public once** (package settings -> Change visibility) or `oras pull` needs `oras login ghcr.io`.
5. `github-release` (`contents: write`): runs even when the GHCR push failed. The notes show the `oras pull` line only
   when it succeeded. Creates `android-v<versionName>` with the APK and `.sha256`.

**"latest" never moves backwards.** `:latest` (GHCR) and the Release's *latest* flag are set only when this run's commit
is still the tip of `main` **and** its versionCode is greater than every published one (other `android-v*` releases, and
for GHCR the current `:latest`'s annotation): `scripts/release-decision.sh`. Otherwise the versioned release/tag is
published without "latest". API errors fail the job; they never default to "latest".

**Re-runs are safe.** Re-running a run (or only its failed jobs) rebuilds the same version. GHCR overwrites
`:<versionName>`. If the tag exists on the same commit, the release's assets and notes are replaced (`--clobber`); if the
tag exists on another commit, the job fails without publishing.

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
  `@HiltViewModel` (nested classes included) in the shipping source sets (`main`, flavour, build type, variant) of every
  Gradle module and fails unless each one is bound in the variant's generated `*_HiltComponents_SingletonC.java`.
  Minified variants also require, in the shrunk dex, the ViewModel's class and its R8 name as an exact string (the key
  Hilt's `LazyClassKey` map looks up; skipped for R8 names under 3 characters, where a matching string proves nothing
  and the component check covers it). The failure names the missing ViewModels; fix with `./gradlew clean` and a
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

Without either, release falls back to the debug key; `-PVOYAGER_REQUIRE_RELEASE_SIGNING=true` turns that into a build
failure. `-PVOYAGER_UNSIGNED_RELEASE=true` builds an unsigned release APK (CI does this, then signs it with
`scripts/sign-release-apk.sh`; you can do the same locally with `VOYAGER_KEYSTORE_*` set only for that script). Keystores (`*.jks`, `*.keystore`, `*.p12`) and `keystore.properties` are git-ignored; never commit them.

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
