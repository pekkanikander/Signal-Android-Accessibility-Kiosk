# Minimal, platform‑agnostic Android SDK image for **local CI with act** (amd64 & arm64)
#
# What this image is:
# - Purpose‑built for **local** builds/tests with `act` (not a generic GitHub Actions runner image).
# - Works on both **amd64** and **arm64** hosts (Apple Silicon, etc.).
# - Installs JDK 17 + recent Node.js, downloads **official** Android commandline‑tools from Google,
#   and installs only the requested SDK components (platform‑tools, platforms, build‑tools).
# - Pre‑warms the project’s **Gradle Wrapper distribution** so first run does not download Gradle.
# - Provides **native adb** via Ubuntu packages (handy on arm64), coexisting with Google’s platform‑tools.
#
# Important architecture notes:
# - **amd64:** normal path. AGP will download its matching **x86‑64 aapt2** from Maven and run natively.
# - **arm64:** we **do not** install distro `aapt2` (too old for API 35). Instead we enable **user‑mode QEMU**
#   and install minimal `:amd64` runtime libs so the official **x86‑64 aapt2** (downloaded by AGP) runs
#   transparently under emulation. This avoids Debian/Ubuntu pinning games and fixes the API‑35 parser errors.
#
# Optional pieces:
# - Emulator install can be enabled via `--build-arg INSTALL_EMULATOR=true` but is only attempted on **amd64**.
#
# PATH layout:
# - `/usr/bin` first (so distro `adb` is available), then SDK `cmdline-tools/latest/bin`, then SDK `platform-tools`.
#
# Scope & trade‑offs:
# - Tailored for this repository’s smoke/integration tests; prioritises **speed and determinism** over generality.
# - No Debian sid/non‑free repos; no arm64 `aapt2` install. The arm64 flow relies on AGP’s Maven aapt2 via QEMU.

# Docker Image build:
#  ARM64: docker buildx build --progress plain --platform linux/arm64 -t signal-act-android:ci-arm64 -f ci/act-android.Dockerfile --load .
#  AMD64: docker buildx build --progress plain --platform linux/amd64 -t signal-act-android:ci-amd64 -f ci/act-android.Dockerfile --load .
#
# Act run:
#  ARM64: act --container-architecture linux/arm64 --container-options "--privileged --add-host=host.docker.internal:host-gateway"  \
#       -P ubuntu-latest=signal-act-android:ci-arm64 -j smoke -W .github/workflows/ci-smoke.yml --pull=false --env CI=true \
#       --env DEBUG_KEYSTORE_B64="$(base64 -i ~/.android/debug.keystore | tr -d '\n')" \
#       --env GITHUB_TOKEN="$GITHUB_TOKEN"   --env ADB_SERVER_SOCKET=tcp:host.docker.internal:5037
#  AMD64: act --container-architecture linux/amd64 --container-options "--privileged --add-host=host.docker.internal:host-gateway"  \
#       -P ubuntu-latest=signal-act-android:ci-amd64 -j smoke -W .github/workflows/ci-smoke.yml --pull=false --env CI=true \
#       --env DEBUG_KEYSTORE_B64="$(base64 -i ~/.android/debug.keystore | tr -d '\n')" \
#       --env GITHUB_TOKEN="$GITHUB_TOKEN"   --env ADB_SERVER_SOCKET=tcp:host.docker.internal:5037

FROM ubuntu:24.04

ARG DEBIAN_FRONTEND=noninteractive

# --- Tunables ---------------------------------------------------------------
# SDK root
ARG ANDROID_SDK_ROOT=/opt/android-sdk
# Which SDK packages to install (space-separated sdkmanager specs)
ARG ANDROID_PACKAGES="platform-tools platforms;android-35 build-tools;35.0.0"
# commandline-tools download URL (override when Google bumps the version)
ARG CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
# Install the Emulator too? (amd64 only)
ARG INSTALL_EMULATOR=false
ARG NODE_MAJOR=22

