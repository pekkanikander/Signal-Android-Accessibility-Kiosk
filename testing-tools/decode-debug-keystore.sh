#!/usr/bin/env bash
set -euo pipefail
set -x

# Decode DEBUG_KEYSTORE_B64 (base64 of an Android debug keystore) into
# a temporary keystore file and write keystore.debug.properties so the
# Gradle build will use it for signing debug APKs.

if [ -z "${DEBUG_KEYSTORE_B64:-}" ]; then
  echo "DEBUG_KEYSTORE_B64 not set; skipping keystore decode"
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Repo root is one level up from testing-tools
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
KEYSTORE_FILE="$REPO_ROOT/debug.keystore"
PROPS_FILE="$REPO_ROOT/keystore.debug.properties"

mkdir -p "$(dirname "$KEYSTORE_FILE")"

# Decode into the keystore file at repository root so Gradle picks it up
echo "$DEBUG_KEYSTORE_B64" | base64 -d > "$KEYSTORE_FILE"
chmod 600 "$KEYSTORE_FILE" || true

# Write properties expected by app/build.gradle.kts
cat > "$PROPS_FILE" <<EOF
storeFile=debug.keystore
storePassword=android
keyAlias=androiddebugkey
keyPassword=android
EOF

echo "Wrote $KEYSTORE_FILE and $PROPS_FILE"

# Ensure properties file is readable by the build and not committed accidentally
chmod 600 "$KEYSTORE_FILE" || true
chmod 600 "$PROPS_FILE" || true

exit 0
