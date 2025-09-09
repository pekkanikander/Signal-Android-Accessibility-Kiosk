Title: Guidance and Examples for GitHub Actions + act for Android emulator "smoke" CI

Context:
- Repo: Signal-Android-Accessibility-Kiosk
- Goal: Create a reliable CI "smoke" job that runs a small set of instrumentation tests (Espresso and UiAutomator/TalkBack checks) and collects useful artifacts.
- Current local attempts: added `.github/workflows/ci-smoke.yml` and `testing-tools/ci-smoke.sh` that build APKs, start an emulator, install APKs, run a couple of instrumentation tests, and collect logs.
- Local testing uses `act` (v0.2.79) on macOS (Apple Silicon) with Docker/Orb running. act fails because the emulator runner inside the act container cannot find `adb`/Android SDK and sometimes tries to clone Actions repositories and hits auth issues unless GITHUB_TOKEN is supplied.

Questions and requests for examples / best practices:

1) Canonical GitHub Actions workflow for Android emulator smoke tests
   - Provide a minimal, robust GitHub Actions YAML that: sets up JDK, installs Android SDK packages, starts an AVD (headless), restores a snapshot if present, installs APKs, runs a short set of instrumentation tests (by class/method), collects `logcat`, UI dump, and test HTML/XML artifacts, and uploads artifacts.
   - Show working patterns for `reactivecircus/android-emulator-runner` usage (emulator config, snapshot load, timeout tuning, disabling hardware acceleration) and for `actions/cache` or alternative caching for Gradle and SDK packages.

2) act-specific guidance for Android workflows
   - Typical pitfalls when running emulator workflows with `act` (lack of adb, missing SDK tooling, container architecture on Apple Silicon, cloning private action repos, authentication with GITHUB_TOKEN). Provide a working `act` invocation example and `.actrc` or `act` flags that reliably run Android emulator jobs on macOS with Apple Silicon.
   - If `act` cannot run the emulator action reliably, suggest container images (GHCR or Docker Hub) that already include `adb` and basic Android SDK tools and are known to work with `act` (prefer x86_64 images for compatibility). Example images and `-P ubuntu-latest=...` mapping suggestions.

3) Best-practice for short smoke runs vs full test matrix
   - Which tests to include in a smoke job (instrumentation test classes/methods that validate onboarding, conversation list, gesture detection, and TalkBack announcements). How to configure instrumentation runner args to avoid clearing app data (preserve registration) and run quickly.
   - Time budget and recommended timeout settings for emulator boot and test execution.

4) Handling action cloning/auth issues in act
   - Recommendations for avoiding remote `git clone` of action repos inside the container (pre-populate `~/.cache/act` or vendor actions into the repo under `.github/actions`), or using `GITHUB_TOKEN` minimal scopes safely for local act runs.

5) Snapshot strategy
   - How to prepare a canonical emulator snapshot (e.g., `signal_preconfigured`) that has the app installed and a pre-registered account, and how to restore it reliably in both GitHub Actions and act. Exact commands for `emulator -avd` and `adb emu` snapshot load that are suitable for automation.

6) Artifacts and debugging tips
   - Which artifacts to collect and how to structure them for quick triage: `logcat`, `window_dump.xml`, test HTML/XML reports, screenshots, emulator console output, and the built APKs. Provide sample `actions/upload-artifact` usages and retention suggestions.

7) Example minimal `ci-smoke.sh` wrapper for reuse locally and in GH Actions
   - If you can provide a proven shell script snippet that builds the app, installs APKs, manipulates accessibility flags, runs instrumentations, collects artifacts, and returns non-zero on failures.

Deliverables requested:
- A sample `ci-smoke.yml` workflow tailored for Android emulator smoke tests (complete and working on GitHub hosted runners).
- A sample `.actrc` or act invocation and the recommended runner image for local testing on macOS Apple Silicon.
- A short checklist for preparing the emulator snapshot and the commands to create/load it in CI.
- Recommended minimal scopes for a PAT used with act locally and instructions for safely managing it (no secrets in repo).

Additional notes:
- We prefer not to modify baseline app code for CI; all CI & test-support changes should be additive (workflows, scripts, docs).
- Performance: smoke runs should be fast (< 10 minutes) on hosted runners. Full connected/instrumentation test suites will run separately (nightly/long-running).

Thank you — please include practical examples and concise commands where possible.


---

## Answer — CI “smoke” for Android emulator (Actions + act)

Below are working, minimal patterns you can drop in. They prioritise determinism, fast feedback (<10 min), and useful artifacts.

### 1) Canonical GitHub Actions workflow (`.github/workflows/ci-smoke.yml`)