# --- Base OS deps -----------------------------------------------------------
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      unzip \
      git \
      openjdk-17-jdk-headless; \
    rm -rf /var/lib/apt/lists/*

# Some Android build/SDK tools and auxiliary scripts expect a recent Node/npm; we install LTS via NodeSource.
# --- Node.js (LTS by default via NodeSource) --------------------------------
RUN set -eux; \
    curl -fsSL https://deb.nodesource.com/setup_${NODE_MAJOR}.x | bash -; \
    apt-get install -y --no-install-recommends nodejs; \
    rm -rf /var/lib/apt/lists/*; \
    node --version && npm --version; \
    corepack enable || true

# --- Ubuntu/Debian packaged Android tools (provides also arm64-native adb) ------
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      android-tools-adb \
      android-sdk-platform-tools; \
    rm -rf /var/lib/apt/lists/*

# --- x86_64 user-mode emulation on arm64 (to run AGP's aapt2) --------------
# Rationale: API 35 framework resources require a newer aapt2 than Ubuntu arm64 ships. We run AGP’s x86‑64 aapt2 via QEMU.
RUN set -eux; \
    if [ "$(dpkg --print-architecture)" = "arm64" ]; then \
      dpkg --add-architecture amd64; \
      . /etc/os-release; \
      # Remove deb822 sources that can override arch filters
      rm -f /etc/apt/sources.list.d/ubuntu.sources || true; \
      # Replace default sources with explicit arch-qualified entries (arm64 -> ports)
      mv /etc/apt/sources.list /etc/apt/sources.list.orig || true; \
      printf "deb [arch=arm64] http://ports.ubuntu.com/ubuntu-ports %s main restricted universe multiverse\n" "$UBUNTU_CODENAME" > /etc/apt/sources.list; \
      printf "deb [arch=arm64] http://ports.ubuntu.com/ubuntu-ports %s-updates main restricted universe multiverse\n" "$UBUNTU_CODENAME" >> /etc/apt/sources.list; \
      printf "deb [arch=arm64] http://ports.ubuntu.com/ubuntu-ports %s-security main restricted universe multiverse\n" "$UBUNTU_CODENAME" >> /etc/apt/sources.list; \
      printf "deb [arch=arm64] http://ports.ubuntu.com/ubuntu-ports %s-backports main restricted universe multiverse\n" "$UBUNTU_CODENAME" >> /etc/apt/sources.list; \
      # Add normal amd64 mirrors for the foreign architecture
      printf "deb [arch=amd64] http://archive.ubuntu.com/ubuntu %s main restricted universe multiverse\n" "$UBUNTU_CODENAME" > /etc/apt/sources.list.d/ubuntu-amd64.list; \
      printf "deb [arch=amd64] http://archive.ubuntu.com/ubuntu %s-updates main restricted universe multiverse\n" "$UBUNTU_CODENAME" >> /etc/apt/sources.list.d/ubuntu-amd64.list; \
      printf "deb [arch=amd64] http://security.ubuntu.com/ubuntu %s-security main restricted universe multiverse\n" "$UBUNTU_CODENAME" >> /etc/apt/sources.list.d/ubuntu-amd64.list; \
      apt-get clean; \
      apt-get update; \
      # Install emulation and minimal amd64 runtime
      apt-get install -y --no-install-recommends qemu-user-static binfmt-support libc6:amd64 libstdc++6:amd64 zlib1g:amd64; \
      # Show effective sources for debugging
      grep -R "^deb" /etc/apt/sources.list* /etc/apt/sources.list.d || true; \
      rm -rf /var/lib/apt/lists/*; \
    fi

# --- Runtime helper to enable binfmt for x86_64 on arm64 containers ---------
RUN cat >/usr/local/bin/ensure-binfmt-x86_64.sh <<'EOF'
#!/usr/bin/env bash
set -eu pipefail
# Only relevant on arm64 containers
if [ "$(dpkg --print-architecture 2>/dev/null || echo '')" != "arm64" ]; then
  exit 0
fi
# Try to mount binfmt_misc and enable qemu-x86_64 (requires --privileged)
if ! mountpoint -q /proc/sys/fs/binfmt_misc 2>/dev/null; then
  mount -t binfmt_misc binfmt_misc /proc/sys/fs/binfmt_misc 2>/dev/null || true
fi
update-binfmts --enable qemu-x86_64 >/dev/null 2>&1 || true
EOF
RUN chmod +x /usr/local/bin/ensure-binfmt-x86_64.sh

# --- Pre-warm Gradle Wrapper distribution (no first-run download) -----------
# Copies only the wrapper bits and runs `./gradlew --version` to populate
# /root/.gradle/wrapper/dists with the project’s exact Gradle distribution.
COPY gradlew /tmp/project/gradlew
COPY gradle/wrapper/gradle-wrapper.jar /tmp/project/gradle/wrapper/gradle-wrapper.jar
COPY gradle/wrapper/gradle-wrapper.properties /tmp/project/gradle/wrapper/gradle-wrapper.properties
RUN set -eux; \
    chmod +x /tmp/project/gradlew; \
    (cd /tmp/project; ./gradlew --no-daemon --version); \
    rm -rf /tmp/project

# --- Setup Android SDK root and PATH -------------------------
ENV ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT} \
    ANDROID_HOME=${ANDROID_SDK_ROOT} \
    PATH=/usr/bin:/usr/lib/android-sdk/platform-tools:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}

# --- Install Android commandline-tools from Google -------------------------
RUN set -eux; \
    mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"; \
    curl -fL --retry 5 --retry-delay 2 --retry-connrefused "${CMDLINE_TOOLS_URL}" -o /tmp/cmdline-tools.zip; \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/; \
    # Google zip extracts to ./cmdline-tools; move under .../latest
    mv /tmp/cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/latest"; \
    rm -f /tmp/cmdline-tools.zip; \
    # Smoke: show sdkmanager version
    sdkmanager --version

# --- Install required Android SDK packages ----------------------------------------
RUN set -eux; \
    yes | sdkmanager --licenses >/dev/null; \
    sdkmanager ${ANDROID_PACKAGES}; \
    # Install emulator only on amd64 and when requested
    if [ "${INSTALL_EMULATOR}" = "true" ]; then \
      if [ "$(dpkg --print-architecture)" = "amd64" ]; then \
        sdkmanager "emulator"; \
      else \
        echo "Emulator is not supported on other architectures than amd64" >&2; \
        exit 1; \
      fi; \
    fi; \
    # List final state for debugging
    sdkmanager --list | head -n 12 || true

# --- Defaults ---------------------------------------------------------------
# Keep working directory clean; caller pipeline supplies project files via bind mount or checkout step
WORKDIR /workspace

# Ensure binfmt is enabled for x86_64 on arm64 containers
CMD ["bash", "-lc", "/usr/local/bin/ensure-binfmt-x86_64.sh || true; java -version && echo SDK:\ $ANDROID_SDK_ROOT && sdkmanager --version && adb version || true"]
