#!/usr/bin/env bash
set -u

echo "=== DEBUG: Environment ==="
env | sort

echo
echo "=== DEBUG: Android vars ==="
echo "ANDROID_HOME=${ANDROID_HOME:-}" 
echo "ANDROID_AVD_HOME=${ANDROID_AVD_HOME:-}" 
echo "PATH=${PATH:-}" 

echo
echo "=== DEBUG: ANDROID_HOME contents ==="
if [ -n "${ANDROID_HOME:-}" ]; then
  ls -la "$ANDROID_HOME" 2>/dev/null || echo "(ANDROID_HOME missing or inaccessible)"
else
  echo "ANDROID_HOME not set"
fi

echo
echo "=== DEBUG: cmdline-tools, emulator, platform-tools ==="
ls -la "${ANDROID_HOME:-}/cmdline-tools" 2>/dev/null || true
ls -la "${ANDROID_HOME:-}/emulator" 2>/dev/null || true
ls -la "${ANDROID_HOME:-}/platform-tools" 2>/dev/null || true

echo
echo "=== DEBUG: sdkmanager & avdmanager ==="
if command -v sdkmanager >/dev/null 2>&1; then
  echo "sdkmanager: $(command -v sdkmanager)"
  sdkmanager --list 2>&1 | sed -n '1,200p' || true
else
  echo "sdkmanager not found"
fi

if command -v avdmanager >/dev/null 2>&1; then
  echo "avdmanager: $(command -v avdmanager)"
  avdmanager list target 2>&1 || true
else
  echo "avdmanager not found"
fi

echo
echo "=== DEBUG: node & npm ==="
command -v node >/dev/null 2>&1 && node --version || echo "node missing"
command -v npm >/dev/null 2>&1 && npm --version || echo "npm missing"

echo
echo "=== END DEBUG ==="

exit 0


