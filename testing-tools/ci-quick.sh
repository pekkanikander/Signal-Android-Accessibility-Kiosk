#!/usr/bin/env bash
set -euo pipefail

DEVICE=${1:-emulator-5554}
OUT_DIR="/tmp/quick-smoke"
mkdir -p "${OUT_DIR}"

echo "Starting CI quick smoke for device ${DEVICE}"

# Ensure binfmt / qemu enabled if needed
/usr/local/bin/ensure-binfmt-x86_64.sh || true

pushd app > /dev/null
  # Build only the variants required for quick tests
  ../gradlew assemblePlayProdDebug assemblePlayProdInstrumentation --no-daemon --console=plain \
    -Dorg.gradle.jvmargs="-Xmx7g -Xms2g -XX:+UseStringDeduplication" -Dorg.gradle.configureondemand=true \
    -Dorg.gradle.vfs.watch=false
popd > /dev/null

# Find APKs
APP_APK=$(find app/build/outputs/apk -type f -name "*play-prod*-universal-debug-*.apk" | head -n1 || true)
INSTR_APK=$(find app/build/outputs/apk -type f -name "*instrumentation*universal*.apk" | head -n1 || true)

if [ -z "${APP_APK}" ]; then
  echo "App APK not found, listing outputs:" >&2
  find app/build/outputs/apk -maxdepth 3 -type f -print >&2 || true
  exit 1
fi

echo "Installing APKs"
adb -s "${DEVICE}" install -r "${APP_APK}" || true
if [ -n "${INSTR_APK}" ]; then
  adb -s "${DEVICE}" install -r "${INSTR_APK}" || true
fi

adb -s "${DEVICE}" wait-for-device 2>/dev/null || true

OUT_DIR_BASE="/tmp/quick-smoke"
mkdir -p "${OUT_DIR_BASE}"

echo "Running AccessibilityEspressoE2E"
adb -s "${DEVICE}" shell am instrument -w -r -e class org.thoughtcrime.securesms.accessibility.AccessibilityEspressoE2E org.thoughtcrime.securesms.instrumentation.test/org.thoughtcrime.securesms.testing.SignalTestRunner > "${OUT_DIR_BASE}/espresso_e2e.txt" 2>&1 || true

echo "Running AccessibilityGestureDetectorInstrTest"
adb -s "${DEVICE}" shell am instrument -w -r -e class org.thoughtcrime.securesms.accessibility.AccessibilityGestureDetectorInstrTest org.thoughtcrime.securesms.instrumentation.test/org.thoughtcrime.securesms.testing.SignalTestRunner > "${OUT_DIR_BASE}/gesture_instr.txt" 2>&1 || true

echo "Running AccessibilityGestureE2E"
adb -s "${DEVICE}" shell am instrument -w -r -e class org.thoughtcrime.securesms.accessibility.AccessibilityGestureE2E org.thoughtcrime.securesms.instrumentation.test/org.thoughtcrime.securesms.testing.SignalTestRunner > "${OUT_DIR_BASE}/gesture_e2e.txt" 2>&1 || true

echo "Running AccessibilityGestureRouterUnitTest (JVM)"
pushd app > /dev/null
  # Run unit tests from inside the app directory (avoid fully-qualified project path)
  # Use the flavor-specific unit test task to avoid ambiguous task names
  # Run only our new unit test to avoid executing the full baseline/Robolectric suite
  ../gradlew testPlayProdDebugUnitTest --no-daemon --console=plain --tests "org.thoughtcrime.securesms.accessibility.AccessibilityGestureRouterUnitTest" -Dorg.gradle.jvmargs="-Xmx3g" || true
popd > /dev/null

adb -s "${DEVICE}" logcat -d > "${OUT_DIR_BASE}/logcat.txt" || true

# Create tarball for retrieval by act-run-fetch.sh
set +e
tar -czf "/tmp/quick-smoke.tar.gz" -C "${OUT_DIR_BASE}" . 2>/dev/null || true
set -e

echo "Created /tmp/quick-smoke.tar.gz"

exit 0
