#!/usr/bin/env bash
# Verifies a signed APK before it is published:
#   - apksigner verify passes, with APK Signature Scheme v2 and v3, and exactly one signer;
#   - every signer certificate SHA-256 line (Signer #n / Signer (minSdkVersion=...)) equals <expected-cert>;
#   - the APK's own manifest has exactly <versionCode> and <versionName> (aapt2), when given.
#
# Usage: scripts/verify-apk.sh <apk> <expected-cert-sha256, AA:BB:.. or hex> [<versionCode> <versionName>]
set -euo pipefail

if [[ $# -ne 2 && $# -ne 4 ]]; then
  echo "Usage: $0 <apk> <expected-cert-sha256> [<versionCode> <versionName>]" >&2
  exit 2
fi
apk=$1
expected=$(tr -d ':' <<<"$2" | tr '[:lower:]' '[:upper:]')

# shellcheck source=scripts/lib/build-tools.sh
source "$(dirname "$0")/lib/build-tools.sh"
bt=$(build_tools_dir)

out=""
fail() {
  echo "::error::$*" >&2
  # Signer and scheme lines only (public certificate data, never key material), so a failure explains itself
  if [[ -n "$out" ]]; then
    grep -E '^(Verified using|Number of signers|Signer)' <<<"$out" | grep -vE 'public key|MD5|SHA-1' >&2 || true
  fi
  exit 1
}

echo "apksigner: $bt/apksigner" >&2
out=$("$bt/apksigner" verify --verbose --print-certs "$apk" 2>&1) || fail "apksigner verify failed for $apk"
grep -qx 'Verified using v2 scheme (APK Signature Scheme v2): true' <<<"$out" || fail "APK is not signed with scheme v2"
grep -qx 'Verified using v3 scheme (APK Signature Scheme v3): true' <<<"$out" || fail "APK is not signed with scheme v3"
signers=$(sed -n 's/^Number of signers: //p' <<<"$out")
[[ "$signers" == 1 ]] || fail "expected exactly one signer, found '${signers:-0}'"
# Hex with or without colons, CRLF-safe (apksigner versions differ in format)
digests=$(tr -d '\r' <<<"$out" |
  sed -nE 's/^Signer( #[0-9]+| \([^)]*\))? certificate SHA-256 digest: *([0-9a-fA-F:]+) *$/\2/p' |
  tr -d ':' | tr '[:lower:]' '[:upper:]')
count=$(grep -c . <<<"$digests" || true)
[[ "$count" -ge 1 ]] || fail "no signer certificate found"
while read -r digest; do
  [[ "$digest" == "$expected" ]] || fail "APK signer mismatch: SHA-256 $digest, expected $2"
done <<<"$digests"

if [[ $# -eq 4 ]]; then
  badging=$("$bt/aapt2" dump badging "$apk") || fail "aapt2 could not read $apk"
  badging=$(sed -n '1p' <<<"$badging")
  code=$(sed -nE "s/.* versionCode='([0-9]+)'.*/\1/p" <<<"$badging")
  name=$(sed -nE "s/.* versionName='([^']*)'.*/\1/p" <<<"$badging")
  [[ "$code" == "$3" ]] || fail "versionCode is '$code', expected '$3'"
  [[ "$name" == "$4" ]] || fail "versionName is '$name', expected '$4'"
fi
echo "Verified: $apk (one signer, v2+v3, cert $2${3:+, versionCode $3, versionName $4})"
