#!/usr/bin/env bash
# Verifies that every relative markdown link in the top-level docs and docs/*.md resolves to a file.
# Usage: scripts/check-doc-links.sh        (exit 1 on a broken link)
# Pages owned by other teams that may not be merged yet are listed in PENDING (warning only);
# remove an entry once its page exists.
set -euo pipefail
cd "$(dirname "$0")/.."

PENDING=()

files=(README.md CONTRIBUTING.md CODE_OF_CONDUCT.md CLAUDE.md MEMORY.md docs/*.md docs/reference/*.md)
errors=0
warnings=0

is_pending() {
  local t="$1"
  # ${arr[@]+...} keeps an empty array safe under `set -u` on bash 3.2 (macOS).
  for p in ${PENDING[@]+"${PENDING[@]}"}; do [[ "$t" == "$p" ]] && return 0; done
  return 1
}

for f in "${files[@]}"; do
  [[ -f "$f" ]] || continue
  dir=$(dirname "$f")
  # strip fenced code blocks, then extract link targets with their line numbers
  while IFS=$'\t' read -r line target; do
    target="${target%%#*}"
    [[ -z "$target" ]] && continue
    case "$target" in http://*|https://*|mailto:*) continue ;; esac
    resolved=$(python3 -c 'import os,sys; print(os.path.normpath(os.path.join(sys.argv[1], sys.argv[2])))' "$dir" "$target")
    if [[ ! -e "$resolved" ]]; then
      if is_pending "$resolved"; then
        echo "warn: $f:$line -> $target (page not merged yet)"
        warnings=$((warnings + 1))
      else
        echo "ERROR: $f:$line -> $target (missing: $resolved)"
        errors=$((errors + 1))
      fi
    fi
  done < <(awk '
    /^```/ { infence = !infence; next }
    !infence {
      s = $0
      while (match(s, /\]\([^)[:space:]]+\)/)) {
        t = substr(s, RSTART + 2, RLENGTH - 3)
        printf "%d\t%s\n", NR, t
        s = substr(s, RSTART + RLENGTH)
      }
    }' "$f")
done

echo "doc links: $errors broken, $warnings pending"
[[ $errors -eq 0 ]]
