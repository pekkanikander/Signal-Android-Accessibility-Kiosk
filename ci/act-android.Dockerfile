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

ENV PATH="/usr/bin:${PATH}"

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
