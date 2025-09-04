#!/usr/bin/env bash
set -euo pipefail

DEVICE=${1:-emulator-5554}
OUT_DIR="/tmp/ci-smoke"
mkdir -p "$OUT_DIR"

echo "Starting CI smoke script for device $DEVICE"

# Build the APKs (fast, will use Gradle cache)
./gradlew :app:assemblePlayProdDebug :app:assemblePlayProdInstrumentation --no-daemon --console=plain

# Find APKs
APP_APK=$(find app/build/outputs/apk -type f -name "*play-prod*-universal-debug-*.apk" | head -n1 || true)
INSTR_APK=$(find app/build/outputs/apk -type f -name "*instrumentation*universal*.apk" | head -n1 || true)

if [ -z "$APP_APK" ]; then
  echo "App APK not found, listing outputs:" >&2
  find app/build/outputs/apk -maxdepth 3 -type f -print >&2 || true
  exit 1
fi

echo "Installing APKs"
adb -s "$DEVICE" install -r "$APP_APK" || true
if [ -n "$INSTR_APK" ]; then
  adb -s "$DEVICE" install -r "$INSTR_APK" || true
fi

# Enable global accessibility (best-effort)
adb -s "$DEVICE" shell settings put secure enabled_accessibility_services ""
adb -s "$DEVICE" shell settings put secure accessibility_enabled 1 || true

# If adb is not available in the container, try to install a lightweight adb package (for act local runs)
if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH; attempting to install android-tools-adb (requires apt)"
  if command -v apt-get >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -y && apt-get install -y android-tools-adb || true
  else
    echo "apt-get not available; cannot install adb. Local act runs may fail." >&2
  fi
fi

# Re-check device connectivity after ensuring adb
adb -s "$DEVICE" wait-for-device 2>/dev/null || true

# Run the Espresso E2E via instrumentation
echo "Running AccessibilityEspressoE2E"
adb -s "$DEVICE" shell am instrument -w -r -e class org.thoughtcrime.securesms.accessibility.AccessibilityEspressoE2E org.thoughtcrime.securesms.instrumentation.test/org.thoughtcrime.securesms.testing.SignalTestRunner > "$OUT_DIR/espresso_e2e.txt" 2>&1 || true

# Run TalkBack UI test method (if instrumentation installed)
echo "Running TalkBack UI test method"
adb -s "$DEVICE" shell am instrument -w -r -e class org.thoughtcrime.securesms.accessibility.AccessibilityTalkBackUiTest#mainScreen_hasAccessibleConversationList org.thoughtcrime.securesms.instrumentation.test/org.thoughtcrime.securesms.testing.SignalTestRunner > "$OUT_DIR/talkback_ui.txt" 2>&1 || true

# Collect logs
adb -s "$DEVICE" logcat -d > "$OUT_DIR/logcat.txt" || true
adb -s "$DEVICE" shell uiautomator dump /sdcard/window_dump.xml || true
adb -s "$DEVICE" pull /sdcard/window_dump.xml "$OUT_DIR/" >/dev/null 2>&1 || true

echo "Artifacts written to $OUT_DIR"
ls -la "$OUT_DIR"

exit 0


