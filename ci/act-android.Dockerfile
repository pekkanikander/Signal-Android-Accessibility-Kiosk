FROM ghcr.io/catthehacker/ubuntu:act-latest

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      android-sdk-platform-tools \
      android-tools-adb \
      nodejs \
      npm \
      unzip \
      openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/emulator:/usr/bin:${PATH}"

# Ensure Android SDK layout exists and is writable
RUN mkdir -p ${ANDROID_HOME} ${ANDROID_HOME}/cmdline-tools ${ANDROID_HOME}/platform-tools ${ANDROID_HOME}/emulator /root/.android/avd \
    && chown -R root:root ${ANDROID_HOME} /root/.android

# Pre-cache Gradle wrapper distribution to speed up ./gradlew on first run
COPY gradle/wrapper/gradle-wrapper.properties /tmp/gradle-wrapper.properties
RUN set -euo pipefail && \
    GRADLE_PROPS=/tmp/gradle-wrapper.properties && \
    if [ -f "$GRADLE_PROPS" ]; then \
      DIST_URL=$(awk -F= '/distributionUrl/ {print $2}' "$GRADLE_PROPS" | tr -d '\r') && \
      DIST_FILE=$(basename "$DIST_URL") && \
      DIST_DIR=/root/.gradle/wrapper/dists/$(basename "$DIST_URL" .zip) && \
      mkdir -p "$DIST_DIR" && \
      echo "Pre-downloading Gradle distribution: $DIST_URL" && \
      wget -q -O /tmp/$DIST_FILE "$DIST_URL" && \
      unzip -q /tmp/$DIST_FILE -d "$DIST_DIR" || true && \
      rm -f /tmp/$DIST_FILE; \
    fi

# Install Android commandline tools so sdkmanager/avdmanager are available
RUN set -eux; \
  SDK_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-12266719_latest.zip"; \
  wget -q -O /tmp/cmdline-tools.zip "$SDK_TOOLS_URL"; \
  unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools; \
  mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest || true; \
  rm -f /tmp/cmdline-tools.zip; \
  chmod -R a+rX ${ANDROID_HOME};
