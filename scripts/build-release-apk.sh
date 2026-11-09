#!/usr/bin/env bash
# Builds the prod release APK from scratch: `clean`, no build cache, then the release checks.
# Release APKs for distribution come from this script or CI, never from an incremental local build
# (a stale incremental KSP output once produced an APK that crashed after sign-in; docs/Release.md).
#
# Usage: scripts/build-release-apk.sh [extra gradle args, e.g. -PPROD_BASE_URL=https://host]
set -euo pipefail

cd "$(dirname "$0")/.."

# `clean` runs as its own invocation so nothing from the previous build survives into the release build;
# --rerun-tasks also ignores Gradle's execution history (an earlier broken run can otherwise look up to date).
./gradlew clean
./gradlew --no-build-cache --rerun-tasks "$@" \
  :app:assembleProdRelease :app:verifyHiltViewModelsProdRelease :app:verifyReleaseKeepRules

apk_dir=app/build/outputs/apk/prod/release
apk=$(find "$apk_dir" -maxdepth 1 -name '*.apk' | head -n 1)
if [[ -z "$apk" ]]; then
  echo "No APK found in $apk_dir" >&2
  exit 1
fi

# Version from AGP's output metadata (no SDK tools needed).
version=$(sed -n 's/.*"versionName": *"\([^"]*\)".*/\1/p' "$apk_dir/output-metadata.json" | head -n 1)
code=$(sed -n 's/.*"versionCode": *\([0-9]*\).*/\1/p' "$apk_dir/output-metadata.json" | head -n 1)
if command -v sha256sum >/dev/null 2>&1; then
  sha=$(sha256sum "$apk" | cut -d' ' -f1)
else
  sha=$(shasum -a 256 "$apk" | cut -d' ' -f1)
fi

echo
echo "APK:     $(cd "$(dirname "$apk")" && pwd)/$(basename "$apk")"
echo "Version: ${version:-?} (versionCode ${code:-?})"
echo "SHA-256: $sha"

if [[ "$apk" == *-unsigned.apk ]]; then
  echo "Unsigned (-PVOYAGER_UNSIGNED_RELEASE=true): sign it with scripts/sign-release-apk.sh."
  exit 0
fi
# shellcheck source=scripts/lib/build-tools.sh
source scripts/lib/build-tools.sh
if bt=$(build_tools_dir 2>/dev/null); then
  cert=$("$bt/apksigner" verify --print-certs "$apk" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' || true)
  echo "Signer:  SHA-256 ${cert:-?}"
fi
if [[ -n "${VOYAGER_KEYSTORE_FILE:-}" || -f keystore.properties ]]; then
  echo "Signed with the release key (VOYAGER_KEYSTORE_FILE or keystore.properties)."
else
  echo "Signed with the debug key: no release key configured (docs/Release.md)."
fi
