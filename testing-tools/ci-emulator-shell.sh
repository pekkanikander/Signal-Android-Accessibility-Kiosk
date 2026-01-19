#!/usr/bin/env bash
set -euo pipefail
# API_LEVEL=${API_LEVEL:-31}
# ARCH=${ARCH:-arm64-v8a}
# TARGET=${TARGET:-google_apis_playstore}
# AVD_NAME=${AVD_NAME:-test}
# PORT=${PORT:-5554}
# EMULATOR_OPTS=${EMULATOR_OPTS:--no-window -no-audio -no-boot-anim}

ARCH=$(uname -m)
export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}

if [ "$ARCH" = "aarch64" ]; then
  ARCH="arm64-v8a"
  PLATFORM_TOOLS_PATH="/usr/lib/android-sdk/platform-tools"
else
  ARCH="x86_64"
  PLATFORM_TOOLS_PATH="${ANDROID_HOME}/platform-tools"
fi

# XXX: Replace 35.0.0 with a variable
export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:/opt/android-sdk/build-tools/35.0.0:${PLATFORM_TOOLS_PATH}:$ANDROID_HOME/emulator:$PATH"

echo "=== ci-emulator-shell: env ==="
echo "ANDROID_HOME=${ANDROID_HOME:-}"
echo "PATH=${PATH:-}"

echo "=== ci-emulator-shell: reboot emulator ==="
adb reboot
sleep 10

echo "=== ci-emulator-shell: wait for adb device ==="
SECONDS=0
TIMEOUT=${EMULATOR_BOOT_TIMEOUT:-600}
while [ $SECONDS -lt $TIMEOUT ]; do
  if adb shell getprop sys.boot_completed 1>/dev/null 2>/dev/null; then
    if [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; then
      echo "emulator is running"
      break
    fi
  fi
  sleep 2
done

if [ $SECONDS -ge $TIMEOUT ]; then
  echo "Timeout waiting for emulator (logs follow):"
  tail -n +1 /tmp/emulator-*.log
  exit 1
fi

echo "=== ci-emulator-shell: post-boot settings ==="
adb shell input keyevent 82
adb shell settings put global window_animation_scale 0.0
adb shell settings put global transition_animation_scale 0.0
adb shell settings put global animator_duration_scale 0.0

echo "=== ci-emulator-shell: run smoke script ==="
chmod +x testing-tools/ci-smoke.sh
./testing-tools/ci-smoke.sh

echo "=== ci-emulator-shell: done ==="
