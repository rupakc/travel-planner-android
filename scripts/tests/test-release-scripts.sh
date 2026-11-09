#!/usr/bin/env bash
# Unit tests for the pure release scripts (no Android SDK, no network). CI runs this in the build job.
set -euo pipefail
cd "$(dirname "$0")/../.."

failures=0
check() { # <description> <expected> <actual>
  if [[ "$2" == "$3" ]]; then
    echo "ok   - $1"
  else
    echo "FAIL - $1: expected '$2', got '$3'"
    failures=$((failures + 1))
  fi
}
decide() { scripts/release-decision.sh "$@" 2>/dev/null; }

check "tip of main, highest code -> latest" true "$(decide abc abc 105 101 104)"
check "first release (nothing published) -> latest" true "$(decide abc abc 101)"
check "commit is no longer main HEAD -> not latest" false "$(decide abc def 105 101)"
check "a higher code is already published -> not latest" false "$(decide abc abc 105 106)"
check "same code already published -> not latest" false "$(decide abc abc 105 105)"
if scripts/release-decision.sh abc abc x >/dev/null 2>&1; then r=accepted; else r=rejected; fi
check "non-numeric versionCode is rejected" rejected "$r"

zwj=$(printf '\342\200\215')
notes=$(VERSION_NAME=0.1.5 VERSION_CODE=105 APK_SHA256=deadbeef CERT_SHA256=AA:BB FCM=inert \
  REPO_URL=https://github.com/example/repo GITHUB_SHA=$(git rev-parse HEAD) PREV_TAG='' \
  bash -c 'git() { printf "%s\n" "- thanks @someone and @org/team (abc1234)"; }; export -f git; scripts/release-notes.sh')
check "notes carry the versionCode marker" "<!-- voyager-version-code: 105 -->" "$(head -n 1 <<<"$notes")"
check "notes neutralise @mentions" "- thanks @${zwj}someone and @${zwj}org/team (abc1234)" "$(tail -n 1 <<<"$notes")"
check "notes say push is inactive without Firebase" 1 "$(grep -c 'Push notifications: \*\*inactive\*\*' <<<"$notes")"

# First release: no PREV_TAG and more than 200 commits. `git log | head` used to die of SIGPIPE (exit 141) here.
repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT
git -C "$repo" init -q
for i in $(seq 1 230); do
  git -C "$repo" -c user.name=t -c user.email=t@example.invalid commit -q --allow-empty -m "change $i by @dev"
done
set +e
long=$(cd "$repo" && VERSION_NAME=0.1.1 VERSION_CODE=101 APK_SHA256=x CERT_SHA256=y FCM=enabled \
  REPO_URL=https://github.com/example/repo GITHUB_SHA=$(git rev-parse HEAD) PREV_TAG='' \
  "$OLDPWD/scripts/release-notes.sh")
rc=$?
set -e
check "first release with 230 commits exits 0 (no SIGPIPE)" 0 "$rc"
check "first release lists at most 200 commits" 200 "$(grep -c '^- change ' <<<"$long")"

if [[ $failures -gt 0 ]]; then
  echo "$failures failure(s)"
  exit 1
fi
echo "All release script tests passed."
