#!/usr/bin/env bash
set -euo pipefail
API_LEVEL=${API_LEVEL:-31}
ARCH=${ARCH:-arm64-v8a}
TARGET=${TARGET:-google_apis}
AVD_NAME=${AVD_NAME:-test}
PORT=${PORT:-5554}
EMULATOR_OPTS=${EMULATOR_OPTS:--no-window -no-audio -no-boot-anim}

echo "=== ci-emulator-shell: env ==="
echo "ANDROID_HOME=${ANDROID_HOME:-}" || true
echo "PATH=${PATH:-}" || true

export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

which sdkmanager || { echo "sdkmanager not found"; exit 1; }

echo "=== Accept SDK licenses ==="
yes | sdkmanager --licenses || true

echo "=== Install required SDK packages ==="
sdkmanager "platform-tools" "emulator" "platforms;android-${API_LEVEL}" "build-tools;35.0.0" "system-images;android-${API_LEVEL};${TARGET};${ARCH}"

echo "=== Create AVD if missing ==="
mkdir -p "$HOME/.android/avd"
if [ ! -d "$HOME/.android/avd/${AVD_NAME}.avd" ]; then
  echo "no" | avdmanager create avd --force -n "${AVD_NAME}" --abi "${TARGET}/${ARCH}" --package "system-images;android-${API_LEVEL};${TARGET};${ARCH}"
fi

echo "=== Start emulator ==="
${ANDROID_HOME}/emulator/emulator -port ${PORT} -avd "${AVD_NAME}" ${EMULATOR_OPTS} -accel off > /tmp/emulator-${PORT}.log 2>&1 &
EMU_PID=$!
echo "emulator pid=${EMU_PID}"

echo "=== Wait for adb device ==="
SECONDS=0
TIMEOUT=${EMULATOR_BOOT_TIMEOUT:-600}
while [ $SECONDS -lt $TIMEOUT ]; do
  if adb -s emulator-${PORT} shell getprop sys.boot_completed 1>/dev/null 2>/dev/null; then
    if [ "$(adb -s emulator-${PORT} shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; then
      echo "emulator booted"
      break
    fi
  fi
  sleep 2
done

if [ $SECONDS -ge $TIMEOUT ]; then
  echo "Timeout waiting for emulator (logs follow):"
  tail -n +1 /tmp/emulator-${PORT}.log || true
  exit 1
fi

echo "=== Post-boot settings ==="
adb -s emulator-${PORT} shell input keyevent 82 || true
adb -s emulator-${PORT} shell settings put global window_animation_scale 0.0 || true
adb -s emulator-${PORT} shell settings put global transition_animation_scale 0.0 || true
adb -s emulator-${PORT} shell settings put global animator_duration_scale 0.0 || true

echo "=== Run smoke script ==="
chmod +x testing-tools/ci-smoke.sh || true
./testing-tools/ci-smoke.sh emulator-${PORT} || true

echo "=== Kill emulator ==="
adb -s emulator-${PORT} emu kill || true
wait ${EMU_PID} || true

echo "=== Collect logs ==="
cp /tmp/emulator-${PORT}.log /tmp/ci-emulator-${PORT}.log || true

echo "Done"


