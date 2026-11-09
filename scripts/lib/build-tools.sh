#!/usr/bin/env bash
# Sourced helper: prints the Android build-tools directory to use (ANDROID_HOME, ANDROID_SDK_ROOT or local.properties
# sdk.dir). Pinned to BUILD_TOOLS_VERSION (default 36.0.0, the version CI installs): the runner image preinstalls
# several newer build-tools, and their apksigner/aapt2 output can differ from what verify-apk.sh parses. Falls back to
# the newest installed version only when the pinned one is missing (local machines), and says so. Exits non-zero when
# none is found.
BUILD_TOOLS_VERSION=${BUILD_TOOLS_VERSION:-36.0.0}
build_tools_dir() {
  local sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
  if [[ -z "$sdk" && -f local.properties ]]; then
    sdk=$(sed -n 's/^sdk.dir=//p' local.properties)
  fi
  local dir="${sdk:-/nonexistent}/build-tools/$BUILD_TOOLS_VERSION"
  if [[ ! -x "$dir/apksigner" ]]; then
    dir=$(find "${sdk:-/nonexistent}/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1 || true)
    if [[ -z "$dir" || ! -x "$dir/apksigner" ]]; then
      echo "No Android build-tools found (set ANDROID_HOME)" >&2
      return 1
    fi
    echo "build-tools $BUILD_TOOLS_VERSION not installed; using $(basename "$dir")" >&2
  fi
  echo "$dir"
}