```yaml
name: ci-smoke
on:
  pull_request:
  push:
    branches: [ main ]

jobs:
  smoke:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    permissions:
      contents: read

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      # JDK + Gradle cache (uses Gradle wrapper)
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      # Optional extra Gradle caches (often saves 1–2 min on cold starts)
      - name: Gradle caches
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Enable KVM group perms (hardware accel on GH Ubuntu runners)
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Build APKs (release aab not needed here)
        run: |
          ./gradlew :app:assembleDebug :app:assembleAndroidTest --stacktrace --no-daemon

      # Start emulator and run a very small instrumentation slice
      - name: Run smoke tests on emulator
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          target: google_apis
          arch: x86_64
          profile: Pixel_6
          # Default options already include -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim
          # You may add: emulator-options: '-no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim'
          disable-animations: true
          script: |
            adb wait-for-device
            adb install -r app/build/outputs/apk/debug/app-debug.apk
            adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            # Optional: keep app data to preserve registration state on longer flows
            # Use Gradle test filtering to keep this fast
            ./gradlew connectedAndroidTest \
              -Pandroid.testInstrumentationRunnerArguments.class=com.yourpkg.SmokeSuite \
              --stacktrace --no-daemon

      - name: Collect artifacts
        if: always()
        run: |
          mkdir -p artifacts
          adb logcat -d > artifacts/logcat.txt || true
          adb shell uiautomator dump /sdcard/window_dump.xml || true
          adb pull /sdcard/window_dump.xml artifacts/window_dump.xml || true
          find . -type f -path "**/outputs/androidTest-results/connected/*.xml" -exec cp {} artifacts/ \; || true
          find . -type f -path "**/reports/androidTests/connected/*" -exec cp -r {} artifacts/ \; || true
          cp app/build/outputs/apk/debug/app-debug.apk artifacts/ || true
          cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk artifacts/ || true

      - name: Upload artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: smoke-artifacts-${{ github.run_id }}
          path: artifacts
          retention-days: 7
```

**Notes**
- The runner action auto-installs system images; the KVM step enables hardware acceleration on GH Ubuntu runners (2–3× faster than macOS). Defaults & options documented in the action’s README (e.g., `emulator-options`, `disable-linux-hw-accel`, etc.). citeturn1view0
- Use Gradle test filtering (`-Pandroid.testInstrumentationRunnerArguments.class=`) to run only a handful of tests (onboarding, gesture detection, TalkBack announcements).

---

### 2) `act` (local) — guidance

**Pitfalls**
- Containers usually have **no KVM**; the emulator must run **software-rendered** (`-gpu swiftshader_indirect`) and without VM accel. The action’s defaults already do software GPU; you may need to effectively disable HW accel.
- The base `act` images don’t include the Android SDK/adb.
- Private Marketplace actions may require a token inside `act`.

**Runner image mapping**
- Map `ubuntu-latest` to an image that at least has common build tools; then install SDK via the action itself, **or** use an image with the SDK preinstalled.
- Examples:
  - Generic act image: `ghcr.io/catthehacker/ubuntu:act-22.04`. citeturn0search10turn0search3
  - Android-SDK images you can use with `-P` (build tools available; still no KVM): `thyrlian/android-sdk` (Docker Hub / GitHub) or similar. citeturn0search4turn0search17

**Recommended `act` invocation**
```bash
# ~/.actrc (example)
-P ubuntu-latest=ghcr.io/catthehacker/ubuntu:act-22.04
# or, to get an SDK in the image:
# -P ubuntu-latest=thyrlian/android-sdk:latest

# Then run with a token for private actions or GHCR pulls
act -j smoke \
  -s GITHUB_TOKEN=ghp_xxx_readonlytoken \
  --container-architecture linux/amd64
```
References: act platform/image mapping docs. citeturn0search16

**If emulator fails under act**
- Force software path: set action input `disable-linux-hw-accel: true` and keep `-no-window -gpu swiftshader_indirect`. citeturn1view0
- As a fallback for local iteration, bypass the emulator and call the shell wrapper (below) up to—but not including—emulator start, or target a **real device** with `adb tcpip`.

---

### 3) What to include in a “smoke” vs full matrix

**Smoke (goal: <10 min)**
- 1 API level (e.g., 34, `google_apis`, `x86_64`, Pixel profile).
- 3–6 critical tests only:
  - Onboarding/first-run guard (skips if already provisioned)
  - Conversation list loads
  - Exit-gesture detection (A + B), confirmation slider appears
  - One TalkBack announcement assertion (if package present)
- Runner args:
  - Preserve app data: don’t wipe between tests (`-e clearPackageData false`).
  - Filter by class/method: `-Pandroid.testInstrumentationRunnerArguments.class=...`.

**Full matrix (nightly)**
- Multiple API levels (e.g., 28/31/34), Google APIs vs default, locales, and form-factors.

---

### 4) Avoiding action clone/auth problems in `act`
- Prefer **public** actions; otherwise:
  - Provide `GITHUB_TOKEN`/PAT with **minimal scopes** (see below).
  - Pre‑vendor critical actions under `.github/actions/*` and reference them locally.
  - Pre-warm `~/.cache/act` by running once online; `act` reuses the cache.

