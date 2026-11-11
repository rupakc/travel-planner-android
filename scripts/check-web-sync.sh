#!/usr/bin/env bash
# Checks the generated design tokens and strings against the web commit pinned in
# config/web-source.properties. Usage: scripts/check-web-sync.sh <web git dir>
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <web git dir>" >&2
  exit 2
fi
web_dir=$1
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root"

sha=$(sed -n 's/^web\.sha=//p' config/web-source.properties | tr -d '[:space:]')
if [ -z "$sha" ]; then
  echo "web.sha is missing in config/web-source.properties" >&2
  exit 1
fi
if ! git -C "$web_dir" cat-file -e "${sha}^{commit}" 2>/dev/null; then
  echo "pinned web commit $sha not found in $web_dir: run git -C $web_dir fetch" >&2
  exit 1
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
git -C "$web_dir" archive "$sha" frontend/design/tokens.json frontend/src/i18n/locales | tar -x -C "$tmp"

python3 -m unittest discover -s tools/web-sync -p 'test_*.py'
python3 tools/web-sync/gen_tokens.py check --tokens "$tmp/frontend/design/tokens.json"
python3 tools/web-sync/gen_strings.py check --web "$tmp/frontend/src/i18n/locales"
echo "web sync OK ($sha)"
