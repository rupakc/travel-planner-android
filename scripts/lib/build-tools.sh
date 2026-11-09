#!/usr/bin/env bash
# Sourced helper: prints the newest installed Android build-tools directory (ANDROID_HOME, ANDROID_SDK_ROOT or
# local.properties sdk.dir). Exits non-zero when none is found.
build_tools_dir() {
  local sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
  if [[ -z "$sdk" && -f local.properties ]]; then
    sdk=$(sed -n 's/^sdk.dir=//p' local.properties)
  fi
  local dir
  dir=$(find "${sdk:-/nonexistent}/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1 || true)
  if [[ -z "$dir" || ! -x "$dir/apksigner" ]]; then
    echo "No Android build-tools found (set ANDROID_HOME)" >&2
    return 1
  fi
  echo "$dir"
}
