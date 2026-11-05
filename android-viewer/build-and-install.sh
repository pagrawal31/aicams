#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
ENV_FILE="$SCRIPT_DIR/.env.local"
STUN_ONLY=false

if [[ "${1:-}" == "--stun-only" ]]; then
  STUN_ONLY=true
elif [[ $# -gt 0 ]]; then
  echo "Usage: $0 [--stun-only]" >&2
  exit 2
fi

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

export AICAMS_STUN_URLS="${AICAMS_STUN_URLS:-stun:stun.l.google.com:19302}"

if [[ "$STUN_ONLY" == true ]]; then
  export AICAMS_TURN_URLS=""
  export AICAMS_TURN_USERNAME=""
  export AICAMS_TURN_PASSWORD=""
  echo "ICE test mode: STUN only (TURN disabled)."
else
  if [[ -n "${AICAMS_TURN_URLS:-}" ]] && { [[ -z "${AICAMS_TURN_USERNAME:-}" ]] || [[ -z "${AICAMS_TURN_PASSWORD:-}" ]]; }; then
    echo "TURN URLs are configured, but the TURN username or password is missing." >&2
    exit 1
  fi
  echo "ICE mode: STUN with TURN fallback."
fi

cd "$SCRIPT_DIR"

echo "Building the AICams Viewer debug APK..."
./gradlew assembleDebug

mapfile_supported=false
if command -v mapfile >/dev/null 2>&1; then
  mapfile_supported=true
fi

if [[ "$mapfile_supported" == true ]]; then
  mapfile -t devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
else
  devices=()
  while IFS= read -r serial; do
    devices+=("$serial")
  done < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
fi

if (( ${#devices[@]} == 0 )); then
  echo "No authorized Android devices are connected." >&2
  echo "Connect a device with USB debugging enabled, accept its authorization prompt, and rerun this script." >&2
  exit 1
fi

for serial in "${devices[@]}"; do
  echo "Installing on $serial..."
  adb -s "$serial" install -r "$APK_PATH"
done

echo "Installed AICams Viewer on ${#devices[@]} connected device(s)."
