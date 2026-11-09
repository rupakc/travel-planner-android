#!/usr/bin/env bash
# PR check for the release path, with no secrets: a throwaway key and a dummy google-services.json stand in for the
# real ones. Runs exactly what the release job runs (unsigned clean build with Firebase applied, apksigner signing,
# signature + version verification) and checks that a wrong certificate is rejected.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -e app/google-services.json ]]; then
  echo "app/google-services.json already exists; refusing to overwrite it" >&2
  exit 1
fi
tmp=$(mktemp -d)
cleanup() {
  rm -rf "$tmp"
  rm -f app/google-services.json
}
trap cleanup EXIT

# Dummy Firebase config for every application id the app builds (values are syntactically valid, not real).
client() {
  printf '{"client_info":{"mobilesdk_app_id":"1:000000000000:android:%s","android_client_info":{"package_name":"%s"}},' "$2" "$1"
  printf '"oauth_client":[],"api_key":[{"current_key":"AIzaSyDummyDummyDummyDummyDummyDummy00"}],'
  printf '"services":{"appinvite_service":{"other_platform_oauth_client":[]}}}'
}
base=io.github.rupakc.travelplanner
{
  printf '{"project_info":{"project_number":"000000000000","project_id":"voyager-ci-dummy","storage_bucket":"voyager-ci-dummy.appspot.com"},"client":['
  client "$base" 0000000000000001; printf ','
  client "$base.debug" 0000000000000002; printf ','
  client "$base.local" 0000000000000003; printf ','
  client "$base.local.debug" 0000000000000004
  printf '],"configuration_version":"1"}\n'
} > app/google-services.json

export VOYAGER_KEYSTORE_FILE="$tmp/throwaway.p12" VOYAGER_KEY_ALIAS=voyager
VOYAGER_KEYSTORE_PASSWORD=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
export VOYAGER_KEYSTORE_PASSWORD
keytool -genkeypair -keystore "$VOYAGER_KEYSTORE_FILE" -storetype PKCS12 -storepass:env VOYAGER_KEYSTORE_PASSWORD \
  -keypass:env VOYAGER_KEYSTORE_PASSWORD -alias voyager -keyalg RSA -keysize 2048 -validity 2 -dname "CN=Voyager CI throwaway" >/dev/null 2>&1
cert=$(keytool -list -v -keystore "$VOYAGER_KEYSTORE_FILE" -storepass:env VOYAGER_KEYSTORE_PASSWORD -alias voyager |
  sed -n 's/^[[:space:]]*SHA256: //p')

# The Gradle build never sees the key (as in the release job).
(
  unset VOYAGER_KEYSTORE_FILE VOYAGER_KEYSTORE_PASSWORD VOYAGER_KEY_ALIAS
  scripts/build-release-apk.sh "$@" -PVOYAGER_UNSIGNED_RELEASE=true -PVOYAGER_VERSION_CODE=100 -PVOYAGER_VERSION_NAME=0.1.0-ci
)
grep -q '<bool name="fcm_enabled">true</bool>' app/build/generated/res/resValues/prod/release/values/gradleResValues.xml ||
  { echo "Firebase config was not applied to the release build" >&2; exit 1; }

unsigned=app/build/outputs/apk/prod/release/app-prod-release-unsigned.apk
scripts/sign-release-apk.sh "$unsigned" "$tmp/signed.apk"
scripts/verify-apk.sh "$tmp/signed.apk" "$cert" 100 0.1.0-ci
if scripts/verify-apk.sh "$tmp/signed.apk" "00:11:22" 100 0.1.0-ci 2>/dev/null; then
  echo "verify-apk.sh accepted a wrong certificate" >&2
  exit 1
fi
if scripts/verify-apk.sh "$tmp/signed.apk" "$cert" 1000 0.1.0-ci 2>/dev/null; then
  echo "verify-apk.sh accepted a wrong versionCode" >&2
  exit 1
fi
if scripts/verify-apk.sh "$unsigned" "$cert" 2>/dev/null; then
  echo "verify-apk.sh accepted an unsigned APK" >&2
  exit 1
fi
echo "Release config check passed."
