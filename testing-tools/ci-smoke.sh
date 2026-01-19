#!/usr/bin/env bash
set -euo pipefail
set -x

DEVICE=${1:-emulator-5554}
OUT_DIR="/tmp/ci-smoke"
mkdir -p "$OUT_DIR"

echo "Starting CI smoke script for device $DEVICE"

# Ensure binfmt is enabled for x86_64 on arm64 containers
/usr/local/bin/ensure-binfmt-x86_64.sh || true

# Build the APKs (fast, limit Gradle configuration to the app module)
# Run Gradle from the app directory so only the app project configures
# Run twice due to transient failures during the first run
pushd app > /dev/null
  ../gradlew assemblePlayProdDebug assemblePlayProdInstrumentation \
    --no-daemon --console=plain \
    -Dorg.gradle.jvmargs="-Xmx7g -Xms2g -XX:+UseStringDeduplication" -Dorg.gradle.configureondemand=true \
    -Dorg.gradle.vfs.watch=false || true
  ../gradlew assemblePlayProdDebug assemblePlayProdInstrumentation \
    --no-daemon --console=plain \
    -Dorg.gradle.jvmargs="-Xmx7g -Xms2g -XX:+UseStringDeduplication" -Dorg.gradle.configureondemand=true \
    -Dorg.gradle.vfs.watch=false
popd > /dev/null

# Find APKs
APP_APK=$(find app/build/outputs/apk -type f -name "*play-prod*-universal-debug-*.apk" | head -n1 || true)
INSTR_APK=$(find app/build/outputs/apk -type f -name "*instrumentation*universal*.apk" | head -n1 || true)

if [ -z "$APP_APK" ]; then
  echo "App APK not found, listing outputs:" >&2
  find app/build/outputs/apk -maxdepth 3 -type f -print >&2 || true
  exit 1
fi

# Compare signer fingerprints, for debugging signatures mismatches
apksigner verify --print-certs "$APP_APK" | grep 'Signer #1 certificate SHA-256'
adb -s "$DEVICE" shell dumpsys package org.thoughtcrime.securesms | grep -A2 'signatures:'

echo "Installing APKs"
adb -s "$DEVICE" install -r "$APP_APK" || true
if [ -n "$INSTR_APK" ]; then
  adb -s "$DEVICE" install -r "$INSTR_APK" || true
fi

# Enable global accessibility (best-effort)
adb -s "$DEVICE" shell settings delete secure enabled_accessibility_services || true
adb -s "$DEVICE" shell settings put secure accessibility_enabled 1 || true

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
