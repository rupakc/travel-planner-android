#!/usr/bin/env bash
# Decides whether a release may be marked "latest" (GitHub Release) or pushed as :latest (GHCR). Pure: no network.
# Latest only when this run's commit is still the tip of main AND its versionCode is greater than every version
# already published; otherwise a re-run of an older run could move "latest" backwards (Android refuses to install a
# lower versionCode over a higher one).
#
# Usage: scripts/release-decision.sh <this-sha> <main-head-sha> <this-version-code> [<published-version-code>...]
# Prints "true" or "false" on stdout and the reason on stderr.
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 <this-sha> <main-head-sha> <this-version-code> [<published-version-code>...]" >&2
  exit 2
fi
sha=$1
head=$2
code=$3
shift 3
[[ "$code" =~ ^[0-9]+$ ]] || { echo "bad versionCode '$code'" >&2; exit 2; }

if [[ "$sha" != "$head" ]]; then
  echo "not latest: $sha is no longer the tip of main ($head)" >&2
  echo false
  exit 0
fi
for published in "$@"; do
  [[ "$published" =~ ^[0-9]+$ ]] || { echo "bad published versionCode '$published'" >&2; exit 2; }
  if (( published >= code )); then
    echo "not latest: versionCode $published is already published (this build is $code)" >&2
    echo false
    exit 0
  fi
done
echo "latest: tip of main and versionCode $code is the highest" >&2
echo true
