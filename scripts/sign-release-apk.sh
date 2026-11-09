#!/usr/bin/env bash
# Signs an unsigned release APK with the Voyager key: zipalign (4-byte, 16 KB pages for .so files), then apksigner
# with APK Signature Scheme v2 + v3 (minSdk 26, so no v1). The key comes only from the environment:
#   VOYAGER_KEYSTORE_FILE, VOYAGER_KEYSTORE_PASSWORD (store and key), VOYAGER_KEY_ALIAS
# The password is passed to apksigner by name (env:), never on the command line or in the logs.
#
# Usage: scripts/sign-release-apk.sh <unsigned.apk> <signed.apk>
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <unsigned.apk> <signed.apk>" >&2
  exit 2
fi
in=$1
out=$2
: "${VOYAGER_KEYSTORE_FILE:?VOYAGER_KEYSTORE_FILE is not set}"
: "${VOYAGER_KEYSTORE_PASSWORD:?VOYAGER_KEYSTORE_PASSWORD is not set}"
: "${VOYAGER_KEY_ALIAS:?VOYAGER_KEY_ALIAS is not set}"

# shellcheck source=scripts/lib/build-tools.sh
source "$(dirname "$0")/lib/build-tools.sh"
bt=$(build_tools_dir)

aligned="$out.aligned.tmp"
trap 'rm -f "$aligned"' EXIT
"$bt/zipalign" -P 16 -f 4 "$in" "$aligned"
"$bt/apksigner" sign \
  --ks "$VOYAGER_KEYSTORE_FILE" \
  --ks-pass env:VOYAGER_KEYSTORE_PASSWORD \
  --key-pass env:VOYAGER_KEYSTORE_PASSWORD \
  --ks-key-alias "$VOYAGER_KEY_ALIAS" \
  --v1-signing-enabled false \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$out" \
  "$aligned"
"$bt/zipalign" -c -P 16 4 "$out"
echo "Signed: $out"
