#!/usr/bin/env bash
set -euo pipefail
set -x

# Runs an act workflow (default: quick job) with --reuse and then fetches
# the artifact tarball (/tmp/quick-smoke.tar.gz) from the created container.
# Usage: ./testing-tools/act-run-fetch.sh [workflow.yml] [job]

WORKFLOW=${1:-.github/workflows/ci-quick.yml}
JOB=${2:-quick}
OUTDIR=${3:-./quick-smoke-host}

mkdir -p "${OUTDIR}"

ACT_LOG=/tmp/act-run-fetch.log

echo "Running act workflow ${WORKFLOW} job ${JOB} (logs -> ${ACT_LOG})"

# Ensure GITHUB_TOKEN is exported in the environment (act needs it)
# Caller should export from .env if needed: export "$(grep GITHUB_TOKEN .env)"

act --container-architecture linux/arm64 \
   --container-options "--privileged --add-host=host.docker.internal:host-gateway" \
   -P ubuntu-latest=signal-act-android:ci-arm64 \
   -j "${JOB}" -W "${WORKFLOW}" \
   --env DEBUG_KEYSTORE_B64="$(base64 -i ~/.android/debug.keystore | tr -d '\n')" \
   --env GITHUB_TOKEN="$(grep GITHUB_TOKEN .env | cut -d '=' -f 2)" \
   --env CI=true --env ADB_SERVER_SOCKET=tcp:host.docker.internal:5037 \
   --pull=false --reuse 2>&1 | tee "${ACT_LOG}"

echo "Searching for most-recent container for image signal-act-android:ci-arm64"
container=$(docker ps -a --filter "ancestor=signal-act-android:ci-arm64" --format '{{.ID}} {{.CreatedAt}} {{.Names}}' | head -n1 | awk '{print $1}') || true

if [ -z "${container}" ]; then
  echo "No container found for image; trying to find any recent act container"
  container=$(docker ps -a --format '{{.ID}} {{.Image}} {{.CreatedAt}} {{.Names}}' | grep act | head -n1 | awk '{print $1}') || true
fi

if [ -z "${container}" ]; then
  echo "ERROR: could not find a container to copy artifacts from. See ${ACT_LOG}" >&2
  exit 2
fi

echo "Found container ${container}; attempting to copy /tmp/quick-smoke.tar.gz"
docker cp "${container}:/tmp/quick-smoke.tar.gz" "${OUTDIR}/" || true

if [ -f "${OUTDIR}/quick-smoke.tar.gz" ]; then
  echo "Extracting artifact tarball into ${OUTDIR}"
  tar -xzf "${OUTDIR}/quick-smoke.tar.gz" -C "${OUTDIR}" || true
  echo "Artifacts available in ${OUTDIR}"
else
  echo "No tarball found in container; attempting to docker cp workspace quick-smoke dir"
  docker cp "${container}:/Users/pnr/Documents/Personal/Ilmari/Signal/Signal-Android-Accessibility-Kiosk/quick-smoke" "${OUTDIR}/" || true
fi

echo "Listing ${OUTDIR}"
ls -la "${OUTDIR}" || true

echo "Done. Check ${OUTDIR} for detailed act output."

exit 0