---

### 5) Snapshot strategy (optional, advanced)

Goal: boot straight into a **pre-registered** emulator to cut minutes off each run.

**Create snapshot (once, locally)**
```bash
# Create AVD
avd_name=signal-ci-api34
sdkmanager --install "system-images;android-34;google_apis;x86_64"
avdmanager create avd -n "$avd_name" -k "system-images;android-34;google_apis;x86_64" -d pixel_6

# Start, install, prepare state
emulator -avd "$avd_name" -no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim &
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r app-debug.apk
adb install -r app-debug-androidTest.apk
# (Optional) enable TalkBack if image includes it; otherwise install AAS.
# ... perform any registration/setup ...

# Save snapshot
adb emu avd snapshot save signal_preconfigured
adb emu avd snapshot list
```
You can later start with `-snapshot signal_preconfigured` or load via `adb emu avd snapshot load signal_preconfigured`. citeturn0search14

**Use snapshot in Actions**
- Keep a **stable `avd-name`** and set `force-avd-creation: false` so the runner doesn’t recreate the AVD every time.
- Restore a cached snapshot dir before launch:
```yaml
      - name: Restore AVD snapshot cache
        uses: actions/cache@v4
        with:
          path: ~/.android/avd/signal-ci-api34.avd/snapshots
          key: avd-signal-ci-api34-${{ hashFiles('ci/avd-snapshot-version.txt') }}

      - name: Run tests with snapshot
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          target: google_apis
          arch: x86_64
          avd-name: signal-ci-api34
          force-avd-creation: false
          emulator-options: '-no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -read-only -no-snapshot-save -snapshot signal_preconfigured'
          script: ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yourpkg.SmokeSuite
```
Community notes on copying snapshots into AVD dirs existed historically; exact paths may vary—cache by `avd-name` to stay consistent. citeturn0search7

---

### 6) Artifacts & debugging checklist
- `logcat.txt` (full dump)
- `window_dump.xml` (UIAutomator dump)
- JUnit XML + HTML reports from `connectedAndroidTest`
- Emulator console output (if captured)
- Built APKs (debug + androidTest)
- Optionally screenshots via `adb exec-out screencap -p > artifacts/screen.png`

Upload via `actions/upload-artifact` with 7–14 days retention for PRs.

---

### 7) Minimal `testing-tools/ci-smoke.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

APP_APK=${APP_APK:-app/build/outputs/apk/debug/app-debug.apk}
TEST_APK=${TEST_APK:-app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk}
SUITE=${SUITE:-com.yourpkg.SmokeSuite}
ART_DIR=${ART_DIR:-artifacts}
TIMEOUT=${TIMEOUT:-600}

mkdir -p "$ART_DIR"

adb wait-for-device || true
adb install -r "$APP_APK"
adb install -r "$TEST_APK"

# Optional: ensure animations off
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

# Optional: enable TalkBack (if present)
# adb shell settings put secure enabled_accessibility_services \
#   com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
# adb shell settings put secure accessibility_enabled 1

./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$SUITE" \
  --stacktrace --no-daemon

adb logcat -d > "$ART_DIR"/logcat.txt || true
adb shell uiautomator dump /sdcard/window_dump.xml || true
adb pull /sdcard/window_dump.xml "$ART_DIR"/window_dump.xml || true

cp "$APP_APK" "$ART_DIR"/ || true
cp "$TEST_APK" "$ART_DIR"/ || true

# Copy reports if available
find . -type f -path "**/outputs/androidTest-results/connected/*.xml" -exec cp {} "$ART_DIR"/ \; || true
find . -type f -path "**/reports/androidTests/connected/*" -exec cp -r {} "$ART_DIR"/ \; || true
```

Make it executable: `chmod +x testing-tools/ci-smoke.sh`. The GitHub Action can call this script inside the emulator-runner `script:` block if you prefer a single entry point.

---

### 8) Tokens for `act` (minimal scopes)
- For **private repos/actions** and GHCR pulls, a PAT with:
  - `repo` (read) — to clone and fetch submodules/actions
  - `read:packages` — if pulling GHCR images
  - (Optionally) `workflow:read` for private action metadata
- Provide to `act` via env/secret: `-s GITHUB_TOKEN=…` or a `.env` file. **Never** commit tokens.

---

### 9) Time budgets (pragmatic defaults)
- Emulator boot (with software GPU): 90–180 s
- Install + run 3–6 tests: 120–240 s
- Total wall clock on GH Ubuntu runner: typically **6–9 min**

---

### References
- Android Emulator Runner README (options, defaults, KVM on Ubuntu). citeturn1view0
- Emulator snapshots and command-line usage. citeturn0search1turn0search14
- act runner image mapping / docs; act Ubuntu images. citeturn0search16turn0search10
- Android SDK container images. citeturn0search4turn0search17
