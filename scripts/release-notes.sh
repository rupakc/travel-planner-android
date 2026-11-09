#!/usr/bin/env bash
# Writes the GitHub Release notes for a CI build to stdout. Commit subjects are untrusted text: @mentions are
# neutralised (zero-width joiner after "@") so a release never pings anyone. The HTML comment carries the versionCode
# that later runs read back (scripts/release-decision.sh).
#
# Env: VERSION_NAME, VERSION_CODE, APK_SHA256, CERT_SHA256, FCM (enabled|inert), REPO_URL, GITHUB_SHA
# Optional: PREV_TAG (previous android-v* tag; empty for the first release)
set -euo pipefail

: "${VERSION_NAME:?}" "${VERSION_CODE:?}" "${APK_SHA256:?}" "${CERT_SHA256:?}" "${FCM:?}" "${REPO_URL:?}" "${GITHUB_SHA:?}"
prev=${PREV_TAG:-}
zwj=$(printf '\342\200\215') # U+200D zero-width joiner

neutralise() {
  sed "s/@/@${zwj}/g"
}

echo "<!-- voyager-version-code: $VERSION_CODE -->"
echo "Voyager for Android $VERSION_NAME (versionCode $VERSION_CODE), built by CI from \`${GITHUB_SHA::12}\` after every check passed."
echo
echo "- APK: \`Voyager-$VERSION_NAME.apk\` (SHA-256 \`$APK_SHA256\`)"
echo "- Signer certificate SHA-256: \`$CERT_SHA256\`"
if [[ "$FCM" == enabled ]]; then
  echo "- Push notifications: enabled (Firebase configured)."
else
  echo "- Push notifications: **inactive** in this build (no Firebase config); in-app alerts still work."
fi
echo
if [[ -n "$prev" ]]; then
  echo "## Changes since $prev ([compare]($REPO_URL/compare/$prev...$GITHUB_SHA))"
  range="$prev..$GITHUB_SHA"
else
  echo "## Changes (first release)"
  range=$GITHUB_SHA
fi
echo
git log --no-merges --pretty='- %s (%h)' "$range" | head -n 200 | neutralise
